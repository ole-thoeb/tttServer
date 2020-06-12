package game.misery

import GameId
import PlayerId
import arrow.core.*
import arrow.optics.Lens
import game.*
import game.TwoPlayerGame.Status
import game.ttt.TTTInGame
import game.ttt.playBotTurn
import update

typealias MiseryGame = GameDefaultLobby<MiseryInGame>

fun newMiseryGame(gameId: GameId): MiseryGame {
    return Game.Lobby(DefaultLobby(gameId, 2, ListK.empty()) { lobby ->
        val inGame = MiseryInGame(
                lobby.toTwoPlayerGame(),
                List(9) { MiseryInGame.CellState.EMPTY }.k()
        )

        val turnPlayer = inGame.twoPlayerGame.turnPlayer()
        val wrapped = Game.InGame(inGame)
        if (turnPlayer is Player.Bot) playBotTurn(wrapped) else wrapped
    })
}

data class MiseryInGame(
        val twoPlayerGame: TwoPlayerGame,
        val board: ListK<CellState>
) : InGameImplWithPlayer<TwoPlayerGame.Human, TwoPlayerGame.Bot> by twoPlayerGame {

    enum class CellState { X, EMPTY }

    fun setPiece(index: Int, playerId: PlayerId): Either<TwoPlayerGameError, MiseryInGame> {
        return twoPlayerGame.getPlayerForPiece(board, CellState.EMPTY, index, playerId).map {
            val updatedBoard = board.update(index, CellState.X).k()
            val updatedTwoGame = twoPlayerGame.nextTurn().copy(status = checkStatus(updatedBoard, !twoPlayerGame.turn))
            copy(
                    board = updatedBoard,
                    twoPlayerGame = updatedTwoGame
            )
        }
    }

    private fun checkStatus(board: ListK<CellState>, turn: TwoPlayerGame.PlayerRef): Status {
        return board.winningIndices(CellState.EMPTY).map { (i1, i2, i3) ->
            Status.Win(turn, i1, i2, i3)
        }.getOrElse{ board.nonWinningStatus(CellState.EMPTY) }
    }

    companion object {
        fun twoPlayerGame(): Lens<MiseryInGame, TwoPlayerGame> = Lens(
                get = { it.twoPlayerGame },
                set = { game, twoGame -> game.copy(twoPlayerGame = twoGame) }
        )
    }
}