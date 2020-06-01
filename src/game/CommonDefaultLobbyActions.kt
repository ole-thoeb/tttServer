package game

import SessionId
import arrow.core.*
import kotlinx.coroutines.Job


fun <G : InGameImplWithPlayer<*, *>> LobbyDefaultLobby<G>.updateTechnical(
        predicate: Predicate<Player.Human<*>>,
        update: (TechnicalPlayer) -> TechnicalPlayer
): LobbyDefaultLobby<G> = update {
    DefaultLobby.technical<G>(predicate).modify(this, update)
}

fun <G : InGameImplWithPlayer<*, *>> LobbyDefaultLobby<G>.addNewPlayer(
        player: DefaultLobbyHuman
): Either<LobbyError, LobbyDefaultLobby<G>> = updateEither { addPlayer(player) }

fun <G : InGameImplWithPlayer<*, *>> LobbyDefaultLobby<G>.addNewPlayer(
        sessionId: SessionId
): Either<LobbyError, LobbyDefaultLobby<G>> {
    val technical = TechnicalPlayer(PlayerId.create(), sessionId, ListK.empty(), emptyMap<String, Job>().k())

    val player = Player.Human(DefaultLobby.Human("Player ${players.size + 1}", false, technical))
    return addNewPlayer(player, this)
}

private fun <G : InGameImplWithPlayer<*, *>> addNewPlayer(
        player: DefaultLobbyHuman,
        lobby: LobbyDefaultLobby<G>
): Either<LobbyError, LobbyDefaultLobby<G>> {
    return lobby.updateEither {
        players[player.playerId].toOption().fold(
                {
                    addPlayer(player)
                },
                { Right(this) }
        )
    }
}

fun <G: InGameImplWithPlayer<*, *>> DefaultLobby<G>.toTwoPlayerGame(): TwoPlayerGame {
    val (p1, p2) = players.shuffled()
    val toInGamePlayer = { player: DefaultLobbyPlayer, ref: TwoPlayerGame.PlayerRef ->
        val (defName, color) = when (ref) {
            TwoPlayerGame.PlayerRef.P1 -> "Player 1" to "#FF0000"
            TwoPlayerGame.PlayerRef.P2 -> "Player 2" to "#00FF00"
        }
        when (player) {
            is Player.Human -> player.map {
                Player.Human(TwoPlayerGame.Human(name.ifBlank { defName }, ref, technical))
            }
            is Player.Bot -> player.map {
                Player.Bot(TwoPlayerGame.Bot(name, PlayerId.create(), player.impl.difficulty, ref))
            }
        }
    }
    return TwoPlayerGame(id,
            toInGamePlayer(p1, TwoPlayerGame.PlayerRef.P1),
            toInGamePlayer(p2, TwoPlayerGame.PlayerRef.P2),
            TwoPlayerGame.PlayerRef.P1,
            TwoPlayerGame.Status.OnGoing
    )
}