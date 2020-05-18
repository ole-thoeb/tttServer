package skynet

import allEqual
import game.TwoPlayerGame
import update

object TTTStrategy : MinMaxStrategy<TTTBoard, Int> {
    override fun TTTBoard.possibleMoves(): List<Int> {
        return mapIndexedNotNull { index, cellState ->
            if (cellState == TTTBoard.CellState.EMPTY) index else null
        }
    }

    override fun TTTBoard.doMove(move: Int, player: MinMaxPlayer): TTTBoard {
        return set(move, player)
    }

    override val TTTBoard.isTerminal: Boolean
        get() = status != TTTBoard.Status.ONGOING

    override fun TTTBoard.score(player: MinMaxPlayer): Int {
        return when (status) {
            TTTBoard.Status.P1_WON -> if (player == MinMaxPlayer.MAX) 1 else -1
            TTTBoard.Status.P2_WON -> if (player == MinMaxPlayer.MIN) 1 else -1
            TTTBoard.Status.DRAW -> 0
            TTTBoard.Status.ONGOING -> 0
        }
    }
}

data class TTTBoard(
        val cells: List<CellState>,
        val history: List<HistoryEntry> = emptyList()
) : List<TTTBoard.CellState> by cells {

    val status: Status = run {
        val winnIndices = listOf(
                listOf(0, 1, 2),
                listOf(3, 4, 5),
                listOf(6, 7, 8),
                listOf(0, 3, 6),
                listOf(1, 4, 7),
                listOf(2, 5, 8),
                listOf(0, 4, 8),
                listOf(2, 4, 6)
        )
        val winningIndices = winnIndices.firstOrNull {
            slice(it).allEqual() && this[it.first()] != CellState.EMPTY
        }
        if (winningIndices == null) {
            if (any { it == CellState.EMPTY }) Status.ONGOING else Status.DRAW
        } else {
            when (this[winningIndices.first()]) {
                CellState.P1 -> Status.P1_WON
                CellState.P2 -> Status.P2_WON
                CellState.EMPTY -> Status.ONGOING
            }
        }
    }

    val MinMaxPlayer.cellState: CellState
        get() = when (this) {
            MinMaxPlayer.MIN -> CellState.P2
            MinMaxPlayer.MAX -> CellState.P1
        }

    fun set(index: Int, player: MinMaxPlayer): TTTBoard {
        require(this[index] == CellState.EMPTY)
        return TTTBoard(update(index, player.cellState), history + HistoryEntry(index, player))
    }

    enum class CellState { P1, P2, EMPTY }

    enum class Status { P1_WON, P2_WON, DRAW, ONGOING }

    data class HistoryEntry(val index: Int, val player: MinMaxPlayer)

    companion object {
        fun empty() = TTTBoard(List(9) { CellState.EMPTY })
    }
}