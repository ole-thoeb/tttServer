sealed class LobbyError {
    
    data class Full(val maxPlayers: Int) : LobbyError()
}