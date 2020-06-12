package game.stoplight

import GameId
import SessionId
import arrow.core.Either
import arrow.core.Predicate
import arrow.core.extensions.either.monadError.monadError
import arrow.core.fix
import arrow.core.k
import game.*
import io.ktor.application.Application
import io.ktor.http.cio.websocket.WebSocketSession
import json.JsonParser
import json.JsonSerializable
import json.JsonString
import json.parsRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import messages.Messages
import messages.noMessages
import messages.requests.LobbyRequest
import messages.requests.MiseryGameRequest
import messages.requests.StoplightGameRequest
import messages.responses.MiseryInGameResponse
import messages.responses.StoplightInGameResponse
import java.util.concurrent.atomic.AtomicInteger

class StoplightGameServer(
        override val application: Application,
        val testing: Boolean = true
) : GameServer<DefaultLobby<StoplightInGame>, StoplightInGame>,
        SynchronizedGameRegistry<DefaultLobby<StoplightInGame>, StoplightInGame> by LockingSynchronizedGameRegistry() {

    private val jsonParser = JsonParser(Either.monadError())

    private val rematchManager = RematchManager(this, this)

    private val messageChannel = Channel<Messages>(Channel.UNLIMITED)
    override val asyncMessages: ReceiveChannel<Messages> get() = messageChannel

    private var testGameId = AtomicInteger(0)

    override suspend fun newGame(): StoplightGame {
        val gameId = if (testing) GameId(testGameId.incrementAndGet().toString()) else GameId.create()
        return add(newMiseryGame(gameId))
    }

    override suspend fun clientJoined(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession): JsonSerializable {
        return clientJoined(sessionId, gameId, websocket, StoplightInGameResponse.State.Companion::forPlayer, StoplightInGame::updateTechnical)
    }

    override suspend fun clientLeft(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession) {
        clientLeft(sessionId, gameId, websocket, CLIENT_TIME_OUT, StoplightInGame::updateTechnical,
                handleTwoPlayerGameDisconnect(gameId) { it.twoPlayerGame })
    }

    override suspend fun handleJsonRequest(session: SessionId, request: JsonString): Messages {
        val deserializers = LobbyRequest.deserializers + StoplightGameRequest.deserializers
        return jsonParser.parsRequest(request, deserializers.k()).fix().fold(
                { error ->
                    log.info("failed to parse JSON request! session=$session, error=$error")
                    noMessages()
                },
                { parsedRequest ->
                    when (parsedRequest) {
                        is LobbyRequest -> handleLobbyRequest(parsedRequest, StoplightInGameResponse.State.Companion::forPlayer)
                        is StoplightGameRequest -> handleGameRequest(parsedRequest)
                        else -> {
                            log.info("unknown parsed request type. parsedRequest=$parsedRequest")
                            noMessages()
                        }
                    }
                }
        )
    }

    override suspend fun rematch(sessionId: SessionId, oldGameId: GameId): GameServer.DirectResponse {
        return rematch(rematchManager, sessionId, oldGameId, StoplightInGameResponse.State.Companion::forPlayer)
    }

    override fun launchAsyncAction(block: suspend GameServer.AsyncActionContext.() -> Unit): Job =
            application.launch {
                block(GameServer.AsyncActionContext(messageChannel, log, this))
            }

    override suspend fun addPlayer(sessionId: SessionId, gameId: GameId): GameServer.DirectResponse {
        val messages = joinLobby(gameId, sessionId, StoplightInGameResponse.State.Companion::forPlayer) { lobby ->
            lobby.addNewPlayer(sessionId)
        }
        return messagesToDircect(sessionId, gameId, messages)
    }
}

fun StoplightInGame.updateTechnical(
        predicate: Predicate<Player.Human<*>>,
        update: (TechnicalPlayer) -> TechnicalPlayer
): StoplightInGame = (StoplightInGame.twoPlayerGame() + TwoPlayerGame.technical(predicate)).modify(this, update)