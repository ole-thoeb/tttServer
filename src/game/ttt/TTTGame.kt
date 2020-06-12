package game.ttt

import GameId
import PlayerId
import arrow.core.*
import arrow.optics.Lens
import game.*
import game.TwoPlayerGame.PlayerRef
import game.TwoPlayerGame.Status
import get
import update


typealias TTTGame = GameDefaultLobby<TTTInGame>

fun newTTTGame(gameId: GameId): TTTGame {
    return Game.Lobby(DefaultLobby(gameId, 2, ListK.empty()) { lobby ->
        val inGame = TTTInGame(
                lobby.toTwoPlayerGame(),
                List(9) { TTTInGame.CellState.EMPTY }.k()
        )

        val turnPlayer = inGame.twoPlayerGame.turnPlayer()
        val wrapped = Game.InGame(inGame)
        if (turnPlayer is Player.Bot) playBotTurn(wrapped) else wrapped
    })
}

data class TTTInGame(
        val twoPlayerGame: TwoPlayerGame,
        val board: ListK<CellState>
) : InGameImplWithPlayer<TwoPlayerGame.Human, TwoPlayerGame.Bot> by twoPlayerGame {

    enum class CellState { P1, P2, EMPTY }

    private val PlayerRef.cellState: CellState
        get() = when (this) {
            PlayerRef.P1 -> CellState.P1
            PlayerRef.P2 -> CellState.P2
        }

    fun setPiece(index: Int, playerId: PlayerId): Either<TwoPlayerGameError, TTTInGame> {
        return twoPlayerGame.getPlayerForPiece(board, CellState.EMPTY, index, playerId).map { player ->
            val updatedBoard = board.update(index, player.ref.cellState).k()
            val updatedTwoGame = twoPlayerGame.nextTurn().copy(status = checkStatus(updatedBoard))
            copy(
                    board = updatedBoard,
                    twoPlayerGame = updatedTwoGame
            )
        }
    }

    private fun checkStatus(board: ListK<CellState>): Status {
        return board.winningIndices(CellState.EMPTY).map { (i1, i2, i3) ->
            val winningPlayerRef = when (board[i1]) {
                CellState.P1 -> PlayerRef.P1
                CellState.P2 -> PlayerRef.P2
                CellState.EMPTY -> return@map Option.empty<Status>()
            }
            Option(Status.Win(winningPlayerRef, i1, i2, i3))
        }.get().getOrElse {
            board.nonWinningStatus(CellState.EMPTY)
        }
    }

    companion object {
        fun twoPlayerGame(): Lens<TTTInGame, TwoPlayerGame> = Lens(
                get = { it.twoPlayerGame },
                set = { game, twoGame -> game.copy(twoPlayerGame = twoGame) }
        )
    }
}