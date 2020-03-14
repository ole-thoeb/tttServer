import arrow.core.*
import arrow.optics.Lens
import arrow.optics.Setter
import io.ktor.http.cio.websocket.WebSocketSession
import kotlinx.coroutines.Job

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
            fun players(): Lens<Lobby, ListK<Player>> = Lens(
                    get = { it.players },
                    set = { lobby, players -> lobby.copy(players =  players) }
            )
            
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

data class TechnicalPlayer(
        val playerId: PlayerId,
        val sessionId: SessionId,
        val sockets: ListK<WebSocketSession>,
        val jobs: MapK<String, Job>
) {

    fun addSocket(webSocket: WebSocketSession): TechnicalPlayer = copy(sockets = (sockets + webSocket).k())
    fun removeSocket(webSocket: WebSocketSession): TechnicalPlayer = copy(sockets = (sockets - webSocket).k())

    companion object {
        val DUMMY = TechnicalPlayer(PlayerId("DUMMY_PLAYER"), SessionId("DUMMY_SESSION"), ListK.empty(), emptyMap<String, Job>().k())
    }
}