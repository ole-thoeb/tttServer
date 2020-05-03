module ServerRequest.InGame exposing (..)


import Game
import Json.Encode as Encode
import ServerRequest.JsonHelper exposing (remoteMsg)


setPiece : Game.Id -> String -> Int -> Encode.Value
setPiece gameId playerId index =
    Encode.object
        [ ( "playerId", Encode.string playerId )
        , ( "gameId", Game.encodeId gameId )
        , ( "index", Encode.int index )
        ]
        |> remoteMsg "tttSetPiece"