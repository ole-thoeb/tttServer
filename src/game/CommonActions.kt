package game

import arrow.core.Either

inline fun <L : Game.LobbyImpl, E> Game.Lobby<L>.updateEither(transform: L.() -> Either<E, L>): Either<E, Game.Lobby<L>> =
        map(transform).map(::update)

