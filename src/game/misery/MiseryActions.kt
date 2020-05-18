package game.misery

import GameId
import arrow.core.constant
import arrow.core.identity
import arrow.core.toT
import game.*
import messages.requests.MiseryGameRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import messages.Messages
import messages.noMessages
import messages.responses.MiseryInGameResponse
import skynet.MinMaxPlayer
import skynet.MiseryBoard
import skynet.MiseryStrategy
import skynet.minMax
import kotlin.random.Random

suspend fun MiseryGameServer.handleGameRequest(gameRequest: MiseryGameRequest): Messages = updateGame(gameRequest.gameId) { game ->
    when (game) {
        is Game.Lobby -> game toT lobbyStateMsgs(game)
        is Game.InGame -> {
            val updatedGame = when (gameRequest) {
                is MiseryGameRequest.SetPiece -> game.update {
                    setPiece(gameRequest.content.index, gameRequest.playerId).fold(constant(this), { updatedGame ->
                        val nextRoundPlayer = updatedGame.twoPlayerGame.turnPlayer()
                        if (updatedGame.twoPlayerGame.status == TwoPlayerGame.Status.OnGoing && nextRoundPlayer is Player.Bot) {
                            launchBotSetPieceAction(updatedGame.id)
                        }
                        updatedGame
                    })
                }
            }
            updatedGame toT inGameStateMsgs(updatedGame)
        }
    }
}

fun MiseryGameServer.launchBotSetPieceAction(gameId: GameId): Job = launchAsyncAction {
    delay(Random.nextLong(500, 1500))
    asyncUpdateGame(gameId) { game ->
        when (game) {
            is Game.Lobby -> {
                log.error("[PlayBotTurn] failed because game was a lobby")
                game toT noMessages()
            }
            is Game.InGame -> {
                val updatedGame = playBotTurn(game)
                updatedGame toT inGameStateMsgs(updatedGame)
            }
        }
    }
}

fun GameServer<*, *>.playBotTurn(game: Game.InGame<MiseryInGame>): Game.InGame<MiseryInGame> = game.update {
    val turnPlayer = twoPlayerGame.turnPlayer()
    if (turnPlayer !is Player.Bot) {
        log.warn("[PlayBotTurn] but current player is not a bot")
        return game
    } else {
        val index = MiseryStrategy.minMax(MiseryBoard(board.map { state ->
            when (state) {
                MiseryInGame.CellState.X -> MiseryBoard.CellState.X
                MiseryInGame.CellState.EMPTY -> MiseryBoard.CellState.EMPTY
            }
        }, lastPlayer = MinMaxPlayer.MIN)).move
        setPiece(index, turnPlayer.playerId).fold(
                { e ->
                    log.error("[PlayBotTurn] failed to set piece with error $e")
                    this
                },
                ::identity
        )
    }
}


fun inGameStateMsgs(inGame: Game.InGame<MiseryInGame>): Messages =
        inGameStateMsgs(inGame, MiseryInGameResponse.State.Companion::forPlayer)