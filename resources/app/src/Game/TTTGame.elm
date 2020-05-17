module Game.TTTGame exposing (TTTGame, decoder, CellState(..), cellStateFromSymbol, playerOfSymbol, playerFromRef)


import Array exposing (Array)
import Game
import Game.Game as Game
import Game.GamePlayer as Player
import Game.TTTGamePlayer as TTTPlayer
import Json.Decode as Decode exposing (Decoder)


type CellState = X | O | Empty


type alias TTTGame =
    { gameId : Game.Id
    , playerMe : TTTPlayer.PlayerMe
    , opponent: TTTPlayer.Player
    , meTurn : Bool
    , board : Array CellState
    , status : Game.Status
    }


-- UTILITY


playerOfSymbol : TTTGame -> TTTPlayer.Symbol -> TTTPlayer.Player
playerOfSymbol game symbol =
    if game.playerMe.symbol == symbol then
        TTTPlayer.meAsPlayer game.playerMe
    else
        game.opponent


playerFromRef : TTTGame -> Player.PlayerRef -> TTTPlayer.Player
playerFromRef game ref =
    if game.playerMe.playerRef == ref then
        TTTPlayer.meAsPlayer game.playerMe
    else
        game.opponent


cellStateFromSymbol : TTTPlayer.Symbol -> CellState
cellStateFromSymbol symbol =
    case symbol of
        TTTPlayer.X ->
            X

        TTTPlayer.O ->
            O


-- DECODER


decoder : Decoder TTTGame
decoder =
    Decode.map6 TTTGame
        (Decode.field "gameId" Game.idDecoder)
        (Decode.field "playerMe" TTTPlayer.meDecoder)
        (Decode.field "opponent" TTTPlayer.decoder)
        (Decode.field "meTurn" Decode.bool)
        (Decode.field "board" boardDecoder)
        (Decode.field "status" Game.statusDecoder)


boardDecoder : Decoder (Array CellState)
boardDecoder =
    Decode.array cellDecoder


cellDecoder : Decoder CellState
cellDecoder =
    Decode.string |> Decode.andThen (\stateStr ->
        case stateStr of
            "X" -> Decode.succeed X
            "O" -> Decode.succeed O
            "EMPTY" -> Decode.succeed Empty
            _ -> Decode.fail ("Unknown cell state " ++ stateStr)
        )
