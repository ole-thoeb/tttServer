port module Websocket exposing (send, receive, connect, disconnect)

import Endpoint
import Game
import Json.Encode as Encode
import Url.Builder


port send : Encode.Value -> Cmd msg


port receive : (String -> msg) -> Sub msg


port disconnect_ : () -> Cmd msg


disconnect : Cmd msg
disconnect = disconnect_ ()


port connect_ : Encode.Value -> Cmd msg


connect : Game.Mode -> Game.Id -> Cmd msg
connect mode gameId =
    Encode.object
        [ ( "url", Encode.string <| Endpoint.websocket mode gameId )
        ]
        |> connect_