package messages.responses

import game.*
import json.JsonSerializable
import json.JsonTypeDeserializer
import json.packageJson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

sealed class DefaultLobbyResponse : JsonSerializable {

    @Serializable
    data class State(val content: Content) : DefaultLobbyResponse() {
        val type = TYPE

        override fun toJson(): JsonElement {
            return packageJson.encodeToJsonElement(serializer(), this)
        }

        @Serializable
        data class Content(val gameId: String, val players: List<Player>, val playerMe: PlayerMe) {
            fun toMsg(): State = State(this)
        }

        companion object : JsonTypeDeserializer<State, Content> {
            override val typeConstructor: (Content) -> State = DefaultLobbyResponse::State
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
            override val TYPE: String = "lobbyState"

            fun <G : InGameImplWithPlayer<*, *>> forPlayer(
                    lobby: LobbyDefaultLobby<G>,
                    player: DefaultLobbyHuman
            ): State = Content(lobby.id.asString(),
                    lobby.players.filter { it.playerId != player.playerId }.map(Player.Companion::create),
                    PlayerMe(player.technical.playerId.asString(), player.name, player.isReady)
            ).toMsg()
        }
    }

    @Serializable
    data class Full(val content: Content) : DefaultLobbyResponse() {
        val type = TYPE

        override fun toJson(): JsonElement {
            return packageJson.encodeToJsonElement(serializer(), this)
        }

        @Serializable
        data class Content(val maxPlayers: Int) {
            fun toMsg(): Full = Full(this)
        }

        companion object : JsonTypeDeserializer<Full, Content> {
            override val typeConstructor: (Content) -> Full = DefaultLobbyResponse::Full
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
            override val TYPE: String = "lobbyFull"

            fun fromError(error: LobbyError.Full): Full = Content(error.maxPlayers).toMsg()
        }
    }

    @Serializable
    data class GameAlreadyStarted(val content: Content) : DefaultLobbyResponse() {
        val type = TYPE

        constructor(gameId: String) : this(Content(gameId))

        override fun toJson(): JsonElement {
            return packageJson.encodeToJsonElement(serializer(), this)
        }

        @Serializable
        data class Content(val gameId: String)

        companion object : JsonTypeDeserializer<GameAlreadyStarted, Content> {
            override val typeConstructor: (Content) -> GameAlreadyStarted = DefaultLobbyResponse::GameAlreadyStarted
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
            override val TYPE: String = "gameAlreadyStarted"
        }
    }

    @Serializable
    sealed class Player {
        abstract val name: String
        abstract val isReady: Boolean

        @Serializable
        data class Human(override val name: String, override val isReady: Boolean) : Player()

        @Serializable
        data class Bot(override val name: String, val id: String, val difficulty: DefaultLobby.Difficulty) : Player() {
            override val isReady: Boolean = true
        }

        companion object {
            fun create(player: game.Player<DefaultLobby.Human, DefaultLobby.Bot>): Player = when (player) {
                is game.Player.Human -> Human(player.name, player.isReady)
                is game.Player.Bot -> Bot(player.name, player.playerId.asString(), player.impl.difficulty)
            }
        }
    }

    @Serializable
    data class PlayerMe(val id: String, val name: String, val isReady: Boolean)
}