package game.stoplight

import GameId
import PlayerId
import arrow.core.*
import arrow.optics.Lens
import game.*
import game.TwoPlayerGame.Status
import update

typealias StoplightGame = GameDefaultLobby<StoplightInGame>

fun newMiseryGame(gameId: GameId): StoplightGame {
    return Game.Lobby(DefaultLobby(gameId, 2, ListK.empty()) { lobby ->
        val inGame = StoplightInGame(
                lobby.toTwoPlayerGame(),
                List(9) { StoplightInGame.CellState.EMPTY }.k()
        )

        val turnPlayer = inGame.twoPlayerGame.turnPlayer()
        val wrapped = Game.InGame(inGame)
        if (turnPlayer is Player.Bot) playBotTurn(wrapped) else wrapped
    })
}

data class StoplightInGame(
        val twoPlayerGame: TwoPlayerGame,
        val board: ListK<CellState>
) : InGameImplWithPlayer<TwoPlayerGame.Human, TwoPlayerGame.Bot> by twoPlayerGame {

    enum class CellState { GREEN, YELLOW, RED, EMPTY }

    fun setPiece(index: Int, playerId: PlayerId): Either<TwoPlayerGameError, StoplightInGame> {
        return twoPlayerGame.validatePlayer(playerId).flatMap {
            when (board[index]) {
                CellState.GREEN -> Right(board.update(index, CellState.YELLOW).k())
                CellState.YELLOW -> Right(board.update(index, CellState.RED).k())
                CellState.RED -> Left(TwoPlayerGameError.IllegalPlace(index))
                CellState.EMPTY -> Right(board.update(index, CellState.GREEN).k())
            }.map { updatedBoard ->
                val updatedTwoGame = twoPlayerGame.nextTurn().copy(status = checkStatus(updatedBoard, twoPlayerGame.turn))
                copy(
                        board = updatedBoard,
                        twoPlayerGame = updatedTwoGame
                )
            }
        }
    }

    private fun checkStatus(board: ListK<CellState>, turn: TwoPlayerGame.PlayerRef): Status {
        return board.winningIndices(CellState.EMPTY).map { (i1, i2, i3) ->
            Status.Win(turn, i1, i2, i3)
        }.getOrElse{ Status.OnGoing }
    }

    companion object {
        fun twoPlayerGame(): Lens<StoplightInGame, TwoPlayerGame> = Lens(
                get = { it.twoPlayerGame },
                set = { game, twoGame -> game.copy(twoPlayerGame = twoGame) }
        )
    }
}