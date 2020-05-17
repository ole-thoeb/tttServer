module Game.Game exposing (Status(..), statusDecoder)

import Game.GamePlayer as Player
import Json.Decode as Decode exposing (Decoder)


type Status
    = OnGoing
    | Draw
    | Win Player.PlayerRef Int Int Int


statusDecoder : Decoder Status
statusDecoder =
    Decode.field "type" Decode.string
        |> Decode.andThen
            (\status ->
                case status of
                    "OnGoing" ->
                        Decode.succeed OnGoing

                    "Draw" ->
                        Decode.succeed Draw

                    "Win" ->
                        winDecoder

                    _ ->
                        Decode.fail ("Unknown status " ++ status)
            )


winDecoder : Decoder Status
winDecoder =
    Decode.map4 Win
        (Decode.field "winner" Player.playerRefDecoder)
        (Decode.field "winField1" Decode.int)
        (Decode.field "winField2" Decode.int)
        (Decode.field "winField3" Decode.int)
