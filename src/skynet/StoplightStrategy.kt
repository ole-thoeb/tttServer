package skynet

import allEqual
import contains
import forEachFast
import mapToSet


// ==========================================================================
// Immutable things
// ==========================================================================
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

        companion object {
            val VALUES = values()
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

    override fun StoplightBoard.possibleMoves(): List<SymmetricMove> {
        val symmetries = cells.symmetries()
        return mapIndexedNotNull { index, cellState ->
            if (cellState != StoplightBoard.CellState.RED) index else null
        }.mapToSet { it.normalize(symmetries) }.map { index ->
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
        val winningIndices = WIN_INDICES.firstOrNull {
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
        return StoplightBoard(
            toMutableList().also { it[index] = newState }, /*history + HistoryEntry(index, player),*/
            player
        )
    }

    enum class CellState {
        EMPTY, GREEN, YELLOW, RED;

        companion object {
            val VALUES = values()
        }
    }

    enum class Status { MAX_WON, MIN_WON, ONGOING }

    data class HistoryEntry(val index: Int, val player: MinMaxPlayer)

    companion object {
        fun empty() = StoplightBoard(List(9) { CellState.EMPTY }, lastPlayer = MinMaxPlayer.MAX)
        val WIN_INDICES = listOf(
            intArrayOf(0, 1, 2),
            intArrayOf(3, 4, 5),
            intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6),
            intArrayOf(1, 4, 7),
            intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8),
            intArrayOf(2, 4, 6)
        )
    }
}

// ==========================================================================
// Helper
// ==========================================================================

private fun <T> List<T>.symmetries(): List<StoplightStrategy.Symmetries> =
    StoplightStrategy.Symmetries.VALUES.filter { symmetry ->
        symmetry.symmetricPairs.all { this[it.first] == this[it.second] }
    }

private fun Int.normalize(symmetries: List<StoplightStrategy.Symmetries>): Int {
    var normalised = this
    symmetries.forEachFast { symm ->
        symm.symmetricPairs.forEachFast { symmPair ->
            if (symmPair.second == normalised)
                normalised = symmPair.first
        }
    }
    return normalised
}

private fun Int.set(mask: Int, value: Int): Int {
    val r = this and mask.inv()
    return r xor value
}

private fun Int.get2(index: Int): Int {
    return this shr index * 2 and MutableBitStoplightBoard.MASK0
}

private fun Int.set2(index: Int, value: Int): Int {
    return set(MutableBitStoplightBoard.MASK0 shl 2 * index, value shl 2 * index)
}

private fun Int.swap2(index1: Int, index2: Int): Int {
    return set2(index1, get2(index2)).set2(index2, get2(index1))
}

// ==========================================================================
// Mutable things (minimal allocations)
// ==========================================================================

data class MutableStoplightBoard(
    val cells: MutableList<StoplightBoard.CellState>,
    var lastPlayer: MinMaxPlayer
) : Copyable<MutableStoplightBoard> {

    var status: StoplightBoard.Status = calculateStatus()

    fun set(index: Int, player: MinMaxPlayer) {
        val newState = when (cells[index]) {
            StoplightBoard.CellState.GREEN -> StoplightBoard.CellState.YELLOW
            StoplightBoard.CellState.YELLOW -> StoplightBoard.CellState.RED
            StoplightBoard.CellState.RED -> throw IllegalArgumentException()
            StoplightBoard.CellState.EMPTY -> StoplightBoard.CellState.GREEN
        }
        cells[index] = newState
        lastPlayer = player
        status = calculateStatus()
    }

    fun unset(index: Int, player: MinMaxPlayer) {
        val newState = when (cells[index]) {
            StoplightBoard.CellState.GREEN -> StoplightBoard.CellState.EMPTY
            StoplightBoard.CellState.YELLOW -> StoplightBoard.CellState.GREEN
            StoplightBoard.CellState.RED -> StoplightBoard.CellState.YELLOW
            StoplightBoard.CellState.EMPTY -> throw IllegalArgumentException()
        }
        cells[index] = newState
        lastPlayer = !player
        status = calculateStatus()
    }

    private fun calculateStatus(): StoplightBoard.Status {
        val winningIndices = WIN_INDICES.firstOrNull {
            cells.allEqual(it) && cells[it.first()] != StoplightBoard.CellState.EMPTY
        }
        return if (winningIndices == null) {
            StoplightBoard.Status.ONGOING
        } else {
            when (lastPlayer) {
                MinMaxPlayer.MIN -> StoplightBoard.Status.MIN_WON
                MinMaxPlayer.MAX -> StoplightBoard.Status.MAX_WON
            }
        }
    }

    override fun copy(): MutableStoplightBoard {
        return MutableStoplightBoard(cells.toMutableList(), lastPlayer)
    }

    companion object {
        fun empty() =
            MutableStoplightBoard(MutableList(9) { StoplightBoard.CellState.EMPTY }, lastPlayer = MinMaxPlayer.MAX)

        val WIN_INDICES = StoplightBoard.WIN_INDICES
    }
}

