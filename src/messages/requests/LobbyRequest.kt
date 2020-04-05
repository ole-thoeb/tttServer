package messages.requests

import GameId
import PlayerId
import arrow.Kind
import arrow.core.k
import arrow.typeclasses.MonadError
import json.JsonError
import json.JsonTypeDeserializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Transient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

sealed class LobbyRequest {

    abstract val gameId: GameId
    abstract val playerId: PlayerId

    @Serializable
    data class Ready(val content: Content) : LobbyRequest() {
        val type: String = TYPE

        @Transient override val gameId = GameId(content.gameId)
        @Transient override val playerId = PlayerId(content.playerId)

        @Serializable
        data class Content(val playerId: String, val gameId: String, val isReady: Boolean)
        
        companion object : JsonTypeDeserializer<Ready, Content> {
            override val typeConstructor: (Content) -> Ready = LobbyRequest::Ready
            override val TYPE: String = "lobbyReady"
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
        }
    }

    @Serializable
    data class Name(val content: Content) : LobbyRequest() {
        val type: String = TYPE

        @Transient override val gameId = GameId(content.gameId)
        @Transient override val playerId = PlayerId(content.playerId)

        @Serializable
        data class Content(val playerId: String, val gameId: String, val name: String)

        companion object : JsonTypeDeserializer<Name, Content> {
            override val typeConstructor: (Content) -> Name = LobbyRequest::Name
            override val TYPE: String = "lobbyName"
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
        }
    }

    @Serializable
    data class AddBot(val content: Content) : LobbyRequest() {
        val type: String = TYPE

        @Transient override val gameId = GameId(content.gameId)
        @Transient override val playerId = PlayerId(content.playerId)

        @Serializable
        data class Content(val playerId: String, val gameId: String)

        companion object : JsonTypeDeserializer<AddBot, Content> {
            override val typeConstructor = LobbyRequest::AddBot
            override val TYPE: String = "addBot"
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
        }
    }
    
    companion object {
        val deserializers = listOf(Ready, Name, AddBot).k()
        
        fun <F> fromJson(type: String, jsonObj: JsonObject, ME: MonadError<F, JsonError>): Kind<F, LobbyRequest> = when {
            type.equals("ready", true) -> Ready.fromContentJson(jsonObj, ME)
            else -> ME.raiseError(JsonError.UnknownType("There is no LobbyRequest of type $type"))
        }
    }
}