import arrow.core.Either
import arrow.core.Tuple2
import arrow.core.extensions.either.foldable.fold
import arrow.core.extensions.either.monadError.monadError
import arrow.core.fix
import arrow.core.toT
import io.ktor.application.Application
import io.ktor.application.log
import io.ktor.http.cio.websocket.WebSocketSession
import json.JsonParser
import json.JsonSerializable
import json.JsonString
import json.parsRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import messages.requests.LobbyRequest
import messages.responses.TTTResponse
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class TTTGameServer(private val application: Application, val testing: Boolean){
    
    private val games: ConcurrentHashMap<GameId, TTTGame> = ConcurrentHashMap()
    private val locks: ConcurrentHashMap<GameId, Mutex> = ConcurrentHashMap()

    private val jsonParser = JsonParser(Either.monadError())
    private val log get() = application.log

    private var testGameId = AtomicInteger(0)

    fun newGame(): TTTGame {
        val gameId = if (testing) GameId(testGameId.incrementAndGet().toString()) else GameId.create()
        val game = TTTGame.Lobby(gameId)
        locks[gameId] = Mutex()
        games[gameId] = game
        return game
    }
    
    suspend fun <MSG: JsonSerializable>updateGame(gameId: GameId, update: (TTTGame) -> Tuple2<TTTGame, MessagesOf<MSG>>): Messages {
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
            val player = game.technicalPlayers.firstOrNull { it.sessionId == sessionId }
                    ?: return@updateGame game toT noSuchGame(game.id)

            val updatedGame = when (game) {
                is TTTGame.Lobby -> {
                    TTTGame.Lobby.technical { it.technical.sessionId == sessionId }
                            .modify(game) { it.addSocket(websocket) }
                }
                is TTTGame.InGame -> {
                    TODO()
                }
            }

            return@updateGame updatedGame toT mapOf(player to TTTResponse.State.forPlayer(updatedGame, player))
        }.entries.first().value
    }
    
    suspend fun clientLeft(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession) {
        updateGame<JsonSerializable>(gameId) { lockedGame ->
            val updatedGame = when (lockedGame) {
                is TTTGame.Lobby -> {
                    TTTGame.Lobby.technical { it.technical.sessionId == sessionId }
                            .modify(lockedGame) { it.removeSocket(websocket) }
                }
                is TTTGame.InGame -> {
                    TODO()
                }
            }
            return@updateGame updatedGame toT emptyMap()
        }
    }

    private fun noSuchGame(unknownGameId: GameId): Messages =
        mapOf(TechnicalPlayer.DUMMY to TTTResponse.NoSuchGame(unknownGameId.asString()))

    suspend fun handleJsonRequest(session: SessionId, request: JsonString): Messages {
        return jsonParser.parsRequest(request, LobbyRequest.deserializers).fix().fold(
            { error ->
                log.info("failed to parse JSON request! session=$session, error=$error")
                emptyMap()
            },
            { parsedRequest -> when (parsedRequest) {
                is LobbyRequest -> handleLobbyRequest(parsedRequest)
            }}
        )
    }

    private val SessionId.game: TTTGame? get() {
        return games.entries.firstOrNull { (_, game) ->
            game.technicalPlayers.any { it.sessionId == this }
        }?.value
    }
}