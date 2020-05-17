package game.ttt

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import messages.Messages
import messages.noMessages
import messages.requests.TTTGameRequest
import messages.requests.LobbyRequest
import messages.responses.TTTInGameResponse
import java.util.concurrent.atomic.AtomicInteger

class TTTGameServer(
        override val application: Application,
        val testing: Boolean
) : GameServer<DefaultLobby<TTTInGame>, TTTInGame>,
        CoroutineScope by application,
        SynchronizedGameRegistry<DefaultLobby<TTTInGame>, TTTInGame> by LockingSynchronizedGameRegistry() {

    private val jsonParser = JsonParser(Either.monadError())

    private val rematchManager = RematchManager(this, this)

    private val messageChannel = Channel<Messages>(Channel.UNLIMITED)
    override val asyncMessages: ReceiveChannel<Messages> get() = messageChannel

    private var testGameId = AtomicInteger(0)

    override suspend fun newGame(): TTTGame {
        val gameId = if (testing) GameId(testGameId.incrementAndGet().toString()) else GameId.create()
        return add(newTTTGame(gameId))
    }

    override suspend fun rematch(sessionId: SessionId, oldGameId: GameId): GameServer.DirectResponse {
        return rematch(rematchManager, sessionId, oldGameId, TTTInGameResponse.State.Companion::forPlayer)
    }

    override suspend fun clientJoined(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession): JsonSerializable {
        return clientJoined(sessionId, gameId, websocket, TTTInGameResponse.State.Companion::forPlayer, TTTInGame::updateTechnical)
    }

    override suspend fun clientLeft(sessionId: SessionId, gameId: GameId, websocket: WebSocketSession) {
        clientLeft(sessionId, gameId, websocket, CLIENT_TIME_OUT, TTTInGame::updateTechnical,
                handleTwoPlayerGameDisconnect(gameId) { it.twoPlayerGame })
    }

    override suspend fun handleJsonRequest(session: SessionId, request: JsonString): Messages {
        val deserializers = LobbyRequest.deserializers + TTTGameRequest.deserializers
        return jsonParser.parsRequest(request, deserializers.k()).fix().fold(
                { error ->
                    log.info("failed to parse JSON request! session=$session, error=$error")
                    noMessages()
                },
                { parsedRequest ->
                    when (parsedRequest) {
                        is LobbyRequest -> handleLobbyRequest(parsedRequest, TTTInGameResponse.State.Companion::forPlayer)
                        is TTTGameRequest -> handleGameRequest(parsedRequest)
                        else -> {
                            log.info("unknown parsed request type. parsedRequest=$parsedRequest")
                            noMessages()
                        }
                    }
                }
        )
    }

    override fun launchAsyncAction(block: suspend GameServer.AsyncActionContext.() -> Unit): Job =
            application.launch {
                block(GameServer.AsyncActionContext(messageChannel, log, this))
            }

    override suspend fun addPlayer(sessionId: SessionId, gameId: GameId): GameServer.DirectResponse {
        val messages = joinLobby(gameId, sessionId, TTTInGameResponse.State.Companion::forPlayer) { lobby ->
            lobby.addNewPlayer(sessionId)
        }
        return messagesToDircect(sessionId, gameId, messages)
    }
}

fun TTTInGame.updateTechnical(
        predicate: Predicate<Player.Human<*>>,
        update: (TechnicalPlayer) -> TechnicalPlayer
): TTTInGame = (TTTInGame.twoPlayerGame() + TwoPlayerGame.technical(predicate)).modify(this, update)