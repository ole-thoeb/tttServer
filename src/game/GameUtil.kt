package game

import PlayerId
import allEqual
import arrow.core.*

fun <C> List<C>.winningIndices(empty: C): Either<Option<TwoPlayerGame.Status>, List<Int>> {
    if (size != 9) return Left(Option.just(TwoPlayerGame.Status.OnGoing))
    val indexes = listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), listOf(0, 4, 8), listOf(2, 4, 6))
    return indexes.firstOrNone {
        slice(it).allEqual() && this[it.first()] != empty
    }.toEither {
        Option.empty<TwoPlayerGame.Status>()
    }
}

fun <C> List<C>.nonWinningStatus(empty: C): TwoPlayerGame.Status {
    return if (all { it != empty }) TwoPlayerGame.Status.Draw else TwoPlayerGame.Status.OnGoing
}

fun <C> TwoPlayerGame.getPlayerForPiece(
        board: List<C>, empty: C,
        status: TwoPlayerGame.Status,
        index: Int, playerId: PlayerId
): Either<TwoPlayerGameError, TwoPlayerGamePlayer> {
    if (status != TwoPlayerGame.Status.OnGoing) return Left(TwoPlayerGameError.IllegalStatus(status))
    val playerRef = getPlayerRef(playerId)
            ?: return Left(TwoPlayerGameError.WrongTurn(null, turn))

    if (playerRef != turn) return Left(TwoPlayerGameError.WrongTurn(playerRef, turn))

    if (index !in board.indices || board[index] != empty)
        return Left(TwoPlayerGameError.IllegalPlace(index))

    return Right(this[playerRef])
}

val TwoPlayerGamePlayer.ref: TwoPlayerGame.PlayerRef
    get() = when (this) {
        is Player.Human -> impl.ref
        is Player.Bot -> impl.ref
    }

fun TwoPlayerGame.turnPlayer(): TwoPlayerGamePlayer = this[turn]