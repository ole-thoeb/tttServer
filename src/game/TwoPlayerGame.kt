package game

import GameId
import PlayerId
import arrow.core.*
import arrow.optics.Lens
import arrow.optics.PLens
import arrow.optics.PSetter
import arrow.optics.Setter


typealias TwoPlayerGamePlayer = Player<TwoPlayerGame.Human, TwoPlayerGame.Bot>

data class TwoPlayerGame(
        override val id: GameId,
        val player1: TwoPlayerGamePlayer,
        val player2: TwoPlayerGamePlayer,
        val turn: PlayerRef,
        val status: Status
) : InGameImplWithPlayer<TwoPlayerGame.Human, TwoPlayerGame.Bot> {
    override val players: ListK<TwoPlayerGamePlayer>
        get() = listOf(player1, player2).k()

//    fun modifyTechnical(predicate: Predicate<Human>, modification: (TechnicalPlayer) -> TechnicalPlayer): TwoPlayerGame {
//        return technical(predicate).modify(this, modification)
//    }

    fun getPlayerRef(playerId: PlayerId): PlayerRef? = when (playerId) {
        player1.playerId -> PlayerRef.P1
        player2.playerId -> PlayerRef.P2
        else -> null
    }

    operator fun get(ref: PlayerRef): TwoPlayerGamePlayer = when (ref) {
        PlayerRef.P1 -> player1
        PlayerRef.P2 -> player2
    }

    fun nextTurn(): TwoPlayerGame = copy(turn = !turn)

    enum class PlayerRef {
        P1, P2;

        operator fun not(): PlayerRef = when (this) {
            P1 -> P2
            P2 -> P1
        }
    }

    data class Human(
            override val name: String,
            val ref: PlayerRef,
            override val technical: TechnicalPlayer
    ) : Player.HumanImpl {

        companion object {
            val technical: Lens<Human, TechnicalPlayer> = PLens(
                    get = { it.technical },
                    set = { player, technical -> player.copy(technical = technical) }
            )
        }
    }

    data class Bot(
            override val name: String,
            override val playerId: PlayerId,
            val ref: PlayerRef
    ) : Player.BotImpl

    sealed class Status {
        data class Win(val winner: PlayerRef, val winField1: Int, val winField2: Int, val winField3: Int) : Status()

        object Draw : Status()

        object OnGoing : Status()
    }

    companion object {

        fun players(): Lens<TwoPlayerGame, Tuple2<TwoPlayerGamePlayer, TwoPlayerGamePlayer>> = Lens(
                get = { game -> game.player1 toT game.player2 },
                set = { game, (player1, player2) -> game.copy(player1 = player1, player2 = player2) }
        )

        fun humanPlayer(predicate: Predicate<Player.Human<Human>>): Setter<TwoPlayerGame, Player.Human<Human>> =
                PSetter { game, playerUpdate ->
                    game.copy(
                            player1 = if (game.player1 is Player.Human && predicate(game.player1))
                                playerUpdate(game.player1)
                            else
                                game.player1,
                            player2 = if (game.player2 is Player.Human && predicate(game.player2))
                                playerUpdate(game.player2)
                            else
                                game.player2
                    )
                }

        fun technical(predicate: Predicate<Player.Human<Human>>): Setter<TwoPlayerGame, TechnicalPlayer> =
                humanPlayer(predicate) + Player.Human.impl<Human>() + Human.technical
    }

}

sealed class TwoPlayerGameError {
    data class IllegalPlace(val illegalIndex: Int) : TwoPlayerGameError()
    data class WrongTurn(val tried: TwoPlayerGame.PlayerRef?, val actual: TwoPlayerGame.PlayerRef) : TwoPlayerGameError()
    data class IllegalStatus(val status: TwoPlayerGame.Status) : TwoPlayerGameError()
}
//
//interface TwoPlayerInGameImpl<S> : InGameImplWithPlayer<TwoPlayerGame.Human, TwoPlayerGame.Bot> {
//    val twoPlayerGame: TwoPlayerGame
//
//    val board: ListK<S>
//    val emptySymbol: S
//
//    override val id: GameId
//        get() = twoPlayerGame.id
//
//    override val players: ListK<Player<TwoPlayerGame.Human, TwoPlayerGame.Bot>>
//        get() = twoPlayerGame.players
//
//    fun serializeSymbol(symbol: S): String
//
//    fun updateTwoPlayerGame(twoPlayerGame: TwoPlayerGame): TwoPlayerInGameImpl<S>
//
//    companion object {
//        @Suppress("UNCHECKED_CAST")
//        fun <S, G : TwoPlayerInGameImpl<S>> twoPlayerGame(): Lens<G, TwoPlayerGame> = Lens(
//                get = { it.twoPlayerGame },
//                set = { game, twoGame -> game.updateTwoPlayerGame(twoGame) as G }
//        )
//    }
//}