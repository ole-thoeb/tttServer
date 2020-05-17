package messages.responses

import game.*
import game.misery.MiseryInGame
import json.JsonSerializable
import json.JsonTypeDeserializer
import json.packageJson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


sealed class MiseryInGameResponse : JsonSerializable {

    @Serializable
    data class State(val content: Content) : MiseryInGameResponse() {
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
            override val typeConstructor: (Content) -> State = MiseryInGameResponse::State
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
            override val TYPE: String = "miseryInGameState"

            fun forPlayer(inGame: Game.InGame<MiseryInGame>, playerMe: game.Player.Human<TwoPlayerGame.Human>): State {
                val tttGame = inGame.impl
                val playerRef = playerMe.ref
                val opponent = tttGame.twoPlayerGame[!playerRef]

                val serialPlayerMe = PlayerMe(playerMe.technical.playerId.asString(), playerMe.name, playerRef)
                val serialOpponent = Player(opponent.name, !playerRef)

                return Content(tttGame.id.asString(),
                        serialPlayerMe,
                        serialOpponent,
                        tttGame.board.map(Symbol.Companion::fromCellState),
                        playerRef == tttGame.twoPlayerGame.turn,
                        TTTInGameResponse.SerializedStatus.fromRealStatus(tttGame.twoPlayerGame.status)
                ).toMsg()
            }
        }
    }

    @Serializable
    enum class Symbol {
        X, EMPTY;

        companion object {
            fun fromCellState(state: MiseryInGame.CellState): Symbol = when (state) {
                MiseryInGame.CellState.X -> X
                MiseryInGame.CellState.EMPTY -> EMPTY
            }
        }
    }

    @Serializable
    data class Player(val name: String, val playerRef: TwoPlayerGame.PlayerRef)

    @Serializable
    data class PlayerMe(val id: String, val name: String, val playerRef: TwoPlayerGame.PlayerRef)
}