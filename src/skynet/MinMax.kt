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

fun <M, S> MinMaxStrategy<S, M>.minMax(state : S, maxLevel: Int = -1): ScoredMove<M> {

    val weightedMoves = state.possibleMoves().map { move ->
        val newState = state.doMove(move, MinMaxPlayer.MAX)
        ScoredMove(-minMaxEval(newState, MinMaxPlayer.MIN, maxLevel, 0), move)
    }
//    val weightList = MutableList(9) { 55 }
//    weightedMoves.forEach { weightList[it.move as Int] = it.score }
//    println()
//    println("%02d %02d %02d\n%02d %02d %02d\n%02d %02d %02d".format(*weightList.toTypedArray()))
    return weightedMoves.allMaxsBy { it.score }.random()
}

private fun <S, M> MinMaxStrategy<S, M>.minMaxEval(state : S, player: MinMaxPlayer, maxLevel: Int, currentLevel: Int): Int {
    return if (state.isTerminal || maxLevel == currentLevel) {
        state.score(player)
    } else {
        val scores = state.possibleMoves().map { move ->
            val newState = state.doMove(move, player)
            -minMaxEval(newState, !player, maxLevel, currentLevel + 1)
        }
        scores.max()!!
    }
}

fun <M, S> MinMaxStrategy<S, M>.alphaBeta(state : S, maxLevel: Int): ScoredMove<M> {
    val weightedMoves = state.possibleMoves().map { move ->
        val newState = state.doMove(move, MinMaxPlayer.MAX)
        ScoredMove(-alphaBetaEval(newState, MinMaxPlayer.MIN, maxLevel - 1, -Int.MAX_VALUE, Int.MAX_VALUE), move)
    }
//    val weightList = MutableList(9) { 55 }
//    weightedMoves.forEach { weightList[it.move as Int] = it.score }
//    println()
//    println("%02d %02d %02d\n%02d %02d %02d\n%02d %02d %02d".format(*weightList.toTypedArray()))
    return weightedMoves.allMaxsBy { it.score }.random()
}

private fun <S, M> MinMaxStrategy<S, M>.alphaBetaEval(state : S, player: MinMaxPlayer, level: Int, alpha: Int, beta: Int): Int {
    return if (state.isTerminal || level == 0) {
        state.score(player) * (level + 1)
    } else {
        var maxValue = alpha
        val scores = state.possibleMoves().map { move ->
            if (maxValue >= beta)
                Int.MAX_VALUE
            else {
                val newState = state.doMove(move, player)
                val score = -alphaBetaEval(newState, !player, level - 1, -beta, -maxValue)
                if (score > maxValue)
                    maxValue = score
                score
            }
        }
        scores.max()!!
    }
}

fun <M, S> MinMaxStrategy<S, M>.randomMove(state : S): M {
    return state.possibleMoves().toList().random()
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