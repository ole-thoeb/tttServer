import arrow.core.*
import arrow.optics.Lens
import arrow.optics.Setter

sealed class TTTGame : Game {
    abstract override val id: GameId
    abstract operator fun get(sessionId: SessionId): TechnicalPlayer?

    data class Lobby(
            override val id: GameId,
            val players: ListK<Player> = ListK.empty()
    ) : TTTGame() {

        private val minPlayer = 2
        private val maxPlayer = 2

        fun addPlayer(player: Player): Either<LobbyError.Full, Lobby> {
            return if (players.size < maxPlayer) {
                Either.right(copy(players = (players + player).k()))
            } else {
                Either.left(LobbyError.Full(maxPlayer))
            }
        }

        override fun get(sessionId: SessionId): TechnicalPlayer? =
                players.filter(Player.Human::class)
                        .firstOrNull { player -> player.technical.sessionId == sessionId }
                        ?.technical

        sealed class Player {
            abstract val name: String
            abstract val isReady: Boolean

            data class Human(override val name: String, override val isReady: Boolean, val technical: TechnicalPlayer) : Player() {
                companion object {
                    val technical: Lens<Human, TechnicalPlayer> = Lens(
                            get = { it.technical },
                            set = { player, technical -> player.copy(technical = technical) }
                    )
                }
            }

            data class Bot(override val name: String, val playerId: PlayerId) : Player() {
                override val isReady: Boolean = true
            }
        }

        companion object {
            fun players(): Lens<Lobby, ListK<Player>> = Lens(
                    get = { it.players },
                    set = { lobby, players -> lobby.copy(players = players) }
            )

            fun player(predicate: Predicate<Player.Human>): Setter<Lobby, Player.Human> = Setter { lobby, playerUpdate ->
                lobby.copy(players = lobby.players.map {
                    if (it is Player.Human && predicate(it)) playerUpdate(it) else it
                }.k())
            }

            fun technical(predicate: Predicate<Player.Human>): Setter<Lobby, TechnicalPlayer> = player(predicate) + Player.Human.technical
        }
    }

    data class InGame(
            override val id: GameId,
            val player1: Player,
            val player2: Player,
            val board: ListK<CellState>,
            val turn: PlayerRef,
            val status: Status
    ) : TTTGame() {

        val players: ListK<Player>
            get() = listOf(player1, player2).k()

        override fun get(sessionId: SessionId): TechnicalPlayer? = when {
            player1 is Player.Human && player1.technical.sessionId == sessionId -> player1.technical
            player2 is Player.Human && player2.technical.sessionId == sessionId -> player2.technical
            else -> null
        }

        enum class PlayerRef(val cellState: CellState) {
            P1(CellState.P1), P2(CellState.P2);

            operator fun not(): PlayerRef = when (this) {
                P1 -> P2
                P2 -> P1
            }
        }

        enum class CellState { P1, P2, EMPTY }

        fun setPiece(index: Int, playerId: PlayerId): Either<InGameError, InGame> {
            if (status != Status.OnGoing) return Left(InGameError.IllegalStatus(status))
            val playerRef = playerId.playerRef ?: return Left(InGameError.WrongTurn(null, turn))
            if (playerRef != turn) return Left(InGameError.WrongTurn(playerRef, turn))

            if (index !in board.indices || board[index] != CellState.EMPTY) return Left(InGameError.IllegalPlace(index))
            val updatedBoard = board.update(index, playerRef.cellState).k()

            return Right(copy(
                    board = updatedBoard,
                    turn = !turn,
                    status = checkStatus(updatedBoard)
            ))
        }

        operator fun get(ref: PlayerRef): Player = when (ref) {
            PlayerRef.P1 -> player1
            PlayerRef.P2 -> player2
        }

        private val PlayerId.playerRef: PlayerRef?
            get() = when (this) {
                player1.playerId -> PlayerRef.P1
                player2.playerId -> PlayerRef.P2
                else -> null
            }

        private fun checkStatus(board: ListK<CellState>): Status {
            if (board.size != 9) return Status.OnGoing
            val indexes = listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), listOf(0, 4, 8), listOf(2, 4, 6))
            val winningIndices = indexes.firstOrNone {
                board.slice(it).allEqual() && board[it.first()] != CellState.EMPTY
            }
            return winningIndices.flatMap { (i1, i2, i3) ->
                val winningPlayerRef = when (board[i1]) {
                    CellState.P1 -> PlayerRef.P1
                    CellState.P2 -> PlayerRef.P2
                    CellState.EMPTY -> return@flatMap Option.empty<Status>()
                }
                Option(Status.Win(winningPlayerRef, i1, i2, i3))
            }.getOrElse {
                if (board.all { it != CellState.EMPTY }) Status.Draw else Status.OnGoing
            }
        }

        sealed class Player {
            abstract val name: String
            abstract val playerId: PlayerId
            abstract val ref: PlayerRef
            abstract val color: String

            data class Human(
                    override val name: String,
                    override val color: String,
                    override val ref: PlayerRef,
                    val technical: TechnicalPlayer
            ) : Player() {

                override val playerId: PlayerId
                    get() = technical.playerId

                companion object {
                    val technical: Lens<Human, TechnicalPlayer> = Lens(
                            get = { it.technical },
                            set = { player, technical -> player.copy(technical = technical) }
                    )
                }
            }

            data class Bot(
                    override val name: String,
                    override val playerId: PlayerId,
                    override val color: String,
                    override val ref: PlayerRef
            ) : Player()
        }

        sealed class Status {
            data class Win(val winner: PlayerRef, val winField1: Int, val winField2: Int, val winField3: Int) : Status()

            object Draw : Status()

            object OnGoing : Status()
        }

        companion object {
            fun players(): Lens<InGame, Tuple2<Player, Player>> = Lens(
                    get = { inGame -> inGame.player1 toT inGame.player2 },
                    set = { inGame, (player1, player2) -> inGame.copy(player1 = player1, player2 = player2) }
            )

            fun humanPlayer(predicate: Predicate<Player.Human>): Setter<InGame, Player.Human> = Setter { inGame, playerUpdate ->
                inGame.copy(
                        player1 = if (inGame.player1 is Player.Human && predicate(inGame.player1))
                            playerUpdate(inGame.player1)
                        else
                            inGame.player1,
                        player2 = if (inGame.player2 is Player.Human && predicate(inGame.player2))
                            playerUpdate(inGame.player2)
                        else
                            inGame.player2
                )
            }

            fun technical(predicate: Predicate<Player.Human>): Setter<InGame, TechnicalPlayer> = humanPlayer(predicate) + Player.Human.technical
        }
    }
}