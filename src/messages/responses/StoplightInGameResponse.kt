package messages.responses

import game.*
import game.stoplight.StoplightInGame
import json.JsonSerializable
import json.JsonTypeDeserializer
import json.packageJson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


sealed class StoplightInGameResponse : JsonSerializable {

    @Serializable
    data class State(val content: Content) : StoplightInGameResponse() {
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
                val status: TTTInGameResponse.SerializedStatus
        ) {
            fun toMsg(): State = State(this)
        }

        companion object : JsonTypeDeserializer<State, Content> {
            override val typeConstructor: (Content) -> State = StoplightInGameResponse::State
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
            override val TYPE: String = "stoplightInGameState"

            fun forPlayer(inGame: Game.InGame<StoplightInGame>, playerMe: game.Player.Human<TwoPlayerGame.Human>): State {
                val stoplightGame = inGame.impl
                val playerRef = playerMe.ref
                val opponent = stoplightGame.twoPlayerGame[!playerRef]

                val serialPlayerMe = PlayerMe(playerMe.technical.playerId.asString(), playerMe.name, playerRef)
                val serialOpponent = Player(opponent.name, !playerRef)

                return Content(stoplightGame.id.asString(),
                        serialPlayerMe,
                        serialOpponent,
                        stoplightGame.board.map(Symbol.Companion::fromCellState),
                        playerRef == stoplightGame.twoPlayerGame.turn,
                        TTTInGameResponse.SerializedStatus.fromRealStatus(stoplightGame.twoPlayerGame.status)
                ).toMsg()
            }
        }
    }

    @Serializable
    enum class Symbol {
        GREEN, YELLOW, RED, EMPTY;

        companion object {
            fun fromCellState(state: StoplightInGame.CellState): Symbol = when (state) {
                StoplightInGame.CellState.GREEN -> GREEN
                StoplightInGame.CellState.YELLOW -> YELLOW
                StoplightInGame.CellState.RED -> RED
                StoplightInGame.CellState.EMPTY -> EMPTY
            }
        }
    }

    @Serializable
    data class Player(val name: String, val playerRef: TwoPlayerGame.PlayerRef)

    @Serializable
    data class PlayerMe(val id: String, val name: String, val playerRef: TwoPlayerGame.PlayerRef)
}