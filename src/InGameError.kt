sealed class InGameError {
    data class IllegalPlace(val illegalX: Int, val illegalY: Int) : InGameError()
    data class WrongTurn(val tried: TTTGame.InGame.PlayerRef?, val actual: TTTGame.InGame.PlayerRef) : InGameError()
}

