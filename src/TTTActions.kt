import arrow.core.ListK
import arrow.core.firstOrNone
import arrow.core.k
import arrow.core.toT
import json.JsonSerializable
import kotlinx.coroutines.Job
import messages.requests.LobbyRequest
import messages.responses.LobbyResponse

suspend fun TTTGameServer.addNewPlayer(sessionId: SessionId, gameId: GameId): Messages = updateGame(gameId) { game ->
    val newPlayer = TechnicalPlayer(PlayerId.create(), sessionId, ListK.empty(), emptyMap<String, Job>().k())
    when (game) {
        is TTTGame.Lobby -> {
            game.players.firstOrNone { it.technical.sessionId == sessionId }.fold(
                { game.addPlayer(newPlayer).fold(
                    { lobbyError ->
                        game toT mapOf(newPlayer to LobbyResponse.Full.fromError(lobbyError))
                    },
                    { updatedLobby ->
                        updatedLobby toT lobbyStateMsgs(updatedLobby)
                    }
                ) },
                {  game toT lobbyStateMsgs(game) }
            )
        }
        is TTTGame.InGame -> {
            game toT mapOf(newPlayer to LobbyResponse.GameAlreadyStarted(game.id.asString()))
        }
    }
}

suspend fun TTTGameServer.handleLobbyRequest(lobbyRequest: LobbyRequest): Messages = updateGame(lobbyRequest.gameId) { game ->
    if (game !is TTTGame.Lobby) return@updateGame TODO()

    val updatedGame = TTTGame.Lobby.player { it.technical.playerId == lobbyRequest.playerId }.modify(game) {
        when (lobbyRequest) {
            is LobbyRequest.Ready -> {
                it.copy(isReady = lobbyRequest.content.isReady)
            }
            is LobbyRequest.Name -> {
                it.copy(name = lobbyRequest.content.name)
            }
        }
    }
    updatedGame toT lobbyStateMsgs(updatedGame)
}

fun lobbyStateMsgs(lobby: TTTGame.Lobby): MessagesOf<LobbyResponse.State> = lobby.players.associate {
    it.technical to LobbyResponse.State.forPlayer(lobby, it)
}