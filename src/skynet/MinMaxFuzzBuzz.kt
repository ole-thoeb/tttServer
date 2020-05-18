package skynet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking


val analyseTTT = { board: TTTBoard ->
    when (board.status) {
        TTTBoard.Status.P1_WON -> Analyse(wins = 1)
        TTTBoard.Status.P2_WON -> {
            println("lost: history = ${board.history}")
            Analyse(losses = 1)
        }
        TTTBoard.Status.DRAW -> Analyse(draws = 1)
        TTTBoard.Status.ONGOING -> throw IllegalStateException("welp")
    }
}

val analyseMisery = { board: MiseryBoard ->
    when (board.status) {
        MiseryBoard.Status.MAX_WON -> Analyse(wins = 1)
        MiseryBoard.Status.MIN_WON -> {
            println("lost: history = ${board.history}")
            Analyse(losses = 1)
        }
        MiseryBoard.Status.ONGOING -> throw IllegalStateException("welp")
    }
}

fun main() {
//    println("Going first")
//    fuzz(TTTStrategy, MinMaxPlayer.MAX, TTTBoard.empty(), 500, analyseTTT)
//    println("Going second")
//    fuzz(TTTStrategy, MinMaxPlayer.MIN, TTTBoard.empty(), 500, analyseTTT)

    println("Going first")
    fuzz(MiseryStrategy, MinMaxPlayer.MAX, MiseryBoard.empty(), 500, analyseMisery)
    println("Going second")
    fuzz(MiseryStrategy, MinMaxPlayer.MIN, MiseryBoard.empty(), 500, analyseMisery)
//    val b = listOf(
//            MiseryBoard.CellState.X, MiseryBoard.CellState.X, MiseryBoard.CellState.EMPTY,
//            MiseryBoard.CellState.EMPTY, MiseryBoard.CellState.EMPTY, MiseryBoard.CellState.EMPTY,
//            MiseryBoard.CellState.EMPTY,MiseryBoard.CellState.EMPTY, MiseryBoard.CellState.EMPTY
//    )
//    val move = MiseryStrategy.minMax(MiseryBoard.empty())
//    println("best move: $move")
}

data class Analyse(val wins: Int = 0, val draws: Int = 0, val losses: Int = 0)

fun <S, M> fuzz(strategy: MinMaxStrategy<S, M>, startingPlayer: MinMaxPlayer, initialState: S, n: Int, analyse: (S) -> Analyse) = strategy.run {
    runBlocking {
        val jobs = List(n) {
            async(Dispatchers.Default) {
                var state = initialState
                var cPlayer = startingPlayer
                while (!state.isTerminal) {
                    state = when (cPlayer) {
                        MinMaxPlayer.MAX -> {
                            val move = strategy.minMax(state)
                            state.doMove(move.move, MinMaxPlayer.MAX)
                        }
                        MinMaxPlayer.MIN -> {
                            val freeIndexes = state.possibleMoves()
                            state.doMove(freeIndexes.toList().random(), MinMaxPlayer.MIN)
                        }
                    }
                    cPlayer = !cPlayer
                }
                state
            }
        }
        val analyses = jobs.map { it.await() }.map(analyse)
        println("n = $n")
        println("wins = ${analyses.sumBy { it.wins }}")
        println("draws = ${analyses.sumBy { it.draws }}")
        println("losses = ${analyses.sumBy { it.losses }}")
    }
}