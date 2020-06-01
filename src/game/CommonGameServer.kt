package game

import GameId
import PlayerId
import SessionId
import arrow.core.*
import io.ktor.http.cio.websocket.WebSocketSession
import json.JsonSerializable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import limit
import messages.*
import messages.requests.LobbyRequest
import messages.responses.DefaultLobbyResponse
import messages.responses.GameResponse
import skynet.MinMaxStrategy
import skynet.minMax
import skynet.randomMove

const val DISCONNECT_JOB = "job.disconnect"
const val CLIENT_TIME_OUT = 1000 * 5L

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
        } ?: return@updateGame lockedGame toT noMessages()

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
        return@updateGame updatedGame toT noMessages()
    }
}

inline fun <G: InGameImplWithPlayer<*, *>> SynchronizedGameRegistry<*, G>.handleTwoPlayerGameDisconnect(
        gameId: GameId,
        crossinline getter: (G) -> TwoPlayerGame
): suspend GameServer.AsyncActionContext.(G) -> Unit {
    return { handleTwoPlayerGameDisconnect(gameId)(getter(it)) }
}

fun SynchronizedGameRegistry<*, *>.handleTwoPlayerGameDisconnect(
        gameId: GameId
): suspend GameServer.AsyncActionContext.(TwoPlayerGame) -> Unit {
    return { twoPlayerGame ->
        val player1 = twoPlayerGame.player1
        val player2 = twoPlayerGame.player2
        when (player1) {
            is Player.Human -> when (player2) {
                is Player.Human -> when {
                    player1.hasNoSockets() && player2.hasNoSockets() -> removeUnlocked(gameId)
                    player1.hasNoSockets() -> messageChannel.send(
                            mapOf(player2.technical to GameResponse.PlayerDisconnected(player1.name))
                    )
                    player2.hasNoSockets() -> messageChannel.send(
                            mapOf(player1.technical to GameResponse.PlayerDisconnected(player2.name))
                    )
                }
                is Player.Bot -> if (player1.hasNoSockets()) removeUnlocked(gameId)
            }
            is Player.Bot -> when (player2) {
                is Player.Human -> if (player2.hasNoSockets()) removeUnlocked(gameId)
                is Player.Bot -> removeUnlocked(gameId)
            }
        }
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

suspend inline fun <H : Player.HumanImpl, G : InGameImplWithPlayer<H, *>, S> S.joinLobby(
        gameId: GameId,
        sessionId: SessionId,
        crossinline inGameState: (Game.InGame<G>, Player.Human<H>) -> JsonSerializable,
        crossinline update: (LobbyDefaultLobby<G>) -> Either<LobbyError, LobbyDefaultLobby<G>>
): Messages where S : GameServer<DefaultLobby<G>, G>, S : SynchronizedGameRegistry<DefaultLobby<G>, G> =
        updateGame(gameId) { game ->
            when (game) {
                is Game.Lobby -> {
                    val joinedPlayer = game.humanPlayers[sessionId]
                    if (joinedPlayer != null) {
                        game toT lobbyStateMsgs(game)
                    } else {
                        update(game).fold(
                                { error -> game toT handleLobbyError(sessionId)(error) },
                                { updatedLobby -> updatedLobby toT lobbyStateMsgs(updatedLobby) }
                        )
                    }
                }
                is Game.InGame -> {
                    val joinedPlayer = game.humanPlayers[sessionId]
                    if (joinedPlayer != null) {
                        game toT mapOf(joinedPlayer.technical to inGameState(game, joinedPlayer))
                    } else {
                        game toT mapOf(TechnicalPlayer(sessionId = sessionId) to DefaultLobbyResponse.GameAlreadyStarted(gameId.asString()))
                    }
                }
            }
        }

suspend inline fun <E, H : Player.HumanImpl, G : InGameImplWithPlayer<H, *>, S> S.updateLobby(
        gameId: GameId,
        crossinline inGameState: (Game.InGame<G>, Player.Human<H>) -> JsonSerializable,
        crossinline update: (LobbyDefaultLobby<G>) -> Either<E, LobbyDefaultLobby<G>>
): Either<E, Messages> where S : GameServer<DefaultLobby<G>, G>, S : SynchronizedGameRegistry<DefaultLobby<G>, G> =
        updateGame(gameId, Right(noSuchGame(gameId))) { game ->
            when (game) {
                is Game.Lobby -> {
                    update(game).fold(
                            { error -> game toT Left(error) },
                            { upLobby -> upLobby toT Right(lobbyStateMsgs(upLobby)) }
                    )
                }
                is Game.InGame -> game toT Right(inGameStateMsgs(game, inGameState))
            }
        }


suspend fun <H : Player.HumanImpl, G : InGameImplWithPlayer<H, *>, S> S.handleLobbyRequest(
        lobbyRequest: LobbyRequest,
        inGameState: (Game.InGame<G>, Player.Human<H>) -> JsonSerializable
): Messages where S : GameServer<DefaultLobby<G>, G>, S : SynchronizedGameRegistry<DefaultLobby<G>, G> =
        updateGame(lobbyRequest.gameId) { game ->
            when (game) {
                is Game.InGame -> game toT inGameStateMsgs(game, inGameState)
                is Game.Lobby -> handleLobbyRequest(game, lobbyRequest, inGameState)
            }
        }

private inline fun <H : Player.HumanImpl, G : InGameImplWithPlayer<H, *>> GameServer<DefaultLobby<G>, G>.handleLobbyRequest(
        wrappedLobby: LobbyDefaultLobby<G>,
        lobbyRequest: LobbyRequest,
        inGameState: (Game.InGame<G>, Player.Human<H>) -> JsonSerializable
): Tuple2<GameDefaultLobby<G>, Messages> {
    val lobby = wrappedLobby.impl
    val updatedLobby = when (lobbyRequest) {
        is LobbyRequest.Ready -> {
            val modifiedLobby = DefaultLobby.player<G> { it.technical.playerId == lobbyRequest.playerId }
                    .modify(lobby) {
                        it.copy(isReady = lobbyRequest.content.isReady)
                    }
            if (modifiedLobby.players.size == lobby.maxPlayer && modifiedLobby.players.all { it.isReady }) {
                val inGame = lobby.startGame(this, modifiedLobby)
                return inGame toT inGameStateMsgs(inGame, inGameState)
            } else {
                modifiedLobby
            }
        }
        is LobbyRequest.Name -> {
            DefaultLobby.player<G> { it.technical.playerId == lobbyRequest.playerId }.modify(lobby) {
                it.copy(name = lobbyRequest.content.name.limit(20))
            }
        }
        is LobbyRequest.AddBot -> {
            val botNames = listOf("Skynet", "Terminator", "Wall-e", "RoboCop", "\uD83E\uDD16")
            val bot = Player.Bot(DefaultLobby.Bot(
                    botNames.random(), PlayerId.create()
            ))
            val modifiedLobby = lobby.addPlayer(bot).getOrElse { lobby }

            if (modifiedLobby.players.size == lobby.maxPlayer && modifiedLobby.players.all { it.isReady }) {
                val inGame = lobby.startGame(this, modifiedLobby)
                return inGame toT inGameStateMsgs(inGame, inGameState)
            } else {
                modifiedLobby
            }
        }
        is LobbyRequest.SetBotDifficulty -> {
            DefaultLobby.bot<G> { it.playerId == lobbyRequest.botId }.modify(lobby) {
                it.copy(difficulty = lobbyRequest.content.botDifficulty)
            }
        }
    }
    val wrapped = wrappedLobby.update(updatedLobby)
    return wrapped toT lobbyStateMsgs(wrapped)
}


suspend inline fun <H : Player.HumanImpl, G: InGameImplWithPlayer<H, *>, S> S.rematch(
        rematchManager: RematchManager<DefaultLobby<G>, G>,
        sessionId: SessionId,
        oldGameId: GameId,
        crossinline inGameState: (Game.InGame<G>, Player.Human<H>) -> JsonSerializable
): GameServer.DirectResponse where S : GameServer<DefaultLobby<G>, G>, S : SynchronizedGameRegistry<DefaultLobby<G>, G> {
    return rematchManager.rematch(sessionId, oldGameId) { rematchId, oldPlayerName ->
        if (oldPlayerName != null) {
            val technical = TechnicalPlayer(sessionId = sessionId)
            val player = DefaultLobby.Human(oldPlayerName, false, technical)
            joinLobby(rematchId, sessionId, inGameState) { lobby ->
                lobby.addNewPlayer(Player.Human(player))
            }
        } else {
            joinLobby(rematchId, sessionId, inGameState) { lobby ->
                lobby.addNewPlayer(sessionId)
            }
        }
    }
}

fun playerWith(id: PlayerId): Predicate<Player<*, *>> = { it.playerId == id }

fun handleLobbyError(sessionId: SessionId): (LobbyError) -> Messages = { error: LobbyError ->
    when (error) {
        is LobbyError.Full -> mapOf(TechnicalPlayer(sessionId = sessionId) to DefaultLobbyResponse.Full.fromError(error))
    }
}

fun <S, M> MinMaxStrategy<S, M>.withDifficulty(difficulty: DefaultLobby.Difficulty): (S) -> M {
    return when (difficulty) {
        DefaultLobby.Difficulty.CHILDS_PLAY -> this::randomMove
        DefaultLobby.Difficulty.CHALLENGE -> { state -> minMax(state, 2).move }
        DefaultLobby.Difficulty.NIGHTMARE -> { state -> minMax(state).move }
    }
}

fun messagesToDircect(sessionId: SessionId, gameId: GameId, messages: Messages): GameServer.DirectResponse {
    return if (messages.isCouldNotMatchGame()) {
        GameServer.DirectResponse(GameResponse.NoSuchGame(gameId.asString()), noMessages())
    } else {
        val respondMsg = messages.entries.firstOrNull {
            it.key.sessionId == sessionId
        } ?: throw IllegalStateException("no join response was produced")
        GameServer.DirectResponse(respondMsg.value, messages)
    }
}

fun <G : InGameImplWithPlayer<*, *>> lobbyStateMsgs(lobby: LobbyDefaultLobby<G>): MessagesOf<DefaultLobbyResponse.State> =
        lobby.humanPlayers.associate { it.technical to DefaultLobbyResponse.State.forPlayer(lobby, it) }

inline fun <H : Player.HumanImpl, G : InGameImplWithPlayer<H, *>> inGameStateMsgs(
        inGame: Game.InGame<G>,
        inGameState: (Game.InGame<G>, Player.Human<H>) -> JsonSerializable
): Messages = inGame.humanPlayers.associate { player ->
    player.technical to inGameState(inGame, player)
}