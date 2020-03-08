import arrow.Kind
import arrow.core.ListK
import arrow.core.Option
import arrow.core.Predicate
import arrow.core.k
import arrow.optics.Lens
import arrow.typeclasses.ApplicativeError
import arrow.typeclasses.MonadError
import json.JsonSerializable
import messages.responses.TTTResponse

inline fun <F, A, E> MonadError<F, E>.tryCatch(fe: (Throwable) -> E, f: () -> A): Kind<F, A> = try {
    just(f())
} catch (t : Throwable) {
    raiseError(fe(t))
}

fun <F, T, E> T?.fromNull(ME: ApplicativeError<F, E>, fe: () -> E): Kind<F, T> =
        if (this == null) ME.raiseError(fe()) else ME.just(this)

inline fun <T> List<T>.update(predicate: Predicate<T>, update: (T) -> T): List<T> =
    this.map { if (predicate(it)) update(it) else it }

inline fun <T: Any> List<T>.updateNotNone(predicate: Predicate<T>, update: (T) -> Option<T>): List<T> =
    this.mapNotNull { if (predicate(it)) update(it).orNull() else it }

typealias MessagesOf<MSG> = Map<TechnicalPlayer, MSG>

typealias Messages = MessagesOf<JsonSerializable>


fun Messages.isCouldNotMatchGame(): Boolean = size == 1 && this[TechnicalPlayer.DUMMY] is TTTResponse.NoSuchGame