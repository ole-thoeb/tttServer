module Game.TTTGame exposing (TTTGame, decoder, CellState(..), cellStateFromSymbol)


import Array exposing (Array)
import Game.TTTGamePlayer as TTTPlayer
import Json.Decode as Decode exposing (Decoder)


type CellState = X | O | Empty


type alias TTTGame =
    { gameId : String
    , playerMe : TTTPlayer.PlayerMe
    , opponent: TTTPlayer.Player
    , meTurn : Bool
    , board : Array CellState
    }


decoder : Decoder TTTGame
decoder =
    Decode.map5 TTTGame
        (Decode.field "gameId" Decode.string)
        (Decode.field "playerMe" TTTPlayer.meDecoder)
        (Decode.field "opponent" TTTPlayer.decoder)
        (Decode.field "meTurn" Decode.bool)
        (Decode.field "board" boardDecoder)


cellStateFromSymbol : TTTPlayer.Symbol -> CellState
cellStateFromSymbol symbol =
    case symbol of
        TTTPlayer.X ->
            X

        TTTPlayer.O ->
            O


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
