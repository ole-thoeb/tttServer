package messages.responses

import TTTGame
import json.JsonSerializable
import json.JsonTypeDeserializer
import json.packageJson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

sealed class InGameResponse : JsonSerializable {

    @Serializable
    data class State(val content: Content) : InGameResponse() {
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
                val meTurn: Boolean
        ) {
            fun toMsg(): State = State(this)
        }

        companion object : JsonTypeDeserializer<State, Content> {
            override val typeConstructor: (Content) -> State = InGameResponse::State
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
            override val TYPE: String = "inGameState"

            fun forPlayer(inGame: TTTGame.InGame, playerRef: TTTGame.InGame.PlayerRef): State {
                val playerMe = inGame[playerRef]
                val opponent = inGame[!playerRef]

                val meSymbol = Symbol.fromCellState(playerRef.cellState)
                val opponentSymbol = !meSymbol

                val serialPlayerMe = PlayerMe(playerMe.technical.playerId.asString(), playerMe.name, playerMe.color, meSymbol)
                val serialOpponent = Player(opponent.name, opponent.color, opponentSymbol)

                return Content(inGame.id.asString(),
                        serialPlayerMe,
                        serialOpponent,
                        inGame.board.map(Symbol.Companion::fromCellState),
                        playerRef == inGame.turn
                ).toMsg()
            }
        }
    }

    @Serializable
    enum class Symbol {
        X, O, EMPTY;

        operator fun not() = when (this) {
            X -> O
            O -> X
            EMPTY -> EMPTY
        }

        companion object {
            fun fromCellState(cellState: TTTGame.InGame.CellState): Symbol = when (cellState) {
                TTTGame.InGame.CellState.P1 -> X
                TTTGame.InGame.CellState.P2 -> O
                TTTGame.InGame.CellState.EMPTY -> EMPTY
            }
        }
    }

    @Serializable
    data class Player(val name: String, val color: String, val symbol: Symbol)

    @Serializable
    data class PlayerMe(val id: String, val name: String, val color: String, val symbol: Symbol)
}

