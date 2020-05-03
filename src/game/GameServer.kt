package game

import GameId
import Messages
import SessionId
import io.ktor.application.Application
import io.ktor.application.log
import io.ktor.http.cio.websocket.WebSocketSession
import json.JsonSerializable
import json.JsonString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import org.slf4j.Logger

interface GameServer<out GAME : Game> {

    val application: Application
    suspend fun newGame(): GAME
    suspend fun clientJoined(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession): JsonSerializable
    suspend fun clientLeft(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession)
    suspend fun handleJsonRequest(session: SessionId, request: JsonString): Messages
    val asyncMessages: ReceiveChannel<Messages>

    suspend fun addPlayer(sessionId: SessionId, gameId: GameId): DirectResponse
    suspend fun rematch(sessionId: SessionId, oldGameId: GameId): DirectResponse

    data class DirectResponse(val calleeResponse: JsonSerializable, val otherResponses: Messages)

    data class AsyncActionContext(
            val messageChanel: Channel<Messages>,
            val log: Logger,
            val scope: CoroutineScope
    ) : CoroutineScope by scope

    fun launchAsyncAction(block: suspend AsyncActionContext.() -> Unit): Job
}

val <GAME : Game> GameServer<GAME>.log get() = application.log