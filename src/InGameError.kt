sealed class InGameError {
    data class IllegalPlace(val illegalIndex: Int) : InGameError()
    data class WrongTurn(val tried: TTTGame.InGame.PlayerRef?, val actual: TTTGame.InGame.PlayerRef) : InGameError()
}

