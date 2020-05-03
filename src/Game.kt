import arrow.core.ListK
import arrow.core.MapK
import arrow.core.k
import io.ktor.http.cio.websocket.WebSocketSession
import kotlinx.coroutines.Job

interface Game {
    val id: GameId
}


data class TechnicalPlayer(
        val playerId: PlayerId,
        val sessionId: SessionId,
        val sockets: ListK<WebSocketSession>,
        val jobs: MapK<String, Job>
) {

    fun addSocket(webSocket: WebSocketSession): TechnicalPlayer = copy(sockets = (sockets + webSocket).k())
    fun removeSocket(webSocket: WebSocketSession): TechnicalPlayer = copy(sockets = (sockets - webSocket).k())

    companion object {
        val DUMMY = TechnicalPlayer(PlayerId("DUMMY_PLAYER"), SessionId("DUMMY_SESSION"), ListK.empty(), emptyMap<String, Job>().k())
    }
}