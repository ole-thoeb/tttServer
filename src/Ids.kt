import io.ktor.util.generateNonce


/**
 * The [id] field must be non private because of automatic serialisation or so idk
 */
data class SessionId(val id: String) {
    fun asString(): String = id
    
    companion object {
        fun create(): SessionId = SessionId(generateNonce())
    }
}

data class PlayerId(private val id: String) {
    fun asString(): String = id
    
    companion object {
        fun create(): PlayerId = PlayerId(generateNonce())
    }
}

data class GameId(private val id: String) {
    fun asString(): String = id
    
    companion object {
        fun create(): GameId = GameId(generateNonce())
    }
}