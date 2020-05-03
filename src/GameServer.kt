import io.ktor.http.cio.websocket.WebSocketSession
import json.JsonSerializable
import json.JsonString
import kotlinx.coroutines.channels.ReceiveChannel

interface GameServer<out GAME : Game> {
    suspend fun newGame(): GAME
    suspend fun clientJoined(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession): JsonSerializable
    suspend fun clientLeft(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession)
    suspend fun handleJsonRequest(session: SessionId, request: JsonString): Messages
    val asyncMessages: ReceiveChannel<Messages>

    suspend fun addPlayer(sessionId: SessionId, gameId: GameId): DirectResponse
    suspend fun rematch(sessionId: SessionId, oldGameId: GameId): DirectResponse

    data class DirectResponse(val calleeResponse: JsonSerializable, val otherResponses: Messages)
}