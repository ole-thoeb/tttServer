package skynet

import allEqual
import update

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
        val winningIndices = winnIndices.firstOrNull { slice(it).allEqual() && this[it.first()] != CellState.EMPTY }
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

    enum class Player(val cellState: CellState) {
        P1(CellState.P1), P2(CellState.P2);

        operator fun not(): Player = when (this) {
            P1 -> P2
            P2 -> P1
        }
    }

    fun set(index: Int, player: Player): TTTBoard {
        require(this[index] == CellState.EMPTY)
        return TTTBoard(update(index, player.cellState), history + HistoryEntry(index, player))
    }

    enum class CellState { P1, P2, EMPTY }

    enum class Status { P1_WON, P2_WON, DRAW, ONGOING }

    data class HistoryEntry(val index: Int, val player: Player)

    companion object {
        fun empty() = TTTBoard(List(9) {CellState.EMPTY} )
    }
}