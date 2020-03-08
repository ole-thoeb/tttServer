import arrow.core.ListK
import arrow.core.toT
import json.JsonSerializable
import messages.responses.LobbyResponse

suspend fun TTTGameServer.addNewPlayer(sessionId: SessionId, gameId: GameId): Messages = updateGame(gameId) { game ->
    val newPlayer = TechnicalPlayer(PlayerId.create(), sessionId, ListK.empty())
    when (game) {
        is TTTGame.Lobby -> {
            game.addPlayer(newPlayer).fold(
                    { lobbyError ->
                        game toT mapOf(newPlayer to LobbyResponse.Full.fromError(lobbyError))
                    },
                    { updatedLobby ->
                        updatedLobby toT updatedLobby.players
                                .associate { it.technical to LobbyResponse.State.forPlayer(game, it) }
                    }
            )
        }
        is TTTGame.InGame -> {
            game toT mapOf(newPlayer to LobbyResponse.GameAlreadyStarted(game.id.asString()))
        }
    }
}
