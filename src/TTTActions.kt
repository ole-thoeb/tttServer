import TTTGame.InGame.PlayerRef
import arrow.core.*
import kotlinx.coroutines.*
import messages.requests.GameRequest
import messages.requests.LobbyRequest
import messages.responses.InGameResponse
import messages.responses.LobbyResponse
import skynet.TTTBoard
import skynet.bestMove
import kotlin.random.Random

suspend fun TTTGameServer.addNewPlayer(sessionId: SessionId, gameId: GameId): Messages = updateGame(gameId) { game ->
    val technical = TechnicalPlayer(PlayerId.create(), sessionId, ListK.empty(), emptyMap<String, Job>().k())
    game.joinLobby(technical) { lobby ->
        val player = TTTGame.Lobby.Player.Human("Player ${lobby.players.size + 1}", false, technical)
        addNewPlayer(player, lobby)
    }
}

suspend fun TTTGameServer.addNewPlayer(player: TTTGame.Lobby.Player.Human, gameId: GameId): Messages = updateGame(gameId) { game ->
    game.joinLobby(player.technical) { lobby ->
        addNewPlayer(player, lobby)
    }
}

private fun addNewPlayer(player: TTTGame.Lobby.Player.Human, lobby: TTTGame.Lobby): Tuple2<TTTGame, Messages> {
    val technical = player.technical
    return lobby[player.technical.sessionId].toOption().fold(
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
    is TTTGame.InGame -> when {
        this[player.sessionId] != null -> this toT inGameStateMsgs(this)
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
                        val toInGamePlayer = { player: TTTGame.Lobby.Player, ref: PlayerRef ->
                            val (name, color) = when (ref) {
                                PlayerRef.P1 -> "Player 1" to "#FF0000"
                                PlayerRef.P2 -> "Player 2" to "#00FF00"
                            }
                            when (player) {
                                is TTTGame.Lobby.Player.Human ->
                                    TTTGame.InGame.Player.Human(player.name.ifBlank { name }, color, ref, player.technical)

                                is TTTGame.Lobby.Player.Bot ->
                                    TTTGame.InGame.Player.Bot(player.name, PlayerId.create(), color, ref)
                            }
                        }
                        val inGame = TTTGame.InGame(
                                modifiedLobby.id,
                                toInGamePlayer(p1, PlayerRef.P1),
                                toInGamePlayer(p2, PlayerRef.P2),
                                List(9) { TTTGame.InGame.CellState.EMPTY }.k(),
                                PlayerRef.P1,
                                TTTGame.InGame.Status.OnGoing
                        )

                        val turnPlayer = inGame[inGame.turn]
                        if (turnPlayer is TTTGame.InGame.Player.Bot) {
                            launchBotSetPieceAction(inGame.id)
                        }

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
                is LobbyRequest.AddBot -> {
                    val botNames = listOf("Skynet", "Terminator", "Wall-e", "RoboCop", "\uD83E\uDD16")
                    val bot = TTTGame.Lobby.Player.Bot(botNames.random(), PlayerId.create())
                    game.addPlayer(bot).getOrElse { game }
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
                        .fold(constant(game), { updatedGame ->
                            val nextRoundPlayer = updatedGame[updatedGame.turn]
                            if (updatedGame.status == TTTGame.InGame.Status.OnGoing &&
                                    nextRoundPlayer is TTTGame.InGame.Player.Bot) {
                                launchBotSetPieceAction(updatedGame.id)
                            }
                            updatedGame
                        })
            }
            updatedGame toT inGameStateMsgs(updatedGame)
        }
    }
}

fun TTTGameServer.launchBotSetPieceAction(gameId: GameId): Job = launchAsyncAction {
    //delay(Random.nextLong(500, 2000))
    asyncUpdateGame(gameId) { game ->
        when (game) {
            is TTTGame.Lobby -> {
                log.error("bot failed to set piece because the game was a lobby")
                game toT noMessages()
            }
            is TTTGame.InGame -> {
                val turnPlayer = game[game.turn]
                if (turnPlayer !is TTTGame.InGame.Player.Bot) {
                    log.warn("BotSetPiecAction but current palyer is not a bot")
                    game toT noMessages()
                } else {
                    val turnPlayerMappedRef = when (turnPlayer.ref) {
                        PlayerRef.P1 -> TTTBoard.Player.P1
                        PlayerRef.P2 -> TTTBoard.Player.P2
                    }
                    val bestMoveIndex = bestMove(TTTBoard(game.board.map { state ->
                        when (state) {
                            TTTGame.InGame.CellState.P1 -> TTTBoard.CellState.P1
                            TTTGame.InGame.CellState.P2 -> TTTBoard.CellState.P2
                            TTTGame.InGame.CellState.EMPTY -> TTTBoard.CellState.EMPTY
                        }
                    }), turnPlayerMappedRef).index

                    val updatedGame: TTTGame.InGame = game.setPiece(bestMoveIndex, turnPlayer.playerId).fold(
                            { e ->
                                log.error("bot failed to set piece with error $e")
                                game
                            },
                            ::identity
                    )
                    updatedGame toT inGameStateMsgs(updatedGame)
                }
            }
        }
    }
}

fun lobbyStateMsgs(lobby: TTTGame.Lobby): MessagesOf<LobbyResponse.State> = lobby.players
        .filter(TTTGame.Lobby.Player.Human::class)
        .associate { it.technical to LobbyResponse.State.forPlayer(lobby, it) }

fun inGameStateMsgs(inGame: TTTGame.InGame): MessagesOf<InGameResponse.State> = inGame.players
        .filter(TTTGame.InGame.Player.Human::class)
        .associate { player -> player.technical to InGameResponse.State.forPlayer(inGame, player) }