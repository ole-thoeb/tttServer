package game.ttt

import GameId
import arrow.core.constant
import arrow.core.identity
import arrow.core.toT
import game.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import messages.Messages
import messages.noMessages
import messages.requests.TTTGameRequest
import messages.responses.TTTInGameResponse
import skynet.TTTBoard
import skynet.TTTStrategy
import skynet.minMax
import kotlin.random.Random

suspend fun TTTGameServer.handleGameRequest(gameRequest: TTTGameRequest): Messages = updateGame(gameRequest.gameId) { game ->
    when (game) {
        is Game.Lobby -> game toT lobbyStateMsgs(game)
        is Game.InGame -> {
            val updatedGame = when (gameRequest) {
                is TTTGameRequest.SetPiece -> game.update {
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

fun TTTGameServer.launchBotSetPieceAction(gameId: GameId): Job = launchAsyncAction {
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

fun GameServer<*, *>.playBotTurn(game: Game.InGame<TTTInGame>): Game.InGame<TTTInGame> = game.update {
    val turnPlayer = twoPlayerGame.turnPlayer()
    if (turnPlayer !is Player.Bot) {
        log.warn("[PlayBotTurn] but current player is not a bot")
        return game
    } else {
        val bestMoveIndex = TTTStrategy.minMax(TTTBoard(board.map { state ->
            when (state) {
                TTTInGame.CellState.P1 -> TTTBoard.CellState.P1
                TTTInGame.CellState.P2 -> TTTBoard.CellState.P2
                TTTInGame.CellState.EMPTY -> TTTBoard.CellState.EMPTY
            }
        })).move

        setPiece(bestMoveIndex, turnPlayer.playerId).fold(
                { e ->
                    log.error("[PlayBotTurn] failed to set piece with error $e")
                    this
                },
                ::identity
        )
    }
}


fun inGameStateMsgs(inGame: Game.InGame<TTTInGame>): Messages =
        inGameStateMsgs(inGame, TTTInGameResponse.State.Companion::forPlayer)