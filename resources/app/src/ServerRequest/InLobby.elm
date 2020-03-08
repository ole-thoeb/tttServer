module ServerRequest.InLobby exposing (nameChangedMsg, readyChangedMsg)


import Game.LobbyPlayer exposing (PlayerMe)
import Json.Encode as Encode
import ServerRequest.JsonHelper exposing (remoteMsg)


nameChangedMsg : String -> PlayerMe -> Encode.Value
nameChangedMsg gameId changedPlayer =
    Encode.object
        ( (header gameId changedPlayer) ++
        [ ( "name", Encode.string changedPlayer.name ) ] )
        |> remoteMsg "nameChanged"


readyChangedMsg : String -> PlayerMe -> Encode.Value
readyChangedMsg gameId changedPlayer =
    Encode.object
        ( (header gameId changedPlayer) ++
        [ ( "ready", Encode.bool changedPlayer.isReady ) ] )
        |> remoteMsg "readyChanged"


header : String -> PlayerMe -> List ( String, Encode.Value )
header gameId player =
    [ ( "gameId", Encode.string gameId )
    , ( "playerId", Encode.string player.id )
    ]