package skynet

import arrow.core.identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

fun bestMove(board: TTTBoard, player: TTTBoard.Player): Move {
    val weightedMoves = board.mapEmptyCellIndex { index->
            val newBoard = board.set(index, player)
            Move(index, -evalScoreBoard(newBoard, !player))
    }
//    val weightList = MutableList(9) { 55 }
//    weightedMoves.forEach { weightList[it.index] = it.score }
//    println("%2s %2s %2s\n%2s %2s %2s\n%2s %2s %2s".format(*board.toTypedArray()))
//    println()
//    println("%02d %02d %02d\n%02d %02d %02d\n%02d %02d %02d".format(*weightList.toTypedArray()))
    return weightedMoves.maxBy { it.score }!!
}

private fun evalScoreBoard(board: TTTBoard, player: TTTBoard.Player): Int {
    return when (board.status) {
        TTTBoard.Status.P1_WON -> if (player == TTTBoard.Player.P1) 1 else -1
        TTTBoard.Status.P2_WON -> if (player == TTTBoard.Player.P2) 1 else -1
        TTTBoard.Status.DRAW -> 0
        TTTBoard.Status.ONGOING -> {
            val scores = board.mapEmptyCellIndex { index ->
                val newBoard = board.set(index, player)
                -evalScoreBoard(newBoard, !player)
            }
            return scores.max()!!
        }
    }
}

private inline fun <T : Any> TTTBoard.mapEmptyCellIndex(transform: (Int) -> T): List<T> {
    return mapIndexedNotNull { index, cellState ->
        if (cellState == TTTBoard.CellState.EMPTY) {
            transform(index)
        } else {
            null
        }
    }
}

data class Move(val index: Int, val score: Int)

fun main() {
    println("Going first")
    fuzz(1_000, TTTBoard.Player.P1)
    println("Going second")
    fuzz(1_000, TTTBoard.Player.P2)
}

fun fuzz(n: Int, startingPlayer: TTTBoard.Player) = runBlocking {
    var wins = 0
    var draws = 0
    var losses = 0
    val jobs = List(n) {
        async(Dispatchers.Default) {
            var board = TTTBoard.empty()
            var cPlayer = startingPlayer
            while (board.status == TTTBoard.Status.ONGOING) {
                board = when (cPlayer) {
                    TTTBoard.Player.P1 -> {
                        val move = bestMove(board, TTTBoard.Player.P1)
                        board.set(move.index, TTTBoard.Player.P1)
                    }
                    TTTBoard.Player.P2 -> {
                        val freeIndexes = board.mapEmptyCellIndex(::identity)
                        board.set(freeIndexes.random(), TTTBoard.Player.P2)
                    }
                }
                cPlayer = !cPlayer
            }
            board
        }
    }
    jobs.map { it.await() }.forEach { board ->
        when (board.status) {
            TTTBoard.Status.P1_WON -> wins++
            TTTBoard.Status.P2_WON -> {
                losses++
                println("lost: history = ${board.history}")
            }
            TTTBoard.Status.DRAW -> draws++
            TTTBoard.Status.ONGOING -> throw IllegalStateException("welp")
        }
    }
    println("n = $n")
    println("wins = $wins")
    println("draws = $draws")
    println("losses = $losses")
}

