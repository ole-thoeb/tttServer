import game.ttt.TTTGame

sealed class InGameError {
    data class IllegalPlace(val illegalIndex: Int) : InGameError()
    data class WrongTurn(val tried: TTTGame.InGame.PlayerRef?, val actual: TTTGame.InGame.PlayerRef) : InGameError()
    data class IllegalStatus(val status: TTTGame.InGame.Status) : InGameError()
}

