module Game.StoplightGame exposing (CellState(..), StoplightGame, decoder)

import Array exposing (Array)
import Game
import Game.Game as Game
import Game.StoplightGamePlayer as StoplightPlayer
import Json.Decode as Decode exposing (Decoder)


type CellState
    = Green
    | Yellow
    | Red
    | Empty


type alias StoplightGame =
    { gameId : Game.Id
    , playerMe : StoplightPlayer.PlayerMe
    , opponent : StoplightPlayer.Player
    , meTurn : Bool
    , board : Array CellState
    , status : Game.Status
    }



-- DECODER


decoder : Decoder StoplightGame
decoder =
    Decode.map6 StoplightGame
        (Decode.field "gameId" Game.idDecoder)
        (Decode.field "playerMe" StoplightPlayer.meDecoder)
        (Decode.field "opponent" StoplightPlayer.decoder)
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
                    "GREEN" ->
                        Decode.succeed Green

                    "YELLOW" ->
                        Decode.succeed Yellow

                    "RED" ->
                        Decode.succeed Red

                    "EMPTY" ->
                        Decode.succeed Empty

                    _ ->
                        Decode.fail ("Unknown cell state " ++ stateStr)
            )
