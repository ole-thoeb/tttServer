package game

import GameId
import Messages
import MessagesOf
import arrow.core.Tuple2
import arrow.core.constant
import json.JsonSerializable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import noSuchGame
import java.util.concurrent.ConcurrentHashMap

interface SynchronizedGameRegistry<L : Game.LobbyImpl, G : Game.InGameImpl> {

    suspend fun <T> updateGame(gameId: GameId, default: T, update: suspend (Game<L, G>) -> Tuple2<Game<L, G>, T>): T
    suspend fun add(game: Game<L, G>): Game<L, G>
    suspend fun <R> trySynchronize(gameId: GameId, failure: suspend () -> R, success: suspend (Game<L, G>) -> R): R

    suspend fun removeUnlocked(gameId: GameId): Game<L, G>?
    suspend fun updateUnlocked(game: Game<L, G>): Game<L, G>

    suspend fun GameServer.AsyncActionContext.asyncUpdateGame(gameId: GameId, update: suspend (Game<L, G>) -> Tuple2<Game<L, G>, Messages>)
}

suspend fun <L : Game.LobbyImpl, G : Game.InGameImpl, R> SynchronizedGameRegistry<L, G>.withGame(gameId: GameId, action: suspend (Game<L, G>?) -> R): R {
    return trySynchronize(gameId, { action(null) }, action)
}

suspend fun <MSG: JsonSerializable, L : Game.LobbyImpl, G : Game.InGameImpl> SynchronizedGameRegistry<L, G>.updateGame(
        gameId: GameId,
        update: suspend (Game<L, G>) -> Tuple2<Game<L, G>, MessagesOf<MSG>>
): Messages = updateGame(gameId, noSuchGame(gameId), update)

data class LockingSynchronizedGameRegistry<L : Game.LobbyImpl, G : Game.InGameImpl>(
        private val games: ConcurrentHashMap<GameId, Game<L, G>> = ConcurrentHashMap(),
        private val locks: ConcurrentHashMap<GameId, Mutex> = ConcurrentHashMap()
) : SynchronizedGameRegistry<L, G> {

    override suspend fun <T> updateGame(gameId: GameId, default: T, update: suspend (Game<L, G>) -> Tuple2<Game<L, G>, T>): T {
        return trySynchronize(gameId, { default }) { game ->
            val (updatedGame, t) = update(game)
            games[gameId] = updatedGame
            t
        }
    }

    override suspend fun add(game: Game<L, G>): Game<L, G> = updateUnlocked(game)

    override suspend fun <R> trySynchronize(gameId: GameId, failure: suspend () -> R, success: suspend (Game<L, G>) -> R): R {
        val mutex = locks[gameId] ?: return failure()
        mutex.withLock {
            val game = games[gameId] ?: return failure()
            return success(game)
        }
    }

    override suspend fun removeUnlocked(gameId: GameId): Game<L, G>? {
        locks.remove(gameId)
        return games.remove(gameId)
    }

    override suspend fun updateUnlocked(game: Game<L, G>): Game<L, G> {
        val lock = locks[game.id] ?: Mutex()
        locks[game.id] = lock
        games[game.id] = game
        return game
    }

    override suspend fun GameServer.AsyncActionContext.asyncUpdateGame(gameId: GameId, update: suspend (Game<L, G>) -> Tuple2<Game<L, G>, Messages>) {
        trySynchronize(gameId, { null }) { game ->
            val (updatedGame, msgs) = update(game)
            games[gameId] = updatedGame
            msgs
        }?.let { msgs ->
            messageChannel.send(msgs)
        }
    }
}
