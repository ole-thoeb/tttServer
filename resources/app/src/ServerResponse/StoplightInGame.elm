module ServerResponse.StoplightInGame exposing (Response(..), decoder)

import Game.StoplightGame as StoplightGame exposing (StoplightGame)
import Json.Decode as Decode exposing (Decoder)
import ServerResponse.JsonHelper exposing (typeDecoder, contentDecoder)

type Response
    = PlayerDisc String
    | GameState StoplightGame


decoder : Decoder Response
decoder =
    typeDecoder |> Decode.andThen responseDecoder


responseDecoder : String -> Decoder Response
responseDecoder type_ =
    case Debug.log "decoded type" type_ of
        "playerDisconnected" ->
            contentDecoder playerDiscDecoder

        "stoplightInGameState" ->
            contentDecoder gameStateDecoder

        _ -> Decode.fail <| "Unknown type '" ++ type_ ++ "' to InGameResponse"


playerDiscDecoder : Decoder Response
playerDiscDecoder =
    Decode.map PlayerDisc (Decode.field "discPlayerName" Decode.string)


gameStateDecoder : Decoder Response
gameStateDecoder =
    Decode.map GameState StoplightGame.decoder