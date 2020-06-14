package skynet

import allEqual
import contains
import mapToSet
import slice
import update


object StoplightStrategy : MinMaxStrategy<StoplightBoard, StoplightStrategy.SymmetricMove> {

    //0 1 2
    //3 4 5
    //6 7 8

    enum class Symmetries(val symmetricPairs: List<Pair<Int, Int>>) {

        Y_ACHES(listOf(0 to 2, 3 to 5, 6 to 8)),
        X_ACHES(listOf(0 to 6, 1 to 7, 2 to 8)),
        TOP_BOTTOM(listOf(1 to 3, 2 to 6, 5 to 7)),// top left to bottom right
        BOTTOM_TOP(listOf(0 to 8, 1 to 5, 3 to 7)); // bottom left to top right

        fun mirror(index: Int): List<Int> {
            return symmetricPairs.find { symmPair -> index in symmPair }?.toList() ?: listOf(index)
        }
    }

    data class SymmetricMove(val index: Int, val symmetries: List<Symmetries>) {
        fun expandIndex(): List<Int> {
            return if (symmetries.isEmpty())
                listOf(index)
            else
                symmetries.flatMapTo(HashSet()) { it.mirror(index) }.toList()
        }
    }

    private fun <T> List<T>.symmetries(): List<Symmetries> = Symmetries.values().filter { symmetry ->
        symmetry.symmetricPairs.all { this[it.first] == this[it.second] }
    }

    private fun Int.normalize(symmPairs: List<Pair<Int, Int>>): Int {
        return symmPairs.fold(this) { acc, symmPair ->
            if (acc == symmPair.second) symmPair.first else acc
        }
    }

    override fun StoplightBoard.possibleMoves(): List<SymmetricMove> {
        val symmetries = cells.symmetries()
        val symmPairs = symmetries.flatMap { it.symmetricPairs }
        return mapIndexedNotNull { index, cellState ->
            if (cellState != StoplightBoard.CellState.RED) index else null
        }.mapToSet { it.normalize(symmPairs) }.map { index ->
            SymmetricMove(index, symmetries)
        }
    }

    override fun StoplightBoard.doMove(move: SymmetricMove, player: MinMaxPlayer): StoplightBoard {
        return set(move.index, player)
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
        //val history: List<HistoryEntry> = emptyList(),
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
            allEqual(it) && this[it.first()] != CellState.EMPTY
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
        return StoplightBoard(toMutableList().also { it[index] = newState }, /*history + HistoryEntry(index, player),*/ player)
    }

    enum class CellState { GREEN, YELLOW, RED, EMPTY }

    enum class Status { MAX_WON, MIN_WON, ONGOING }

    data class HistoryEntry(val index: Int, val player: MinMaxPlayer)

    companion object {
        fun empty() = StoplightBoard(List(9) { CellState.EMPTY }, lastPlayer = MinMaxPlayer.MAX)
    }
}