object RevertibleStoplightStrategy : RevertibleMinMaxStrategie<MutableStoplightBoard, StoplightStrategy.SymmetricMove> {
    override fun MutableStoplightBoard.possibleMoves(): List<StoplightStrategy.SymmetricMove> {
        val symmetries = cells.symmetries()

        val moves = ArrayList<StoplightStrategy.SymmetricMove>(10)
        val indexCovered = BooleanArray(9)
        for (index in 0..cells.lastIndex) {
            val cellState = cells[index]
            if (cellState == StoplightBoard.CellState.RED)
                continue
            val normalised = index.normalize(symmetries)
            if (indexCovered[normalised])
                continue
            indexCovered[normalised] = true
            moves.add(StoplightStrategy.SymmetricMove(normalised, symmetries))
        }
        return moves
    }

    override fun MutableStoplightBoard.doMove(move: StoplightStrategy.SymmetricMove, player: MinMaxPlayer) {
        set(move.index, player)
    }

    override val MutableStoplightBoard.isTerminal: Boolean
        get() = status != StoplightBoard.Status.ONGOING

    override fun MutableStoplightBoard.score(player: MinMaxPlayer): Int {
        return when (status) {
            StoplightBoard.Status.MAX_WON -> if (player == MinMaxPlayer.MAX) 1 else -1
            StoplightBoard.Status.MIN_WON -> if (player == MinMaxPlayer.MIN) 1 else -1
            StoplightBoard.Status.ONGOING -> 0
        }
    }

    override fun MutableStoplightBoard.undoMove(move: StoplightStrategy.SymmetricMove, player: MinMaxPlayer) {
        unset(move.index, player)
    }
}


// ==========================================================================
// Mutable things and with bit
// ==========================================================================

data class MutableBitStoplightBoard(
    var cells: Int,
    var lastPlayer: MinMaxPlayer
) : Copyable<MutableBitStoplightBoard> {

    enum class Symmetries(val symmetrie: StoplightStrategy.Symmetries) {

        Y_ACHES(StoplightStrategy.Symmetries.Y_ACHES) {
            private val normalMask = MASK0 or MASK3 or MASK6
            private val mirrorMask = MASK2 or MASK5 or MASK8

            override fun mirrorCells(cells: Int): Int {
                val n = cells and normalMask
                val m = cells and mirrorMask

                return cells.set(mirrorMask, n shl 4).set(normalMask, m shr 4)
            }
        },
        X_ACHES(StoplightStrategy.Symmetries.X_ACHES) {
            private val normalMask = MASK0 or MASK1 or MASK2
            private val mirrorMask = MASK6 or MASK7 or MASK8

            override fun mirrorCells(cells: Int): Int {
                val n = cells and normalMask
                val m = cells and mirrorMask

                return cells.set(mirrorMask, n shl 12).set(normalMask, m shr 12)
            }
        },

        // top left to bottom right
        TOP_BOTTOM(StoplightStrategy.Symmetries.TOP_BOTTOM) {
            override fun mirrorCells(cells: Int): Int {
                return cells.swap2(1, 3).swap2(2, 6).swap2(5, 7)
            }
        },

        // bottom left to top right
        BOTTOM_TOP(StoplightStrategy.Symmetries.BOTTOM_TOP) {
            override fun mirrorCells(cells: Int): Int {
                return cells.swap2(0, 8).swap2(1, 5).swap2(3, 7)
            }
        };

        fun mirror(index: Int): List<Int> {
            return symmetrie.mirror(index)
        }

        abstract fun mirrorCells(cells: Int): Int

        companion object {
            val VALUES = values().toList()
        }
    }

    var status: StoplightBoard.Status = calculateStatus()

    fun set(index: Int, player: MinMaxPlayer) {
        val newState = StoplightBoard.CellState.VALUES[this[index].ordinal + 1]
        this[index] = newState
        lastPlayer = player
        status = calculateStatus()
    }

    operator fun get(index: Int): StoplightBoard.CellState {
        return cellStateOf(cells.get2(index))
    }

    operator fun set(index: Int, state: StoplightBoard.CellState) {
//        println(toList())
        cells = cells.set2(index, state.ordinal)
//        println("setting $index to $state -> ${toList()}")
    }

    fun unset(index: Int, player: MinMaxPlayer) {
        val newState = StoplightBoard.CellState.VALUES[this[index].ordinal - 1]
        this[index] = newState
        lastPlayer = !player
        status = calculateStatus()
    }

    private fun calculateStatus() = calculateStatus_()//.also { println("${toList()} -> $it") }

    private fun calculateStatus_(): StoplightBoard.Status {
        val winner = when (lastPlayer) {
            MinMaxPlayer.MIN -> StoplightBoard.Status.MIN_WON
            MinMaxPlayer.MAX -> StoplightBoard.Status.MAX_WON
        }

        WIN_MASKS.forEachFast { mask ->
            if (cells and mask == ALL_GREEN and mask) return winner
            if (cells and mask == ALL_YELLOW and mask) return winner
            if (cells and mask == ALL_RED and mask) return winner
        }
        return StoplightBoard.Status.ONGOING
    }

    override fun copy(): MutableBitStoplightBoard {
        return MutableBitStoplightBoard(cells, lastPlayer)
    }

    fun toList(): List<StoplightBoard.CellState> = List(9) { this[it] }

    companion object {
        fun empty() =
            MutableBitStoplightBoard(fillIntWith(StoplightBoard.CellState.EMPTY.ordinal), lastPlayer = MinMaxPlayer.MAX)

        const val MASK0: Int = 0x3
        const val MASK1: Int = 0x3 shl 2
        const val MASK2: Int = 0x3 shl 4
        const val MASK3: Int = 0x3 shl 6
        const val MASK4: Int = 0x3 shl 8
        const val MASK5: Int = 0x3 shl 10
        const val MASK6: Int = 0x3 shl 12
        const val MASK7: Int = 0x3 shl 14
        const val MASK8: Int = 0x3 shl 16

        private val MASKS = listOf(MASK0, MASK1, MASK2, MASK3, MASK4, MASK5, MASK6, MASK7, MASK8)
        fun indexToMask(index: Int): Int = MASKS[index]
        fun cellStateOf(value: Int): StoplightBoard.CellState = StoplightBoard.CellState.VALUES[value]

        val WIN_MASKS = listOf(
            MASK0 or MASK1 or MASK2,
            MASK3 or MASK4 or MASK5,
            MASK6 or MASK7 or MASK8,
            MASK0 or MASK3 or MASK6,
            MASK1 or MASK4 or MASK7,
            MASK2 or MASK5 or MASK8,
            MASK0 or MASK4 or MASK8,
            MASK2 or MASK4 or MASK6,
        )

        private fun fillIntWith(value: Int): Int {
            var temp = 0
            repeat(32 / 2) {
                temp = temp xor (value shl it * 2)
            }
            return temp
        }

        val ALL_GREEN = fillIntWith(StoplightBoard.CellState.GREEN.ordinal)
        val ALL_YELLOW = fillIntWith(StoplightBoard.CellState.YELLOW.ordinal)
        val ALL_RED = fillIntWith(StoplightBoard.CellState.RED.ordinal)
    }
}

