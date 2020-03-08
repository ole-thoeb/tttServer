sealed class LobbyError {
    
    data class Full(val affectedPlayer: TechnicalPlayer, val maxPlayers: Int) : LobbyError()
}