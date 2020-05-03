package game.misery

import GameId
import Messages
import SessionId
import game.GameServer
import game.LockingSynchronizedGameRegistry
import game.SynchronizedGameRegistry
import io.ktor.application.Application
import io.ktor.http.cio.websocket.WebSocketSession
import json.JsonSerializable
import json.JsonString
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel

class MiseryGameServer(override val application: Application) : GameServer<MiseryGame>,
        SynchronizedGameRegistry<MiseryGame> by LockingSynchronizedGameRegistry() {
    override suspend fun newGame(): MiseryGame {
        TODO("Not yet implemented")
    }

    override suspend fun clientJoined(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession): JsonSerializable {
        TODO("Not yet implemented")
    }

    override suspend fun clientLeft(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession) {
        TODO("Not yet implemented")
    }

    override suspend fun handleJsonRequest(session: SessionId, request: JsonString): Messages {
        TODO("Not yet implemented")
    }

    override val asyncMessages: ReceiveChannel<Messages>
        get() = TODO("Not yet implemented")

    override suspend fun addPlayer(sessionId: SessionId, gameId: GameId): GameServer.DirectResponse {
        TODO("Not yet implemented")
    }

    override suspend fun rematch(sessionId: SessionId, oldGameId: GameId): GameServer.DirectResponse {
        TODO("Not yet implemented")
    }

    override fun launchAsyncAction(block: suspend GameServer.AsyncActionContext.() -> Unit): Job {
        TODO("Not yet implemented")
    }

}