object RevertibleBitStoplightStrategy :
    RevertibleMinMaxStrategie<MutableBitStoplightBoard, RevertibleBitStoplightStrategy.SymmetricMove> {

    data class SymmetricMove(val index: Int, val symmetries: List<MutableBitStoplightBoard.Symmetries>) {
        fun expandIndex(): List<Int> {
            return if (symmetries.isEmpty())
                listOf(index)
            else
                symmetries.flatMapTo(HashSet()) { it.mirror(index) }.toList()
        }
    }

    override fun MutableBitStoplightBoard.possibleMoves(): List<SymmetricMove> {
        val symmetries = symmetries()

        val moves = ArrayList<SymmetricMove>(10)
        val indexCovered = BooleanArray(9)
        for (index in 0..8) {
            val cellState = this[index]
            if (cellState == StoplightBoard.CellState.RED)
                continue
            val normalised = index.normalize(symmetries)
            if (indexCovered[normalised])
                continue
            indexCovered[normalised] = true
            moves.add(SymmetricMove(normalised, symmetries))
        }
        return moves
    }

    override fun MutableBitStoplightBoard.doMove(move: SymmetricMove, player: MinMaxPlayer) {
        set(move.index, player)
    }

    override val MutableBitStoplightBoard.isTerminal: Boolean
        get() = status != StoplightBoard.Status.ONGOING

    override fun MutableBitStoplightBoard.score(player: MinMaxPlayer): Int {
        return when (status) {
            StoplightBoard.Status.MAX_WON -> if (player == MinMaxPlayer.MAX) 1 else -1
            StoplightBoard.Status.MIN_WON -> if (player == MinMaxPlayer.MIN) 1 else -1
            StoplightBoard.Status.ONGOING -> 0
        }
    }

    override fun MutableBitStoplightBoard.undoMove(move: SymmetricMove, player: MinMaxPlayer) {
        unset(move.index, player)
    }

    private fun MutableBitStoplightBoard.symmetries(): List<MutableBitStoplightBoard.Symmetries> {
        val symmetries = ArrayList<MutableBitStoplightBoard.Symmetries>(MutableBitStoplightBoard.Symmetries.VALUES.size)
        MutableBitStoplightBoard.Symmetries.VALUES.forEachFast { symmetry ->
            if (cells == symmetry.mirrorCells(cells))
                symmetries.add(symmetry)
        }
        return symmetries
    }

    private fun Int.normalize(symmetries: List<MutableBitStoplightBoard.Symmetries>): Int {
        var normalised = this
        symmetries.forEachFast { symm ->
            symm.symmetrie.symmetricPairs.forEachFast { symmPair ->
                if (symmPair.second == normalised)
                    normalised = symmPair.first
            }
        }
        return normalised
    }
}