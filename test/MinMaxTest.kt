package emil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test
import skynet.*
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

internal class MinMaxTest {

    @ExperimentalTime
    @Test
    fun timeStoplight() {
//        val timeHeavy = measureTime {
//            println(StoplightStrategy.alphaBeta(StoplightBoard.empty(), 14))
//        }
//        println("Stoplight AllocationHeavy took: $timeHeavy")

        val timeLight = measureTime {
            RevertibleStoplightStrategy.alphaBeta(MutableStoplightBoard.empty(), 15)
        }
        println("Stoplight AllocationLight took: $timeLight")

//        val timeBit = measureTime {
//            RevertibleBitStoplightStrategy.alphaBeta(MutableBitStoplightBoard.empty(), 15)
//        }
//        println("Stoplight Bit took: $timeBit")
    }

    @Test
    fun fuzzStoplight() {
//        run {
//            val analyse = { board: MutableStoplightBoard ->
//                when (board.status) {
//                    StoplightBoard.Status.MAX_WON -> Analyse(wins = 1)
//                    StoplightBoard.Status.MIN_WON -> Analyse(losses = 1)
//                    StoplightBoard.Status.ONGOING -> throw IllegalStateException("welp")
//                }
//            }
//            println(
//                fuzz(
//                    RevertibleStoplightStrategy,
//                    MinMaxPlayer.MAX,
//                    MutableStoplightBoard.empty(),
//                    5_000,
//                    15,
//                    analyse
//                )
//            )
//        }

//        run {
//            val analyse = { board: MutableBitStoplightBoard ->
//                when (board.status) {
//                    StoplightBoard.Status.MAX_WON -> Analyse(wins = 1)
//                    StoplightBoard.Status.MIN_WON -> Analyse(losses = 1)
//                    StoplightBoard.Status.ONGOING -> throw IllegalStateException("welp")
//                }
//            }
//            println(
//                fuzz(
//                    RevertibleBitStoplightStrategy,
//                    MinMaxPlayer.MAX,
//                    MutableBitStoplightBoard.empty(),
//                    100,
//                    13,
//                    analyse
//                )
//            )
//        }
    }

    private data class Analyse(val wins: Int = 0, val draws: Int = 0, val losses: Int = 0)

    private fun <S : Copyable<S>, M> fuzz(
        strategy: RevertibleMinMaxStrategie<S, M>,
        startingPlayer: MinMaxPlayer,
        initialState: S,
        n: Int,
        depth: Int,
        analyse: (S) -> Analyse
    ): Analyse = strategy.run {
        runBlocking {
            val jobs = List(n) {
                async(Dispatchers.Default) {
                    val state = initialState.copy()
                    var cPlayer = startingPlayer
                    while (!state.isTerminal) {
                        when (cPlayer) {
                            MinMaxPlayer.MAX -> {
                                val move = strategy.alphaBeta(state.copy(), depth)
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
            Analyse(analyses.sumBy { it.wins }, analyses.sumBy { it.draws }, analyses.sumBy { it.losses })
        }
    }
}