module ServerRequest.JsonHelper exposing (..)


import Json.Encode as Encode


remoteMsg : String -> Encode.Value -> Encode.Value
remoteMsg type_ content =
    Encode.object
        [ ( "type", Encode.string type_ )
        , ( "content", content )
        ]