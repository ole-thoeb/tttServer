package skynet



interface MinMaxStrategy<S, M> {
    fun S.possibleMoves(): Iterable<M>
    fun S.doMove(move: M, player: MinMaxPlayer): S
    val S.isTerminal: Boolean
    fun S.score(player: MinMaxPlayer): Int
}


enum class MinMaxPlayer {
    MIN, MAX;

    operator fun not(): MinMaxPlayer = when (this) {
        MIN -> MAX
        MAX -> MIN
    }
}

data class ScoredMove<M>(val score: Int, val move: M)

fun <M, S> MinMaxStrategy<S, M>.minMax(state : S): ScoredMove<M> {

    val weightedMoves = state.possibleMoves().map { move ->
        val newState = state.doMove(move, MinMaxPlayer.MAX)
        ScoredMove(-minMaxEval(newState, MinMaxPlayer.MIN), move)
    }
//    val weightList = MutableList(9) { 55 }
//    weightedMoves.forEach { weightList[it.move as Int] = it.score }
//    println()
//    println("%02d %02d %02d\n%02d %02d %02d\n%02d %02d %02d".format(*weightList.toTypedArray()))
    return weightedMoves.allMaxsBy { it.score }.random()
}

private fun <S, M> MinMaxStrategy<S, M>.minMaxEval(state : S, player: MinMaxPlayer): Int {
    return if (state.isTerminal) {
        state.score(player)
    } else {
        val scores = state.possibleMoves().map { move ->
            val newState = state.doMove(move, player)
            -minMaxEval(newState, !player)
        }
        scores.max()!!
    }
}

private fun <T, C : Comparable<C>> List<T>.allMaxsBy(selector: (T) -> C): List<T> {
    val iter = iterator()
    if (!iter.hasNext()) return emptyList()
    val first = iter.next()
    var max = selector(first)
    val maxs = mutableListOf(first)
    while (iter.hasNext()) {
        val next = iter.next()
        val nextSelector = selector(next)
        if (nextSelector > max) {
            max = nextSelector
            maxs.clear()
            maxs.add(next)
        } else if (nextSelector == max) {
            maxs.add(next)
        }
    }
    return maxs
}