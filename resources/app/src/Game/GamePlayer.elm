module Game.GamePlayer exposing (PlayerRef(..), idDecoder, nameDecoder, playerRefDecoder)

import Json.Decode as Decode exposing (Decoder)


type PlayerRef
    = P1
    | P2


idDecoder : Decoder String
idDecoder =
    Decode.field "id" Decode.string


nameDecoder : Decoder String
nameDecoder =
    Decode.field "name" Decode.string


playerRefDecoder : Decoder PlayerRef
playerRefDecoder =
    Decode.string
        |> Decode.andThen
            (\ref ->
                case ref of
                    "P1" ->
                        Decode.succeed P1

                    "P2" ->
                        Decode.succeed P2

                    _ ->
                        Decode.fail ("unknown playerRef " ++ ref)
            )
