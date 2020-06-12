package messages.requests

import GameId
import PlayerId
import arrow.core.k
import json.JsonTypeDeserializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

sealed class StoplightGameRequest {

    abstract val gameId: GameId
    abstract val playerId: PlayerId

    @Serializable
    data class SetPiece(val content: Content) : StoplightGameRequest() {
        val type: String = TYPE

        @Transient
        override val gameId = GameId(content.gameId)
        @Transient
        override val playerId = PlayerId(content.playerId)

        @Serializable
        data class Content(val playerId: String, val gameId: String, val index: Int)

        companion object : JsonTypeDeserializer<SetPiece, Content> {
            override val typeConstructor: (Content) -> SetPiece = StoplightGameRequest::SetPiece
            override val TYPE: String = "stoplightSetPiece"
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
        }
    }

    companion object {
        val deserializers = listOf(SetPiece).k()
    }
}