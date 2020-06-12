package skynet

import allEqual
import update


object StoplightStrategy : MinMaxStrategy<StoplightBoard, Int> {
    override fun StoplightBoard.possibleMoves(): List<Int> {
        return mapIndexedNotNull { index, cellState ->
            if (cellState != StoplightBoard.CellState.RED) index else null
        }
    }

    override fun StoplightBoard.doMove(move: Int, player: MinMaxPlayer): StoplightBoard {
        return set(move, player)
    }

    override val StoplightBoard.isTerminal: Boolean
        get() = status != StoplightBoard.Status.ONGOING

    override fun StoplightBoard.score(player: MinMaxPlayer): Int {
        return when (status) {
            StoplightBoard.Status.MAX_WON -> if (player == MinMaxPlayer.MAX) 1 else -1
            StoplightBoard.Status.MIN_WON -> if (player == MinMaxPlayer.MIN) 1 else -1
            StoplightBoard.Status.ONGOING -> 0
        }
    }
}

data class StoplightBoard(
        val cells: List<CellState>,
        val history: List<HistoryEntry> = emptyList(),
        val lastPlayer: MinMaxPlayer
) : List<StoplightBoard.CellState> by cells {

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
            Status.ONGOING
        } else {
            when (lastPlayer) {
                MinMaxPlayer.MIN -> Status.MIN_WON
                MinMaxPlayer.MAX -> Status.MAX_WON
            }
        }
    }

    fun set(index: Int, player: MinMaxPlayer): StoplightBoard {
        val newState = when (this[index]) {
            CellState.GREEN -> CellState.YELLOW
            CellState.YELLOW -> CellState.RED
            CellState.RED -> throw IllegalArgumentException()
            CellState.EMPTY -> CellState.GREEN
        }
        return StoplightBoard(update(index, newState), history + HistoryEntry(index, player), player)
    }

    enum class CellState { GREEN, YELLOW, RED, EMPTY }

    enum class Status { MAX_WON, MIN_WON, ONGOING }

    data class HistoryEntry(val index: Int, val player: MinMaxPlayer)

    companion object {
        fun empty() = StoplightBoard(List(9) { CellState.EMPTY }, lastPlayer = MinMaxPlayer.MAX)
    }
}