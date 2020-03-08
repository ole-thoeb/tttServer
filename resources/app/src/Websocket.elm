port module Websocket exposing (send, receive, connect)

import Json.Encode as Encode
import Json.Decode as Decode
import Url.Builder


port send : Encode.Value -> Cmd msg


port receive : (Decode.Value -> msg) -> Sub msg


port connect_ : Encode.Value -> Cmd msg


connect : String -> Cmd msg
connect gameId =
    Encode.object
        [ ( "url", Encode.string <| Url.Builder.absolute [ "ttt", gameId, "ws" ] [])
        ]
        |> connect_