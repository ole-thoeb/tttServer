package messages.requests

import arrow.Kind
import arrow.core.k
import arrow.typeclasses.MonadError
import json.JsonError
import json.JsonTypeDeserializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

sealed class LobbyRequest {
    
    @Serializable
    data class Ready(val content: Content) : LobbyRequest() {
        val type: String = TYPE
        
        @Serializable
        data class Content(val playerId: String, val gameId: String, val isReady: Boolean)
        
        companion object : JsonTypeDeserializer<Ready, Content> {
            override val typeConstructor: (Content) -> Ready = LobbyRequest::Ready
            override val TYPE: String = "lobbyReady"
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
        }
    }
    
    companion object {
        val deserializers = kotlin.run {
            println(Ready)
            listOf(Ready).k()
        }
        
        fun <F> fromJson(type: String, jsonObj: JsonObject, ME: MonadError<F, JsonError>): Kind<F, LobbyRequest> = when {
            type.equals("ready", true) -> Ready.fromContentJson(jsonObj, ME)
            else -> ME.raiseError(JsonError.UnknownType("There is no LobbyRequest of type $type"))
        }
    }
}