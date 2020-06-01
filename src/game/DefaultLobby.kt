package game

import GameId
import PlayerId
import arrow.core.Either
import arrow.core.ListK
import arrow.core.Predicate
import arrow.core.k
import arrow.optics.Lens
import arrow.optics.PLens
import arrow.optics.PSetter
import arrow.optics.Setter
import kotlinx.serialization.Serializable
import messages.responses.DefaultLobbyResponse

typealias DefaultLobbyPlayer = Player<DefaultLobby.Human, DefaultLobby.Bot>
typealias DefaultLobbyHuman = Player.Human<DefaultLobby.Human>
typealias DefaultLobbyBot = Player.Bot<DefaultLobby.Bot>
typealias GameDefaultLobby<G> = Game<DefaultLobby<G>, G>
typealias LobbyDefaultLobby<G> = Game.Lobby<DefaultLobby<G>>

data class DefaultLobby<G : InGameImplWithPlayer<*, *>>(
        override val id: GameId,
        val maxPlayer: Int,
        override val players: ListK<DefaultLobbyPlayer> = ListK.empty(),
        val startGame: GameServer<DefaultLobby<*>, G>.(DefaultLobby<*>) -> Game.InGame<G>
) : LobbyImplWithPlayer<DefaultLobby.Human, DefaultLobby.Bot> {

    fun addPlayer(player: DefaultLobbyPlayer): Either<LobbyError.Full, DefaultLobby<G>> {
        return if (players.size < maxPlayer) {
            Either.right(copy(players = (players + player).k()))
        } else {
            Either.left(LobbyError.Full(maxPlayer))
        }
    }

    data class Human(
            override val name: String,
            val isReady: Boolean,
            override val technical: TechnicalPlayer
    ) : Player.HumanImpl {

        companion object {
            val technical: Lens<Human, TechnicalPlayer> = PLens(
                    get = { it.technical },
                    set = { player, technical -> player.copy(technical = technical) }
            )
        }
    }

    @Serializable
    enum class Difficulty { CHILDS_PLAY, CHALLENGE, NIGHTMARE }

    data class Bot(
            override val name: String,
            override val playerId: PlayerId,
            val difficulty: Difficulty = Difficulty.NIGHTMARE
    ) : Player.BotImpl

    companion object {
        fun <G : InGameImplWithPlayer<*, *>> players(): Lens<DefaultLobby<G>, ListK<DefaultLobbyPlayer>> = PLens(
                get = { it.players },
                set = { lobby, players -> lobby.copy(players = players) }
        )

        fun <G : InGameImplWithPlayer<*, *>> player(predicate: Predicate<DefaultLobbyHuman>): Setter<DefaultLobby<G>, Human> = PSetter { lobby, playerUpdate ->
            lobby.copy(players = lobby.players.map {
                if (it is Player.Human && predicate(it)) it.update(playerUpdate) else it
            }.k())
        }

        fun <G : InGameImplWithPlayer<*, *>> bot(predicate: Predicate<DefaultLobbyBot>): Setter<DefaultLobby<G>, Bot> = PSetter { lobby, playerUpdate ->
            lobby.copy(players = lobby.players.map {
                if (it is Player.Bot && predicate(it)) it.update(playerUpdate) else it
            }.k())
        }

        fun <G : InGameImplWithPlayer<*, *>> technical(predicate: Predicate<DefaultLobbyHuman>): Setter<DefaultLobby<G>, TechnicalPlayer> =
                player<G>(predicate) + Human.technical
    }
}

sealed class LobbyError {
    data class Full(val maxPlayers: Int) : LobbyError()
}

val DefaultLobbyPlayer.isReady: Boolean
    get() = when (this) {
        is Player.Human -> impl.isReady
        is Player.Bot -> true
    }
