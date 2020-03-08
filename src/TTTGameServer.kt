import arrow.core.Tuple2
import json.JsonSerializable
import io.ktor.http.cio.websocket.WebSocketSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import arrow.core.toT
import messages.responses.TTTResponse

class TTTGameServer {
    
    private val games: ConcurrentHashMap<GameId, TTTGame> = ConcurrentHashMap()
    private val locks: ConcurrentHashMap<GameId, Mutex> = ConcurrentHashMap()
    
    fun newGame(): TTTGame {
        val gameId = GameId.create()
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
    
    suspend fun clientLeft(sessionId: SessionId, websocket: WebSocketSession) {
        val game = sessionId.game
        if (game != null) {
            updateGame<JsonSerializable>(game.id) { lockedGame ->
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
    }

    fun noSuchGame(unknownGameId: GameId): Messages =
        mapOf(TechnicalPlayer.DUMMY to TTTResponse.NoSuchGame(unknownGameId.asString()))
    
    private val SessionId.game: TTTGame? get() {
        return games.entries.firstOrNull { (_, game) ->
            game.technicalPlayers.any { it.sessionId == this }
        }?.value
    }
}