package game

import GameId
import Messages
import MessagesOf
import PlayerId
import SessionId
import arrow.core.*
import json.JsonSerializable
import kotlinx.coroutines.Job
import limit
import messages.requests.LobbyRequest
import messages.responses.DefaultLobbyResponse
import noSuchGame

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

inline fun <L : Game.LobbyImpl, E> Game.Lobby<L>.updateEither(transform: L.() -> Either<E, L>): Either<E, Game.Lobby<L>> =
        map(transform).map(::update)

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

fun <G : InGameImplWithPlayer<*, *>> LobbyDefaultLobby<G>.addNewPlayer(
        player: DefaultLobbyHuman
): Either<LobbyError, LobbyDefaultLobby<G>> = updateEither { addPlayer(player) }

fun <G : InGameImplWithPlayer<*, *>> LobbyDefaultLobby<G>.addNewPlayer(
        sessionId: SessionId
): Either<LobbyError, LobbyDefaultLobby<G>> {
    val technical = TechnicalPlayer(PlayerId.create(), sessionId, ListK.empty(), emptyMap<String, Job>().k())

    val player = Player.Human(DefaultLobby.Human("Player ${players.size + 1}", false, technical))
    return addNewPlayer(player, this)
}

private fun <G : InGameImplWithPlayer<*, *>> addNewPlayer(
        player: DefaultLobbyHuman,
        lobby: LobbyDefaultLobby<G>
): Either<LobbyError, LobbyDefaultLobby<G>> {
    return lobby.updateEither {
        players[player.playerId].toOption().fold(
                {
                    addPlayer(player)
                },
                { Right(this) }
        )
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
    }
    val wrapped = wrappedLobby.update(updatedLobby)
    return wrapped toT lobbyStateMsgs(wrapped)
}

fun <G : InGameImplWithPlayer<*, *>> lobbyStateMsgs(lobby: LobbyDefaultLobby<G>): MessagesOf<DefaultLobbyResponse.State> =
        lobby.humanPlayers.associate { it.technical to DefaultLobbyResponse.State.forPlayer(lobby, it) }

inline fun <H : Player.HumanImpl, G : InGameImplWithPlayer<H, *>> inGameStateMsgs(
        inGame: Game.InGame<G>,
        inGameState: (Game.InGame<G>, Player.Human<H>) -> JsonSerializable
): Messages = inGame.humanPlayers.associate { player ->
    player.technical to inGameState(inGame, player)
}
