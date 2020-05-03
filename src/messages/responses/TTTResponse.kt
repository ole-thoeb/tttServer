package messages.responses

import json.JsonSerializable
import json.JsonTypeDeserializer
import json.packageJson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

sealed class TTTResponse : JsonSerializable {
    @Serializable
    data class NoSuchGame(val content: Content) : TTTResponse() {
        val type = TYPE

        constructor(gameId: String) : this(Content(gameId))

        override fun toJson(): JsonElement {
            return packageJson.toJson(serializer(), this)
        }

        @Serializable
        data class Content(val gameId: String)

        companion object : JsonTypeDeserializer<NoSuchGame, Content> {
            override val typeConstructor: (Content) -> NoSuchGame = TTTResponse::NoSuchGame
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
            override val TYPE: String = "noSuchGame"
        }
    }

    @Serializable
    data class NoAssociatedGame(val content: Content) : TTTResponse() {
        val type = TYPE

        constructor() : this(Content)

        override fun toJson(): JsonElement {
            return packageJson.toJson(serializer(), this)
        }

        @Serializable
        object Content

        companion object : JsonTypeDeserializer<NoAssociatedGame, Content> {
            override val typeConstructor: (Content) -> NoAssociatedGame = TTTResponse::NoAssociatedGame
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
            override val TYPE: String = "noAssociatedGame"
        }
    }

    @Serializable
    class PlayerDisconnected(val content: Content) : TTTResponse() {
        val type = TYPE

        constructor(discPlayerName: String) : this(Content(discPlayerName))

        override fun toJson(): JsonElement {
            return packageJson.toJson(serializer(), this)
        }

        @Serializable
        data class Content(val discPlayerName: String)

        companion object : JsonTypeDeserializer<PlayerDisconnected, Content> {
            override val typeConstructor: (Content) -> PlayerDisconnected = TTTResponse::PlayerDisconnected
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
            override val TYPE: String = "playerDisconnected"
        }
    }

    @Serializable
    class PlayerConnected(val content: Content) : TTTResponse() {
        val type = TYPE

        constructor(conPlayerName: String) : this(Content(conPlayerName))

        override fun toJson(): JsonElement {
            return packageJson.toJson(serializer(), this)
        }

        @Serializable
        data class Content(val conPlayerName: String)

        companion object : JsonTypeDeserializer<PlayerConnected, Content> {
            override val typeConstructor: (Content) -> PlayerConnected = TTTResponse::PlayerConnected
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
            override val TYPE: String = "playerDisconnected"
        }
    }
}