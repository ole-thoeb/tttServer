import arrow.core.*
import arrow.core.extensions.either.monadError.monadError
import io.ktor.application.Application
import io.ktor.application.log
import io.ktor.http.cio.websocket.WebSocketSession
import json.JsonParser
import json.JsonSerializable
import json.JsonString
import json.parsRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import messages.requests.GameRequest
import messages.requests.LobbyRequest
import messages.responses.InGameResponse
import messages.responses.LobbyResponse
import messages.responses.TTTResponse
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class TTTGameServer(
        private val application: Application,
        val testing: Boolean
) : GameServer<TTTGame>, CoroutineScope by application {

    private val games: ConcurrentHashMap<GameId, TTTGame> = ConcurrentHashMap()
    private val locks: ConcurrentHashMap<GameId, Mutex> = ConcurrentHashMap()

    private val rematchMap: ConcurrentHashMap<GameId, GameId> = ConcurrentHashMap()

    private val jsonParser = JsonParser(Either.monadError())
    val log get() = application.log

    private val messageChannel = Channel<Messages>(Channel.UNLIMITED)
    override val asyncMessages: ReceiveChannel<Messages> get() = messageChannel

    private var testGameId = AtomicInteger(0)

    override suspend fun newGame(): TTTGame {
        val gameId = if (testing) GameId(testGameId.incrementAndGet().toString()) else GameId.create()
        val game = TTTGame.Lobby(gameId)
        locks[gameId] = Mutex()
        games[gameId] = game
        return game
    }

    suspend fun rematchIdOfGame(gameId: GameId): GameId {
        suspend fun getRematchId(): GameId {
            val rematchId = rematchMap[gameId]
            if (rematchId != null)
                return rematchId

            val rematch = newGame()
            rematchMap[gameId] = rematch.id
            return rematch.id
        }

        val lock = locks[gameId]
        return lock?.withLock { getRematchId() } ?: getRematchId()
    }

    private suspend inline fun <R> lockGame(gameId: GameId, default: () -> R, block: (TTTGame) -> R): R {
        val mutex = locks[gameId] ?: return default()
        mutex.withLock {
            val game = games[gameId] ?: return default()
            return block(game)
        }
    }

    suspend fun <MSG : JsonSerializable> updateGame(gameId: GameId, update: (TTTGame) -> Tuple2<TTTGame, MessagesOf<MSG>>): Messages {
        return lockGame(gameId, { noSuchGame(gameId) }) { game ->
            val (updatedGame, msgs) = update(game)
            games[gameId] = updatedGame
            return msgs
        }
    }

    suspend fun <T> withGame(gameId: GameId, action: (TTTGame?) -> T): T {
        return lockGame(gameId, { action(null) }, action)
    }

    override suspend fun addPlayer(sessionId: SessionId, gameId: GameId): GameServer.DirectResponse =
            addPlayer(sessionId, gameId) { addNewPlayer(sessionId, gameId) }

    private suspend fun addPlayer(sessionId: SessionId, gameId: GameId, addMethod: suspend () -> Messages): GameServer.DirectResponse {
        val messages = addMethod()
        return if (messages.isCouldNotMatchGame()) {
            GameServer.DirectResponse(TTTResponse.NoSuchGame(gameId.asString()), noMessages())
        } else {
            val respondMsg = messages.entries.firstOrNull { it.key.sessionId == sessionId }
                    ?: throw IllegalStateException("no join response was produced")
            GameServer.DirectResponse(respondMsg.value, messages)
        }
    }

    override suspend fun rematch(sessionId: SessionId, oldGameId: GameId): GameServer.DirectResponse {
        val rematchId = rematchIdOfGame(oldGameId)
        val oldPlayerName: String? = withGame(oldGameId) { game ->
            when (game) {
                null -> null

                is TTTGame.Lobby -> game.players.firstOrNull {
                    it is TTTGame.Lobby.Player.Human && it.technical.sessionId == sessionId
                }?.name

                is TTTGame.InGame -> game.players.firstOrNull { p ->
                    p is TTTGame.InGame.Player.Human && p.technical.sessionId == sessionId
                }?.name
            }
        }
        return if (oldPlayerName != null) {
            val technical = TechnicalPlayer(PlayerId.create(), sessionId, ListK.empty(), emptyMap<String, Job>().k())
            val player = TTTGame.Lobby.Player.Human(oldPlayerName, false, technical)
            addPlayer(sessionId, rematchId) { addNewPlayer(player, rematchId) }
        } else {
            addPlayer(sessionId, rematchId)
        }
    }

    override suspend fun clientJoined(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession): JsonSerializable {
        return updateGame(gameId) { game ->
            log.info("session ${sessionId.asString()}, game ${gameId.asString()}: client joined")

            val addWebSocket = { technical: TechnicalPlayer ->
                technical.copy(sockets = (technical.sockets + websocket).k(), jobs = technical.jobs.filter { (key, job) ->
                    if (key == DISCONNECT_JOB) {
                        job.cancel()
                        false
                    } else {
                        true
                    }
                }.k())
            }

            when (game) {
                is TTTGame.Lobby -> {
                    val player = game.players.firstOrNull {
                        it is TTTGame.Lobby.Player.Human && it.technical.sessionId == sessionId
                    } as? TTTGame.Lobby.Player.Human ?: return@updateGame game toT noSuchGame(game.id)

                    val updatedGame = TTTGame.Lobby.technical { it.technical.sessionId == sessionId }
                            .modify(game, addWebSocket)
                    return@updateGame updatedGame toT mapOf(player.technical to LobbyResponse.State.forPlayer(game, player))
                }
                is TTTGame.InGame -> {
                    val player = game.players.firstOrNull {
                        it is TTTGame.InGame.Player.Human && it.technical.sessionId == sessionId
                    } as? TTTGame.InGame.Player.Human ?: return@updateGame game toT noSuchGame(game.id)

                    val updatedGame = TTTGame.InGame.technical { it.technical.sessionId == sessionId }
                            .modify(game, addWebSocket)
                    return@updateGame updatedGame toT mapOf(player.technical to InGameResponse.State.forPlayer(game, player))
                }
            }
        }.entries.first().value
    }

    override suspend fun clientLeft(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession) {
        updateGame(gameId) { lockedGame ->
            log.info("session ${sessionId.asString()}, game ${gameId.asString()}: client left")

            val removeWebSocket = { technical: TechnicalPlayer ->
                val newSockets = (technical.sockets - websocket).k()
                val newJobs = if (newSockets.isEmpty()) {
                    (technical.jobs + (DISCONNECT_JOB toT startDisconnectJob(gameId, technical.playerId))).k()
                } else {
                    technical.jobs
                }
                technical.copy(sockets = newSockets, jobs = newJobs)
            }
            val updatedGame = when (lockedGame) {
                is TTTGame.Lobby -> TTTGame.Lobby.technical { it.technical.sessionId == sessionId }
                        .modify(lockedGame, removeWebSocket)
                is TTTGame.InGame -> TTTGame.InGame.technical { it.technical.sessionId == sessionId }
                        .modify(lockedGame, removeWebSocket)
            }
            return@updateGame updatedGame toT noMessages<JsonSerializable>()
        }
    }

    private fun noSuchGame(unknownGameId: GameId): Messages =
            mapOf(TechnicalPlayer.DUMMY to TTTResponse.NoSuchGame(unknownGameId.asString()))

    override suspend fun handleJsonRequest(session: SessionId, request: JsonString): Messages {
        val deserializers = LobbyRequest.deserializers + GameRequest.deserializers
        return jsonParser.parsRequest(request, deserializers.k()).fix().fold(
                { error ->
                    log.info("failed to parse JSON request! session=$session, error=$error")
                    noMessages()
                },
                { parsedRequest ->
                    when (parsedRequest) {
                        is LobbyRequest -> handleLobbyRequest(parsedRequest)
                        is GameRequest -> handleGameRequest(parsedRequest)
                        else -> {
                            log.info("unknown parsed request type. parsedRequest=$parsedRequest")
                            noMessages()
                        }
                    }
                }
        )
    }

    private fun startDisconnectJob(gameId: GameId, playerId: PlayerId): Job = launchAsyncAction {
        delay(CLIENT_TIME_OUT)
        val lock = locks[gameId] ?: return@launchAsyncAction
        lock.withLock {
            val game = games[gameId] ?: return@launchAsyncAction

            val removeGame = {
                games.remove(game.id)
                locks.remove(game.id)
            }

            when (game) {
                is TTTGame.Lobby -> {
                    val updatedGame = TTTGame.Lobby.players().modify(game) { players ->
                        players.filter { it is TTTGame.Lobby.Player.Human && it.technical.playerId != playerId }.k()
                    }

                    if (updatedGame.players.all { it is TTTGame.Lobby.Player.Bot }) {
                        removeGame()
                    } else {
                        games[game.id] = updatedGame
                        messageChannel.send(lobbyStateMsgs(updatedGame))
                    }
                }
                is TTTGame.InGame -> if (game.player1 is TTTGame.InGame.Player.Human && game.player2 is TTTGame.InGame.Player.Human) {
                    val t1 = game.player1.technical
                    val t2 = game.player2.technical
                    when {
                        t1.sockets.isEmpty() && t2.sockets.isEmpty() -> removeGame()
                        t1.playerId == playerId && t1.sockets.isEmpty() ->
                            messageChannel.send(mapOf(t2 to TTTResponse.PlayerDisconnected(game.player1.name)))
                        t2.playerId == playerId && t2.sockets.isEmpty() ->
                            messageChannel.send(mapOf(t1 to TTTResponse.PlayerDisconnected(game.player2.name)))
                    }
                }
            }
            return@withLock
        }
    }

    data class AsyncActionContext(
            val messageChanel: Channel<Messages>,
            val log: Logger,
            val scope: CoroutineScope
    ) : CoroutineScope by scope

    fun launchAsyncAction(block: suspend AsyncActionContext.() -> Unit): Job = launch {
        block(AsyncActionContext(messageChannel, log, this@TTTGameServer))
    }

    suspend fun AsyncActionContext.asyncUpdateGame(gameId: GameId, update: suspend (TTTGame) -> Tuple2<TTTGame, Messages>) {
        lockGame(gameId, { null }) { game ->
            val (updatedGame, msgs) = update(game)
            games[gameId] = updatedGame
            msgs
        }?.let { msgs ->
            log.info("should send $msgs")
            messageChanel.send(msgs)
        }
    }

    companion object {
        private const val CLIENT_TIME_OUT = 1000 * 5L
        private const val DISCONNECT_JOB = "job.disconnect"
    }
}