module Game.MiseryGame exposing (CellState(..), MiseryGame, decoder)

import Array exposing (Array)
import Game
import Game.Game as Game
import Game.MiseryGamePlayer as MiseryPlayer
import Json.Decode as Decode exposing (Decoder)


type CellState
    = X
    | Empty


type alias MiseryGame =
    { gameId : Game.Id
    , playerMe : MiseryPlayer.PlayerMe
    , opponent : MiseryPlayer.Player
    , meTurn : Bool
    , board : Array CellState
    , status : Game.Status
    }



-- DECODER


decoder : Decoder MiseryGame
decoder =
    Decode.map6 MiseryGame
        (Decode.field "gameId" Game.idDecoder)
        (Decode.field "playerMe" MiseryPlayer.meDecoder)
        (Decode.field "opponent" MiseryPlayer.decoder)
        (Decode.field "meTurn" Decode.bool)
        (Decode.field "board" boardDecoder)
        (Decode.field "status" Game.statusDecoder)


boardDecoder : Decoder (Array CellState)
boardDecoder =
    Decode.array cellDecoder


cellDecoder : Decoder CellState
cellDecoder =
    Decode.string
        |> Decode.andThen
            (\stateStr ->
                case stateStr of
                    "X" ->
                        Decode.succeed X

                    "EMPTY" ->
                        Decode.succeed Empty

                    _ ->
                        Decode.fail ("Unknown cell state " ++ stateStr)
            )
