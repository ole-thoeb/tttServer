package messages

import GameId
import game.TechnicalPlayer
import json.JsonSerializable
import messages.responses.GameResponse


typealias MessagesOf<MSG> = Map<TechnicalPlayer, MSG>

typealias Messages = MessagesOf<JsonSerializable>

fun Messages.isCouldNotMatchGame(): Boolean = size == 1 && this[TechnicalPlayer.DUMMY] is GameResponse.NoSuchGame

fun noMessages(): MessagesOf<Nothing> = emptyMap()

fun noSuchGame(unknownGameId: GameId): Messages =
        mapOf(TechnicalPlayer.DUMMY to GameResponse.NoSuchGame(unknownGameId.asString()))