package skynet

import allEqual
import update


object MiseryStrategy : MinMaxStrategy<MiseryBoard, Int> {
    override fun MiseryBoard.possibleMoves(): List<Int> {
        return mapIndexedNotNull { index, cellState ->
            if (cellState == MiseryBoard.CellState.EMPTY) index else null
        }
    }

    override fun MiseryBoard.doMove(move: Int, player: MinMaxPlayer): MiseryBoard {
        return set(move, player)
    }

    override val MiseryBoard.isTerminal: Boolean
        get() = status != MiseryBoard.Status.ONGOING

    override fun MiseryBoard.score(player: MinMaxPlayer): Int {
        return when (status) {
            MiseryBoard.Status.MAX_WON -> if (player == MinMaxPlayer.MAX) 1 else -1
            MiseryBoard.Status.MIN_WON -> if (player == MinMaxPlayer.MIN) 1 else -1
            MiseryBoard.Status.ONGOING -> 0
        }
    }
}

data class MiseryBoard(
        val cells: List<CellState>,
        val history: List<HistoryEntry> = emptyList(),
        val lastPlayer: MinMaxPlayer
) : List<MiseryBoard.CellState> by cells {

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
                MinMaxPlayer.MIN -> Status.MAX_WON
                MinMaxPlayer.MAX -> Status.MIN_WON
            }
        }
    }

    fun set(index: Int, player: MinMaxPlayer): MiseryBoard {
        require(this[index] == CellState.EMPTY)
        return MiseryBoard(update(index, CellState.X), history + HistoryEntry(index, player), player)
    }

    enum class CellState { X, EMPTY }

    enum class Status { MAX_WON, MIN_WON, ONGOING }

    data class HistoryEntry(val index: Int, val player: MinMaxPlayer)

    companion object {
        fun empty() = MiseryBoard(List(9) { CellState.EMPTY }, lastPlayer = MinMaxPlayer.MAX)
    }
}