port module Websocket exposing (send, receive, connect, disconnect)

import Json.Encode as Encode
import Url.Builder


port send : Encode.Value -> Cmd msg


port receive : (String -> msg) -> Sub msg


port disconnect_ : () -> Cmd msg


disconnect : Cmd msg
disconnect = disconnect_ ()


port connect_ : Encode.Value -> Cmd msg


connect : String -> Cmd msg
connect gameId =
    Encode.object
        [ ( "url", Encode.string <| Url.Builder.absolute [ "ttt", gameId, "ws" ] [])
        ]
        |> connect_