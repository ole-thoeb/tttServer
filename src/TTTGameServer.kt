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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class TTTGameServer(
        private val application: Application,
        val testing: Boolean
) : CoroutineScope by application {

    data class GameWithLock(val game: TTTGame, val lock: Mutex)

    private val games: ConcurrentHashMap<GameId, GameWithLock> = ConcurrentHashMap()

    private val rematchMap: ConcurrentHashMap<GameId, GameId> = ConcurrentHashMap()

    private val jsonParser = JsonParser(Either.monadError())
    private val log get() = application.log

    private val messageChannel = Channel<Messages>(Channel.UNLIMITED)
    val asyncMessages: ReceiveChannel<Messages> get() = messageChannel

    private var testGameId = AtomicInteger(0)

    fun newGame(): TTTGame {
        val gameId = if (testing) GameId(testGameId.incrementAndGet().toString()) else GameId.create()
        val gameWithLock = GameWithLock(TTTGame.Lobby(gameId), Mutex())
        games[gameId] = gameWithLock
        return gameWithLock.game
    }

    fun rematchIdOfGame(gameId: GameId): GameId {
        val rematchId = rematchMap[gameId]
        if (rematchId != null)
            return rematchId

        val rematch = newGame()
        rematchMap[gameId] = rematch.id
        return rematch.id
    }

    suspend fun <MSG : JsonSerializable> updateGame(gameId: GameId, update: (TTTGame) -> Tuple2<TTTGame, MessagesOf<MSG>>): Messages {
        val gameWithLock = games[gameId] ?: return noSuchGame(gameId)
        gameWithLock.lock.withLock {
            val (updatedGame, msgs) = update(gameWithLock.game)
            games[gameId] = gameWithLock.copy(game = updatedGame)
            return msgs
        }
    }

    suspend fun <T> withGame(gameId: GameId, action: (TTTGame?) -> T): T {
        val gameWithLock = games[gameId]
        return if (gameWithLock == null) {
            action(null)
        } else {
            gameWithLock.lock.withLock { action(gameWithLock.game) }
        }
    }

    suspend fun clientJoined(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession): JsonSerializable {
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

    suspend fun clientLeft(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession) {
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

    suspend fun handleJsonRequest(session: SessionId, request: JsonString): Messages {
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

    private fun startDisconnectJob(gameId: GameId, playerId: PlayerId): Job = launch {
        delay(CLIENT_TIME_OUT)
        val gameWithLock = games[gameId] ?: return@launch
        gameWithLock.lock.withLock {
            val game = gameWithLock.game

            val removeGame = {
                games.remove(game.id)
            }

            when (game) {
                is TTTGame.Lobby -> {
                    val updatedGame = TTTGame.Lobby.players().modify(game) { players ->
                        players.filter { it is TTTGame.Lobby.Player.Human && it.technical.playerId != playerId }.k()
                    }

                    if (updatedGame.players.all { it is TTTGame.Lobby.Player.Bot }) {
                        removeGame()
                    } else {
                        games[game.id] = gameWithLock.copy(game = updatedGame)
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

    companion object {
        private const val CLIENT_TIME_OUT = 1000 * 5L
        private const val DISCONNECT_JOB = "job.disconnect"
    }
}