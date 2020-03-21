module ServerRequest.InGame exposing (..)


import Json.Encode as Encode
import ServerRequest.JsonHelper exposing (remoteMsg)


setPiece : String -> String -> Int -> Encode.Value
setPiece gameId playerId index =
    Encode.object
        [ ( "playerId", Encode.string playerId )
        , ( "gameId", Encode.string gameId )
        , ( "index", Encode.int index )
        ]
        |> remoteMsg "tttSetPiece"