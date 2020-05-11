package game

import GameId
import Messages
import SessionId
import java.util.concurrent.ConcurrentHashMap

data class RematchManager<L: LobbyImplWithPlayer<*, *>, G: InGameImplWithPlayer<*, *>>(
        val gameRegistry: SynchronizedGameRegistry<L, G>,
        val gameServer: GameServer<L, G>,
        private val rematchMap: ConcurrentHashMap<GameId, GameId> = ConcurrentHashMap()
) {

    suspend fun rematchIdOfGame(gameId: GameId): GameId {
        suspend fun getRematchId(): GameId {
            val rematchId = rematchMap[gameId]
            if (rematchId != null)
                return rematchId

            val rematch = gameServer.newGame()
            rematchMap[gameId] = rematch.id
            return rematch.id
        }

        return gameRegistry.trySynchronize(gameId, { getRematchId() }, { getRematchId() })
    }

    suspend fun rematch(
            sessionId: SessionId,
            oldGameId: GameId,
            addPlayer: suspend (GameId, String?) -> Messages
    ): GameServer.DirectResponse {
        val rematchId = rematchIdOfGame(oldGameId)
        val oldPlayerName: String? = gameRegistry.withGame(oldGameId) { game ->
            when (game) {
                null -> null
                is Game.Lobby -> game.humanPlayers[sessionId]?.name
                is Game.InGame -> game.humanPlayers[sessionId]?.name
            }
        }
        return messagesToDircect(sessionId, rematchId, addPlayer(rematchId, oldPlayerName))
    }
}