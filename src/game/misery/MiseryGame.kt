package game.misery

import GameId
import PlayerId
import arrow.core.*
import arrow.optics.Lens
import game.*
import game.TwoPlayerGame.Status
import game.ttt.TTTInGame
import game.ttt.playBotTurn
import update

//typealias MiseryGame = GameDefaultLobby<MiseryInGame>
//
//fun newMiseryGame(gameId: GameId): MiseryGame {
//    return Game.Lobby(DefaultLobby(gameId, 2, ListK.empty()) { lobby ->
//        val (p1, p2) = lobby.players.shuffled()
//        val toInGamePlayer = { player: DefaultLobbyPlayer, ref: TwoPlayerGame.PlayerRef ->
//            val (defName, color) = when (ref) {
//                TwoPlayerGame.PlayerRef.P1 -> "Player 1" to "#FF0000"
//                TwoPlayerGame.PlayerRef.P2 -> "Player 2" to "#00FF00"
//            }
//            when (player) {
//                is Player.Human -> player.map {
//                    Player.Human(TwoPlayerGame.Human(name.ifBlank { defName }, ref, technical))
//                }
//                is Player.Bot -> player.map {
//                    Player.Bot(TwoPlayerGame.Bot(name, PlayerId.create(), ref))
//                }
//            }
//        }
//        val inGame = TTTInGame(
//                TwoPlayerGame(lobby.id, toInGamePlayer(p1, TwoPlayerGame.PlayerRef.P1), toInGamePlayer(p2, TwoPlayerGame.PlayerRef.P2), TwoPlayerGame.PlayerRef.P1, Status.OnGoing),
//                List(9) { TTTInGame.CellState.EMPTY }.k()
//        )
//
//        val turnPlayer = inGame.twoPlayerGame.turnPlayer()
//        val wrapped = Game.InGame(inGame)
//        if (turnPlayer is Player.Bot) playBotTurn(wrapped) else wrapped
//    })
//}
//
//data class MiseryInGame(
//        val twoPlayerGame: TwoPlayerGame,
//        val board: ListK<CellState>
//) : InGameImplWithPlayer<TwoPlayerGame.Human, TwoPlayerGame.Bot> by twoPlayerGame {
//
//    enum class CellState { X, EMPTY }
//
//    fun setPiece(index: Int, playerId: PlayerId): Either<TwoPlayerGameError, MiseryInGame> {
//        return twoPlayerGame.getPlayerForPiece(board, CellState.EMPTY, twoPlayerGame.status, index, playerId).map {
//            val updatedBoard = board.update(index, CellState.X).k()
//            val updatedTwoGame = twoPlayerGame.nextTurn().copy(status = checkStatus(updatedBoard, twoPlayerGame.turn))
//            copy(
//                    board = updatedBoard,
//                    twoPlayerGame = updatedTwoGame
//            )
//        }
//    }
//
//    private fun checkStatus(board: ListK<CellState>, turn: TwoPlayerGame.PlayerRef): Status {
//        return board.winningIndices(CellState.EMPTY).map { (i1, i2, i3) ->
//            Status.Win(turn, i1, i2, i3)
//        }.getOrElse{ board.nonWinningStatus(CellState.EMPTY) }
//    }
//
//    companion object {
//        fun twoPlayerGame(): Lens<MiseryInGame, TwoPlayerGame> = Lens(
//                get = { it.twoPlayerGame },
//                set = { game, twoGame -> game.copy(twoPlayerGame = twoGame) }
//        )
//    }
//}