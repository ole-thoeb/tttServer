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
import messages.responses.TTTResponse
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class TTTGameServer(
        private val application: Application,
        val testing: Boolean
) : CoroutineScope by application {

    private val games: ConcurrentHashMap<GameId, TTTGame> = ConcurrentHashMap()
    private val locks: ConcurrentHashMap<GameId, Mutex> = ConcurrentHashMap()

    private val rematchMap: ConcurrentHashMap<GameId, GameId> = ConcurrentHashMap()

    private val jsonParser = JsonParser(Either.monadError())
    private val log get() = application.log

    private val messageChannel = Channel<Messages>(Channel.UNLIMITED)
    val asyncMessages: ReceiveChannel<Messages> get() = messageChannel

    private var testGameId = AtomicInteger(0)

    fun newGame(): TTTGame {
        val gameId = if (testing) GameId(testGameId.incrementAndGet().toString()) else GameId.create()
        val game = TTTGame.Lobby(gameId)
        locks[gameId] = Mutex()
        games[gameId] = game
        return game
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
        val mutex = locks[gameId] ?: return noSuchGame(gameId)

        mutex.withLock {
            val game = games[gameId] ?: return noSuchGame(gameId)

            val (updatedGame, msgs) = update(game)
            games[gameId] = updatedGame
            return msgs
        }
    }

    suspend fun clientJoined(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession): JsonSerializable {
        return updateGame(gameId) { game ->
            log.info("session ${sessionId.asString()}, game ${gameId.asString()}: client joined")
            val player = game.technicalPlayers.firstOrNull { it.sessionId == sessionId }
                    ?: return@updateGame game toT noSuchGame(game.id)

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

            val updatedGame = when (game) {
                is TTTGame.Lobby -> TTTGame.Lobby.technical { it.technical.sessionId == sessionId }
                        .modify(game, addWebSocket)
                is TTTGame.InGame -> TTTGame.InGame.technical { it.technical.sessionId == sessionId }
                        .modify(game, addWebSocket)
            }

            return@updateGame updatedGame toT mapOf(player to TTTResponse.State.forPlayer(updatedGame, player))
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
        val lock = locks[gameId] ?: return@launch
        lock.withLock {
            val game = games[gameId] ?: return@launch

            val removeGame = {
                games.remove(game.id)
                locks.remove(game.id)
            }

            when (game) {
                is TTTGame.Lobby -> {
                    val updatedGame = TTTGame.Lobby.players().modify(game) { players ->
                        players.filter { it.technical.playerId != playerId || it.technical.sockets.isNotEmpty() }.k()
                    }

                    if (updatedGame.technicalPlayers.isEmpty()) {
                        removeGame()
                    } else {
                        games[game.id] = updatedGame
                        messageChannel.send(lobbyStateMsgs(updatedGame))
                    }
                }
                is TTTGame.InGame -> {
                    val t1 = game.player1.technical
                    val t2 = game.player2.technical
                    when {
                        t1.sockets.isEmpty() && t2.sockets.isEmpty() -> removeGame()
                        t1.playerId == playerId && t1.sockets.isEmpty() ->
                            messageChannel.send(mapOf(t2 to TTTResponse.PlayerDisconnected(game.player1.name)))
                        t2.playerId == playerId && t2.sockets.isEmpty() ->
                            messageChannel.send(mapOf(t1 to TTTResponse.PlayerDisconnected(game.player2.name)))
                        else -> {
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val CLIENT_TIME_OUT = 1000 * 5L
        private const val DISCONNECT_JOB = "job.disconnect"
    }
}