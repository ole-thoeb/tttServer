package emil

import org.junit.Test
import skynet.TTTBoard
import skynet.bestMove
import kotlin.test.assertEquals


class SkynetTest {
    @Test
    fun dontLoose() {
        val board1 = TTTBoard(listOf(
                TTTBoard.CellState.P1, TTTBoard.CellState.EMPTY, TTTBoard.CellState.P2,
                TTTBoard.CellState.EMPTY, TTTBoard.CellState.EMPTY, TTTBoard.CellState.EMPTY,
                TTTBoard.CellState.EMPTY, TTTBoard.CellState.EMPTY, TTTBoard.CellState.P2
        ))
        assertEquals(5, bestMove(board1, TTTBoard.Player.P1).index)

        val board2 = TTTBoard(listOf(
                TTTBoard.CellState.P2, TTTBoard.CellState.P1, TTTBoard.CellState.P2,
                TTTBoard.CellState.P2, TTTBoard.CellState.P2, TTTBoard.CellState.P1,
                TTTBoard.CellState.P1, TTTBoard.CellState.EMPTY, TTTBoard.CellState.EMPTY
        ))
        assertEquals(8, bestMove(board2, TTTBoard.Player.P1).index)

        val board3 = TTTBoard(listOf(
                TTTBoard.CellState.P2, TTTBoard.CellState.EMPTY, TTTBoard.CellState.EMPTY,
                TTTBoard.CellState.EMPTY, TTTBoard.CellState.EMPTY, TTTBoard.CellState.P1,
                TTTBoard.CellState.EMPTY, TTTBoard.CellState.EMPTY, TTTBoard.CellState.P2
        ))
        assertEquals(4, bestMove(board3, TTTBoard.Player.P1).index)

        val board4 = TTTBoard(listOf(
                TTTBoard.CellState.P1, TTTBoard.CellState.EMPTY, TTTBoard.CellState.EMPTY,
                TTTBoard.CellState.P2, TTTBoard.CellState.P2, TTTBoard.CellState.EMPTY,
                TTTBoard.CellState.EMPTY, TTTBoard.CellState.EMPTY, TTTBoard.CellState.EMPTY
        ))
        assertEquals(5, bestMove(board4, TTTBoard.Player.P1).index)

    }

    @Test
    fun print() {
        println("%02d".format(-3))
    }
}