package messages.responses

import game.*
import game.ttt.TTTInGame
import json.JsonSerializable
import json.JsonTypeDeserializer
import json.packageJson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

sealed class TTTInGameResponse : JsonSerializable {

    @Serializable
    data class State(val content: Content) : TTTInGameResponse() {
        val type = TYPE

        override fun toJson(): JsonElement {
            return packageJson.toJson(serializer(), this)
        }

        @Serializable
        data class Content(
                val gameId: String,
                val playerMe: PlayerMe,
                val opponent: Player,
                val board: List<Symbol>,
                val meTurn: Boolean,
                val status: SerializedStatus
        ) {
            fun toMsg(): State = State(this)
        }

        companion object : JsonTypeDeserializer<State, Content> {
            override val typeConstructor: (Content) -> State = TTTInGameResponse::State
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
            override val TYPE: String = "inGameState"

            fun forPlayer(inGame: Game.InGame<TTTInGame>, playerMe: game.Player.Human<TwoPlayerGame.Human>): State {
                val tttGame = inGame.impl
                val playerRef = playerMe.ref
                val opponent = tttGame.twoPlayerGame[!playerRef]

                val (meSymbol, opponentSymbol) = when (playerRef) {
                    TwoPlayerGame.PlayerRef.P1 -> Symbol.X to Symbol.O
                    TwoPlayerGame.PlayerRef.P2 -> Symbol.O to Symbol.X
                }

                val serialPlayerMe = PlayerMe(playerMe.technical.playerId.asString(), playerMe.name, meSymbol, playerRef)
                val serialOpponent = Player(opponent.name, opponentSymbol, !playerRef)

                return Content(tttGame.id.asString(),
                        serialPlayerMe,
                        serialOpponent,
                        tttGame.board.map(Symbol.Companion::fromCellState),
                        playerRef == tttGame.twoPlayerGame.turn,
                        SerializedStatus.fromRealStatus(tttGame.twoPlayerGame.status)
                ).toMsg()
            }
        }
    }

    @Serializable
    enum class Symbol {
        X, O, EMPTY;

        companion object {
            fun fromCellState(cellState: TTTInGame.CellState): Symbol = when (cellState) {
                TTTInGame.CellState.P1 -> X
                TTTInGame.CellState.P2 -> O
                TTTInGame.CellState.EMPTY -> EMPTY
            }
        }
    }

    @Serializable
    class SerializedStatus private constructor(
            val type: String,
            val winner: TwoPlayerGame.PlayerRef? = null,
            val winField1: Int? = null,
            val winField2: Int? = null,
            val winField3: Int? = null
    ) {
        companion object {
            val OnGoing = SerializedStatus("OnGoing")
            val Draw = SerializedStatus("Draw")
            fun Win(playerRef: TwoPlayerGame.PlayerRef, winField1: Int, winField2: Int, winField3: Int) =
                    SerializedStatus("Win", playerRef, winField1, winField2, winField3)

            fun fromRealStatus(status: TwoPlayerGame.Status) = when (status) {
                is TwoPlayerGame.Status.Win -> Win(status.winner, status.winField1, status.winField2, status.winField3)
                TwoPlayerGame.Status.Draw -> Draw
                TwoPlayerGame.Status.OnGoing -> OnGoing
            }
        }
    }

    @Serializable
    data class Player(val name: String, val symbol: Symbol, val playerRef: TwoPlayerGame.PlayerRef)

    @Serializable
    data class PlayerMe(val id: String, val name: String, val symbol: Symbol, val playerRef: TwoPlayerGame.PlayerRef)
}