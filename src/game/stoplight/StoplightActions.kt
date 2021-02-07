package game.stoplight

import GameId
import arrow.core.constant
import arrow.core.identity
import arrow.core.toT
import game.*
import messages.requests.MiseryGameRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import messages.Messages
import messages.noMessages
import messages.requests.StoplightGameRequest
import messages.responses.StoplightInGameResponse
import runCommand
import skynet.MinMaxPlayer
import skynet.StoplightBoard
import skynet.StoplightStrategy
import java.io.File
import kotlin.random.Random

suspend fun StoplightGameServer.handleGameRequest(gameRequest: StoplightGameRequest): Messages = updateGame(gameRequest.gameId) { game ->
    when (game) {
        is Game.Lobby -> game toT lobbyStateMsgs(game)
        is Game.InGame -> {
            val updatedGame = when (gameRequest) {
                is StoplightGameRequest.SetPiece -> game.update {
                    setPiece(gameRequest.content.index, gameRequest.playerId).fold(constant(this), { updatedGame ->
                        val nextRoundPlayer = updatedGame.twoPlayerGame.turnPlayer()
                        if (updatedGame.twoPlayerGame.status == TwoPlayerGame.Status.OnGoing && nextRoundPlayer is Player.Bot) {
                            launchBotSetPieceAction(updatedGame.id)
                        }
                        updatedGame
                    })
                }
            }
            updatedGame toT inGameStateMsgs(updatedGame)
        }
    }
}

fun StoplightGameServer.launchBotSetPieceAction(gameId: GameId): Job = launchAsyncAction {
    delay(Random.nextLong(500, 1500))
    asyncUpdateGame(gameId) { game ->
        when (game) {
            is Game.Lobby -> {
                log.error("[PlayBotTurn] failed because game was a lobby")
                game toT noMessages()
            }
            is Game.InGame -> {
                val updatedGame = playBotTurn(game)
                updatedGame toT inGameStateMsgs(updatedGame)
            }
        }
    }
}

fun GameServer<*, *>.playBotTurn(game: Game.InGame<StoplightInGame>): Game.InGame<StoplightInGame> = game.update {
    val turnPlayer = twoPlayerGame.turnPlayer()
    fun setPiece(index: Int) = setPiece(index, turnPlayer.playerId).fold(
        { e ->
            log.error("[PlayBotTurn] failed to set piece with error $e")
            this
        },
        ::identity
    )
    if (turnPlayer !is Player.Bot) {
        log.warn("[PlayBotTurn] but current player is not a bot")
        return game
    } else if (turnPlayer.impl.difficulty == DefaultLobby.Difficulty.NIGHTMARE) {
        val boardString = board.map { state ->
            when (state) {
                StoplightInGame.CellState.EMPTY -> "e"
                StoplightInGame.CellState.GREEN -> "g"
                StoplightInGame.CellState.YELLOW -> "y"
                StoplightInGame.CellState.RED -> "r"
            }
        }.joinToString(",")
        val cmd = if (System.getProperty("os.name").contains("Windows")) "resources\\rust_ki.exe" else "./resources/rust_ki"
        val (out, err) = "$cmd $boardString".runCommand(File("./resources")) ?: TODO()
        log.debug(err)
        val res =  out.trim().toIntOrNull()
        if (res != null && res != -1) {
            setPiece(res)
        } else {
            TODO()
        }
    } else {
        val move = StoplightStrategy.withDifficulty(turnPlayer.impl.difficulty)(StoplightBoard(board.map { state ->
            when (state) {
                StoplightInGame.CellState.EMPTY -> StoplightBoard.CellState.EMPTY
                StoplightInGame.CellState.GREEN -> StoplightBoard.CellState.GREEN
                StoplightInGame.CellState.YELLOW -> StoplightBoard.CellState.YELLOW
                StoplightInGame.CellState.RED -> StoplightBoard.CellState.RED
            }
        }, lastPlayer = MinMaxPlayer.MIN))
        setPiece(move.expandIndex().random())
    }
}


fun inGameStateMsgs(inGame: Game.InGame<StoplightInGame>): Messages =
        inGameStateMsgs(inGame, StoplightInGameResponse.State.Companion::forPlayer)