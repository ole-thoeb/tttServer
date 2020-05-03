package game

import GameId
import Messages
import MessagesOf
import arrow.core.Tuple2
import json.JsonSerializable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import noSuchGame
import java.util.concurrent.ConcurrentHashMap

interface SynchronizedGameRegistry<GAME : Game> {

    suspend fun <MSG : JsonSerializable> updateGame(gameId: GameId, update: suspend (GAME) -> Tuple2<GAME, MessagesOf<MSG>>): Messages
    suspend fun add(game: GAME): GAME
    suspend fun <R> trySynchronize(gameId: GameId, failure: suspend () -> R, success: suspend (GAME) -> R): R

    suspend fun removeUnlocked(gameId: GameId): GAME?
    suspend fun updateUnlocked(game: GAME): GAME

    suspend fun GameServer.AsyncActionContext.asyncUpdateGame(gameId: GameId, update: suspend (GAME) -> Tuple2<GAME, Messages>)
}

suspend fun <GAME : Game, R> SynchronizedGameRegistry<GAME>.withGame(gameId: GameId, action: suspend (GAME?) -> R): R {
    return trySynchronize(gameId, { action(null) }, action)
}

data class LockingSynchronizedGameRegistry<GAME : Game>(
        private val games: ConcurrentHashMap<GameId, GAME> = ConcurrentHashMap(),
        private val locks: ConcurrentHashMap<GameId, Mutex> = ConcurrentHashMap()
) : SynchronizedGameRegistry<GAME> {

    override suspend fun <MSG : JsonSerializable> updateGame(gameId: GameId, update: suspend (GAME) -> Tuple2<GAME, MessagesOf<MSG>>): Messages {
        return trySynchronize(gameId, { noSuchGame(gameId) }) { game ->
            val (updatedGame, msgs) = update(game)
            games[gameId] = updatedGame
            msgs
        }
    }

    override suspend fun add(game: GAME): GAME = updateUnlocked(game)

    override suspend fun <R> trySynchronize(gameId: GameId, failure: suspend () -> R, success: suspend (GAME) -> R): R {
        val mutex = locks[gameId] ?: return failure()
        mutex.withLock {
            val game = games[gameId] ?: return failure()
            return success(game)
        }
    }

    override suspend fun removeUnlocked(gameId: GameId): GAME? {
        locks.remove(gameId)
        return games.remove(gameId)
    }

    override suspend fun updateUnlocked(game: GAME): GAME {
        val lock = locks[game.id] ?: Mutex()
        locks[game.id] = lock
        games[game.id] = game
        return game
    }

    override suspend fun GameServer.AsyncActionContext.asyncUpdateGame(gameId: GameId, update: suspend (GAME) -> Tuple2<GAME, Messages>) {
        trySynchronize(gameId, { null }) { game ->
            val (updatedGame, msgs) = update(game)
            games[gameId] = updatedGame
            msgs
        }?.let { msgs ->
            messageChanel.send(msgs)
        }
    }
}
