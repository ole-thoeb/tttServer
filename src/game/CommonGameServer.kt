package game

import GameId
import Messages
import PlayerId
import SessionId
import arrow.core.Predicate
import arrow.core.k
import arrow.core.toT
import io.ktor.http.cio.websocket.WebSocketSession
import isCouldNotMatchGame
import json.JsonSerializable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import messages.responses.DefaultLobbyResponse
import messages.responses.TTTResponse
import noMessages
import noSuchGame

const val DISCONNECT_JOB = "job.disconnect"
const val CLIENT_TIME_OUT = 1000 * 5L

fun <G : InGameImplWithPlayer<*, *>> LobbyDefaultLobby<G>.updateTechnical(
        predicate: Predicate<Player.Human<*>>,
        update: (TechnicalPlayer) -> TechnicalPlayer
): LobbyDefaultLobby<G> = update {
    DefaultLobby.technical<G>(predicate).modify(this, update)
}

suspend inline fun <S, H : Player.HumanImpl, G : InGameImplWithPlayer<H, *>> S.clientJoined(
        sessionId: SessionId,
        gameId: GameId,
        websocket: WebSocketSession,
        crossinline inGameState: (Game.InGame<G>, Player.Human<H>) -> JsonSerializable,
        crossinline updateTechnicalInGame: G.(Predicate<Player.Human<*>>, (TechnicalPlayer) -> TechnicalPlayer) -> G
): JsonSerializable where S : SynchronizedGameRegistry<DefaultLobby<G>, G>, S : GameServer<DefaultLobby<G>, G> =
        updateGame(gameId) { game ->
            log.info("session ${sessionId.asString()}, game ${gameId.asString()}: client joined")

            when (game) {
                is Game.InGame -> {
                    val player = game.humanPlayers[sessionId]
                            ?: return@updateGame game toT noSuchGame(game.id)
                    val updatedGame = game.update {
                        updateTechnicalInGame(playerWith(player.playerId)) {
                            it.addSocket(websocket).cancelJob(DISCONNECT_JOB)
                        }
                    }

                    updatedGame toT mapOf(player.technical to inGameState(updatedGame, player))
                }
                is Game.Lobby -> {
                    val player = game.humanPlayers[sessionId]
                            ?: return@updateGame game toT noSuchGame(game.id)
                    val updatedGame = game.updateTechnical(playerWith(player.playerId)) {
                        it.addSocket(websocket).cancelJob(DISCONNECT_JOB)
                    }

                    updatedGame toT mapOf(player.technical to DefaultLobbyResponse.State.forPlayer(updatedGame, player))
                }
            }
        }.entries.first().value

suspend inline fun <S, H : Player.HumanImpl, G : InGameImplWithPlayer<H, *>> S.clientLeft(
        sessionId: SessionId,
        gameId: GameId,
        websocket: WebSocketSession,
        clientTimeOut: Long = CLIENT_TIME_OUT,
        crossinline updateTechnicalInGame: G.(Predicate<Player.Human<*>>, (TechnicalPlayer) -> TechnicalPlayer) -> G,
        crossinline handleInGameDisconnect: suspend GameServer.AsyncActionContext.(G) -> Unit
) where S : SynchronizedGameRegistry<DefaultLobby<G>, G>, S : GameServer<DefaultLobby<G>, G> {
    updateGame(gameId) { lockedGame ->
        log.info("session ${sessionId.asString()}, game ${gameId.asString()}: client left")

        val player = lockedGame.humanPlayersG.firstOrNull {
            it.technical.sessionId == sessionId
        } ?: return@updateGame lockedGame toT noMessages<JsonSerializable>()

        val updateTechnical = { technical: TechnicalPlayer ->
            technical.removeSocket(websocket).addJob(DISCONNECT_JOB,
                    startDisconnectJob(gameId, player.playerId, clientTimeOut, handleInGameDisconnect))
        }
        val updatedGame = when (lockedGame) {
            is Game.Lobby -> lockedGame.updateTechnical(playerWith(player.playerId), updateTechnical)
            is Game.InGame -> lockedGame.update {
                updateTechnicalInGame(playerWith(player.playerId), updateTechnical)
            }
        }
        return@updateGame updatedGame toT noMessages<JsonSerializable>()
    }
}

inline fun <S, G : InGameImplWithPlayer<*, *>> S.startDisconnectJob(
        gameId: GameId,
        playerId: PlayerId,
        clientTimeOut: Long,
        crossinline handleInGameDisconnect: suspend GameServer.AsyncActionContext.(G) -> Unit
): Job where S : SynchronizedGameRegistry<DefaultLobby<G>, G>, S : GameServer<DefaultLobby<G>, G> = launchAsyncAction {
    delay(clientTimeOut)

    withGame(gameId) { game ->
        when (game) {
            is Game.Lobby -> {
                val updatedGame = game.update {
                    DefaultLobby.players<G>().modify(this) { players ->
                        players.filter { it is Player.Human && it.playerId != playerId }.k()
                    }
                }

                if (updatedGame.players.all { it is Player.Bot }) {
                    removeUnlocked(gameId)
                } else {
                    updateUnlocked(updatedGame)
                    messageChannel.send(lobbyStateMsgs(updatedGame))
                }
            }
            is Game.InGame -> handleInGameDisconnect(game.impl)
        }
        return@withGame
    }
}

fun playerWith(id: PlayerId): Predicate<Player<*, *>> = { it.playerId == id }

fun handleLobbyError(sessionId: SessionId): (LobbyError) -> Messages = { error: LobbyError ->
    when (error) {
        is LobbyError.Full -> mapOf(TechnicalPlayer(sessionId = sessionId) to DefaultLobbyResponse.Full.fromError(error))
    }
}

fun messagesToDircect(sessionId: SessionId, gameId: GameId, messages: Messages): GameServer.DirectResponse {
    return if (messages.isCouldNotMatchGame()) {
        GameServer.DirectResponse(TTTResponse.NoSuchGame(gameId.asString()), noMessages())
    } else {
        val respondMsg = messages.entries.firstOrNull {
            it.key.sessionId == sessionId
        } ?: throw IllegalStateException("no join response was produced")
        GameServer.DirectResponse(respondMsg.value, messages)
    }
}