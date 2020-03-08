import arrow.Kind
import arrow.core.*
import arrow.core.extensions.listk.traverse.traverse
import arrow.optics.Lens
import arrow.optics.PLens
import arrow.optics.Setter
import arrow.optics.Traversal
import arrow.optics.extensions.listk.filterIndex.filterIndex
import arrow.optics.extensions.mapk.at.at
import arrow.optics.extensions.traversal
import arrow.optics.typeclasses.FilterIndex
import arrow.typeclasses.Applicative
import io.ktor.http.cio.websocket.WebSocketSession

sealed class TTTGame {
    abstract val id: GameId
    abstract val technicalPlayers: ListK<TechnicalPlayer>
    
    data class Lobby(
            override val id: GameId,
            val players: ListK<Player> = ListK.empty()
    ) : TTTGame() {
        
        private val minPlayer = 2
        private val maxPlayer = 2
    
        override val technicalPlayers: ListK<TechnicalPlayer>
            get() = players.map(Player::technical)
        
        fun addPlayer(player: Player): Either<LobbyError.Full, Lobby> {
            return if (players.size < maxPlayer) {
                Either.right(copy(players = (players + player).k()))
            } else {
                Either.left(LobbyError.Full(player.technical, maxPlayer))
            }
        }

        data class Player(val name: String, val isReady: Boolean, val technical: TechnicalPlayer) {
            companion object {
                val technical: Lens<Player, TechnicalPlayer> = Lens(
                    get = { it.technical },
                    set = { player, technical -> player.copy(technical = technical)}
                )
            }
        }

        companion object {
            fun player(predicate: Predicate<Player>): Setter<Lobby, Player> = Setter { lobby, playerUpdate ->
                lobby.copy(players = lobby.players.update(predicate, playerUpdate).k())
            }

            fun technical(predicate: Predicate<Player>): Setter<Lobby, TechnicalPlayer> = player(predicate) + Player.technical
        }
    }
    
    class InGame(override val id: GameId, val players: ListK<TechnicalPlayer>) : TTTGame() {
        override val technicalPlayers: ListK<TechnicalPlayer>
            get() = players
    }
}

fun TTTGame.Lobby.addPlayer(technicalPlayer: TechnicalPlayer): Either<LobbyError.Full, TTTGame.Lobby> {
    return addPlayer(TTTGame.Lobby.Player("Player ${players.size + 1}", false, technicalPlayer))
}

data class TechnicalPlayer(val playerId: PlayerId, val sessionId: SessionId, val sockets: ListK<WebSocketSession>) {

    fun addSocket(webSocket: WebSocketSession): TechnicalPlayer = copy(sockets = (sockets + webSocket).k())
    fun removeSocket(webSocket: WebSocketSession): TechnicalPlayer = copy(sockets = (sockets - webSocket).k())

    companion object {
        val DUMMY = TechnicalPlayer(PlayerId("DUMMY_PLAYER"), SessionId("DUMMY_SESSION"), ListK.empty())
    }
}