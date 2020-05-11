import arrow.Kind
import arrow.core.Either
import arrow.core.Option
import arrow.core.Predicate
import arrow.core.identity
import arrow.typeclasses.ApplicativeError
import arrow.typeclasses.MonadError
import game.TechnicalPlayer
import json.JsonSerializable
import messages.responses.TTTResponse
import kotlin.reflect.KClass

inline fun <F, A, E> MonadError<F, E>.tryCatch(fe: (Throwable) -> E, f: () -> A): Kind<F, A> = try {
    just(f())
} catch (t : Throwable) {
    raiseError(fe(t))
}

fun <F, T, E> T?.fromNull(ME: ApplicativeError<F, E>, fe: () -> E): Kind<F, T> =
        if (this == null) ME.raiseError(fe()) else ME.just(this)

fun <T> Either<T, T>.get(): T = fold(::identity, ::identity)

inline fun <T> List<T>.update(predicate: Predicate<T>, update: (T) -> T): List<T> =
    this.map { if (predicate(it)) update(it) else it }

inline fun <T: Any, reified SUB: T> List<T>.filter(type: KClass<SUB>): List<SUB> =
        this.mapNotNull { if (it is SUB) it else null }

inline fun <T> List<T>.updateIndexed(predicate: (Int, T) -> Boolean, update: (Int, T) -> T): List<T> =
        this.mapIndexed { i, t -> if (predicate(i, t)) update(i, t) else t }

fun <T> List<T>.update(index: Int, value: T): List<T> = updateIndexed(
        { i, _ -> i == index },
        { _, _ -> value }
)

inline fun <T: Any> List<T>.updateNotNone(predicate: Predicate<T>, update: (T) -> Option<T>): List<T> =
    this.mapNotNull { if (predicate(it)) update(it).orNull() else it }


fun <T> Iterable<T>.allEqual(): Boolean {
    val iter = iterator()
    if (!iter.hasNext()) return true
    val element = iter.next()
    while (iter.hasNext()) {
        if (element != iter.next()) return false
    }
    return true
}

fun String.limit(n: Int) = if (length > n) slice(0 until n) else this

typealias MessagesOf<MSG> = Map<TechnicalPlayer, MSG>

typealias Messages = MessagesOf<JsonSerializable>

fun Messages.isCouldNotMatchGame(): Boolean = size == 1 && this[TechnicalPlayer.DUMMY] is TTTResponse.NoSuchGame

fun <T: JsonSerializable> noMessages(): MessagesOf<T> = emptyMap()

fun noSuchGame(unknownGameId: GameId): Messages =
        mapOf(TechnicalPlayer.DUMMY to TTTResponse.NoSuchGame(unknownGameId.asString()))