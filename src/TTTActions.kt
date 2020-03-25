import arrow.core.*
import kotlinx.coroutines.Job
import messages.requests.GameRequest
import messages.requests.LobbyRequest
import messages.responses.InGameResponse
import messages.responses.LobbyResponse

suspend fun TTTGameServer.addNewPlayer(sessionId: SessionId, gameId: GameId): Messages = updateGame(gameId) { game ->
    val newPlayer = TechnicalPlayer(PlayerId.create(), sessionId, ListK.empty(), emptyMap<String, Job>().k())
    when (game) {
        is TTTGame.Lobby -> {
            game.players.firstOrNone { it.technical.sessionId == sessionId }.fold(
                    {
                        game.addPlayer(newPlayer).fold(
                                { lobbyError ->
                                    game toT mapOf(newPlayer to LobbyResponse.Full.fromError(lobbyError))
                                },
                                { updatedLobby ->
                                    updatedLobby toT lobbyStateMsgs(updatedLobby)
                                }
                        )
                    },
                    { game toT lobbyStateMsgs(game) }
            )
        }
        is TTTGame.InGame -> {
            when (sessionId) {
                game.player1.technical.sessionId, game.player2.technical.sessionId -> game toT inGameStateMsgs(game)
                else -> game toT mapOf(newPlayer to LobbyResponse.GameAlreadyStarted(game.id.asString()))
            }
        }
    }
}

suspend fun TTTGameServer.handleLobbyRequest(lobbyRequest: LobbyRequest): Messages = updateGame(lobbyRequest.gameId) { game ->
    when (game) {
        is TTTGame.InGame -> game toT inGameStateMsgs(game)
        is TTTGame.Lobby -> {
            val updatedGame = when (lobbyRequest) {
                is LobbyRequest.Ready -> {
                    val modifiedLobby = TTTGame.Lobby.player { it.technical.playerId == lobbyRequest.playerId }
                            .modify(game) {
                                it.copy(isReady = lobbyRequest.content.isReady)
                            }
                    if (modifiedLobby.players.size == 2 && modifiedLobby.players.all { it.isReady }) {
                        val (p1, p2) = modifiedLobby.players
                        val inGame = TTTGame.InGame(
                                modifiedLobby.id,
                                TTTGame.InGame.Player(p1.name, "#FF0000", p1.technical),
                                TTTGame.InGame.Player(p2.name, "#00FF00", p2.technical),
                                List(9) { TTTGame.InGame.CellState.EMPTY }.k(),
                                TTTGame.InGame.PlayerRef.P1
                        )
                        return@updateGame inGame toT inGameStateMsgs(inGame)
                    } else {
                        modifiedLobby
                    }
                }
                is LobbyRequest.Name -> {
                    TTTGame.Lobby.player { it.technical.playerId == lobbyRequest.playerId }.modify(game) {
                        it.copy(name = lobbyRequest.content.name)
                    }
                }
            }
            updatedGame toT lobbyStateMsgs(updatedGame)
        }
    }
}

suspend fun TTTGameServer.handleGameRequest(gameRequest: GameRequest): Messages = updateGame(gameRequest.gameId) { game ->
    when (game) {
        is TTTGame.Lobby -> game toT lobbyStateMsgs(game)
        is TTTGame.InGame -> {
            val updatedGame = when (gameRequest) {
                is GameRequest.SetPiece -> game.setPiece(gameRequest.content.index, gameRequest.playerId)
                        .getOrElse { game }
            }
            updatedGame toT inGameStateMsgs(updatedGame)
        }
    }
}

fun lobbyStateMsgs(lobby: TTTGame.Lobby): MessagesOf<LobbyResponse.State> = lobby.players.associate {
    it.technical to LobbyResponse.State.forPlayer(lobby, it)
}

fun inGameStateMsgs(inGame: TTTGame.InGame): MessagesOf<InGameResponse.State> = inGame.playersWithRef.associate { (player, ref) ->
    player.technical to InGameResponse.State.forPlayer(inGame, ref)
}