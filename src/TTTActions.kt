import arrow.core.*
import kotlinx.coroutines.Job
import messages.requests.GameRequest
import messages.requests.LobbyRequest
import messages.responses.InGameResponse
import messages.responses.LobbyResponse

suspend fun TTTGameServer.addNewPlayer(sessionId: SessionId, gameId: GameId): Messages = updateGame(gameId) { game ->
    val technical = TechnicalPlayer(PlayerId.create(), sessionId, ListK.empty(), emptyMap<String, Job>().k())
    game.joinLobby(technical) { lobby ->
        val player = TTTGame.Lobby.Player("Player ${lobby.players.size + 1}", false, technical)
        addNewPlayer(player, lobby)
    }
}

suspend fun TTTGameServer.addNewPlayer(player: TTTGame.Lobby.Player, gameId: GameId): Messages = updateGame(gameId) { game ->
    game.joinLobby(player.technical) { lobby ->
        addNewPlayer(player, lobby)
    }
}

private fun addNewPlayer(player: TTTGame.Lobby.Player, lobby: TTTGame.Lobby): Tuple2<TTTGame, Messages> {
    val technical = player.technical
    return lobby.players.firstOrNone { it.technical.sessionId == technical.sessionId }.fold(
            {
                lobby.addPlayer(player).fold(
                        { lobbyError ->
                            lobby toT mapOf(technical to LobbyResponse.Full.fromError(lobbyError))
                        },
                        { updatedLobby ->
                            updatedLobby toT lobbyStateMsgs(updatedLobby)
                        }
                )
            },
            { lobby toT lobbyStateMsgs(lobby) }
    )
}

private inline fun TTTGame.joinLobby(
        player: TechnicalPlayer,
        addPlayer: (TTTGame.Lobby) -> Tuple2<TTTGame, Messages>
): Tuple2<TTTGame, Messages> = when (this) {

    is TTTGame.Lobby -> addPlayer(this)
    is TTTGame.InGame -> when (player.sessionId) {
        player1.technical.sessionId, player2.technical.sessionId -> this toT inGameStateMsgs(this)
        else -> this toT mapOf(player to LobbyResponse.GameAlreadyStarted(id.asString()))
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
                        val (p1, p2) = modifiedLobby.players.shuffled()
                        val inGame = TTTGame.InGame(
                                modifiedLobby.id,
                                TTTGame.InGame.Player(p1.name.ifBlank { "Player 1" }, "#FF0000", p1.technical),
                                TTTGame.InGame.Player(p2.name.ifBlank { "Player 2" }, "#00FF00", p2.technical),
                                List(9) { TTTGame.InGame.CellState.EMPTY }.k(),
                                TTTGame.InGame.PlayerRef.P1,
                                TTTGame.InGame.Status.OnGoing
                        )
                        return@updateGame inGame toT inGameStateMsgs(inGame)
                    } else {
                        modifiedLobby
                    }
                }
                is LobbyRequest.Name -> {
                    TTTGame.Lobby.player { it.technical.playerId == lobbyRequest.playerId }.modify(game) {
                        it.copy(name = lobbyRequest.content.name.limit(20))
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