module Game.TTTGame exposing (TTTGame, decoder, CellState(..), cellStateFromSymbol, Status(..))


import Array exposing (Array)
import Game.TTTGamePlayer as TTTPlayer
import Json.Decode as Decode exposing (Decoder)


type CellState = X | O | Empty


type Status
    = OnGoing
    | Draw
    | Win TTTPlayer.PlayerRef Int Int Int


type alias TTTGame =
    { gameId : String
    , playerMe : TTTPlayer.PlayerMe
    , opponent: TTTPlayer.Player
    , meTurn : Bool
    , board : Array CellState
    , status : Status
    }


decoder : Decoder TTTGame
decoder =
    Decode.map6 TTTGame
        (Decode.field "gameId" Decode.string)
        (Decode.field "playerMe" TTTPlayer.meDecoder)
        (Decode.field "opponent" TTTPlayer.decoder)
        (Decode.field "meTurn" Decode.bool)
        (Decode.field "board" boardDecoder)
        (Decode.field "status" statusDecoder)


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


statusDecoder : Decoder Status
statusDecoder =
        Decode.field "type" Decode.string |> Decode.andThen
            (\status -> case status of
                "OnGoing" -> Decode.succeed OnGoing
                "Draw" -> Decode.succeed Draw
                "Win" -> winDecoder
                _ -> Decode.fail ("Unknown status " ++ status)
            )



winDecoder : Decoder Status
winDecoder =
    Decode.map4 Win
        (Decode.field "winner" TTTPlayer.playerRefDecoder)
        (Decode.field "winField1" Decode.int)
        (Decode.field "winField2" Decode.int)
        (Decode.field "winField3" Decode.int)