package messages.responses

import LobbyError
import TTTGame
import arrow.core.ListK
import json.JsonSerializable
import json.JsonTypeDeserializer
import json.packageJson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

sealed class LobbyResponse : JsonSerializable {
    
    @Serializable
    data class State(val content: Content) : LobbyResponse() {
        val type = TYPE
    
        override fun toJson(): JsonElement {
            return packageJson.toJson(serializer(), this)
        }
        
        @Serializable
        data class Content(val gameId: String, val players: List<Player>, val playerMe: PlayerMe) {
            fun toMsg(): State = State(this)
        }
        
        companion object : JsonTypeDeserializer<State, Content> {
            override val typeConstructor: (Content) -> State = LobbyResponse::State
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
            override val TYPE: String = "lobbyState"
            
            fun forPlayer(lobby: TTTGame.Lobby, player: TTTGame.Lobby.Player.Human): State {
                return Content(lobby.id.asString(),
                        lobby.players.filter { it != player }.map { Player(it.name, it.isReady) },
                        PlayerMe(player.technical.playerId.asString(), player.name, player.isReady)
                ).toMsg()
            }
        }
    }
    
    @Serializable
    data class Full(val content: Content) : LobbyResponse() {
        val type = TYPE
    
        override fun toJson(): JsonElement {
            return packageJson.toJson(serializer(), this)
        }
    
        @Serializable
        data class Content(val maxPlayers: Int) {
            fun toMsg(): Full = Full(this)
        }
        
        companion object : JsonTypeDeserializer<Full, Content> {
            override val typeConstructor: (Content) -> Full = LobbyResponse::Full
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
            override val TYPE: String = "lobbyFull"
            
            fun fromError(error: LobbyError.Full): Full = Content(error.maxPlayers).toMsg()
        }
    }
    
    @Serializable
    data class GameAlreadyStarted(val content: Content) : LobbyResponse() {
        val type = TYPE

        constructor(gameId: String) : this(Content(gameId))

        override fun toJson(): JsonElement {
            return packageJson.toJson(serializer(), this)
        }
        
        @Serializable
        data class Content(val gameId: String)
        
        companion object : JsonTypeDeserializer<GameAlreadyStarted, Content> {
            override val typeConstructor: (Content) -> GameAlreadyStarted = LobbyResponse::GameAlreadyStarted
            override val contentDeserializer: DeserializationStrategy<Content> = Content.serializer()
            override val TYPE: String = "gameAlreadyStarted"
        }
    }
}

@Serializable
data class Player(val name: String, val isReady: Boolean)

@Serializable
data class PlayerMe(val id: String, val name: String, val isReady: Boolean)