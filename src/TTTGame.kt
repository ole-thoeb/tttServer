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
                        set = { player, technical -> player.copy(technical = technical) }
                )
            }
        }

        companion object {
            fun players(): Lens<Lobby, ListK<Player>> = Lens(
                    get = { it.players },
                    set = { lobby, players -> lobby.copy(players = players) }
            )

            fun player(predicate: Predicate<Player>): Setter<Lobby, Player> = Setter { lobby, playerUpdate ->
                lobby.copy(players = lobby.players.update(predicate, playerUpdate).k())
            }

            fun technical(predicate: Predicate<Player>): Setter<Lobby, TechnicalPlayer> = player(predicate) + Player.technical
        }
    }

    data class InGame(
            override val id: GameId,
            val player1: Player,
            val player2: Player,
            val board: ListK<CellState>,
            val turn: PlayerRef
    ) : TTTGame() {

        override val technicalPlayers: ListK<TechnicalPlayer>
            get() = listOf(player1.technical, player2.technical).k()

        val playersWithRef: ListK<Tuple2<Player, PlayerRef>>
            get() = listOf(player1 toT PlayerRef.P1, player2 toT PlayerRef.P2).k()

        enum class PlayerRef(val cellState: CellState) {
            P1(CellState.P1), P2(CellState.P2);

            operator fun not(): PlayerRef = when (this) {
                P1 -> P2
                P2 -> P1
            }
        }

        enum class CellState { P1, P2, EMPTY }

        fun setPiece(x: Int, y: Int, playerId: PlayerId): Either<InGameError, InGame> {
            val playerRef = playerId.playerRef ?: return Left(InGameError.WrongTurn(null, turn))
            if (playerRef != turn) return Left(InGameError.WrongTurn(playerRef, turn))

            val index = transformIndex(x, y)
            if (index !in board.indices || board[index] != CellState.EMPTY) return Left(InGameError.IllegalPlace(x, y))

            return Right(copy(
                    board = board.update(index, playerRef.cellState).k(),
                    turn = !turn
            ))
        }

        operator fun get(ref: PlayerRef): Player = when (ref) {
            PlayerRef.P1 -> player1
            PlayerRef.P2 -> player2
        }

        private val PlayerId.playerRef: PlayerRef?
            get() = when (this) {
                player1.technical.playerId -> PlayerRef.P1
                player2.technical.playerId -> PlayerRef.P2
                else -> null
            }

        private fun transformIndex(x: Int, y: Int): Int = x + y * 3

        data class Player(val name: String, val color: String, val technical: TechnicalPlayer) {
            companion object {
                val technical: Lens<Player, TechnicalPlayer> = Lens(
                        get = { it.technical },
                        set = { player, technical -> player.copy(technical = technical) }
                )
            }
        }

        companion object {
            fun players(): Lens<InGame, Tuple2<Player, Player>> = Lens(
                    get = { inGame -> inGame.player1 toT inGame.player2 },
                    set = { inGame, (player1, player2) -> inGame.copy(player1 = player1, player2 = player2) }
            )

            fun player(predicate: Predicate<Player>): Setter<InGame, Player> = Setter { inGame, playerUpdate ->
                inGame.copy(
                        player1 = if (predicate(inGame.player1)) playerUpdate(inGame.player1) else inGame.player1,
                        player2 = if (predicate(inGame.player2)) playerUpdate(inGame.player2) else inGame.player2
                )
            }

            fun technical(predicate: Predicate<Player>): Setter<InGame, TechnicalPlayer> = player(predicate) + Player.technical
        }
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