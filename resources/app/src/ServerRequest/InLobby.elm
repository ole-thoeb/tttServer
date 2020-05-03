module ServerRequest.InLobby exposing (nameChangedMsg, readyChangedMsg, addBotMsg)


import Game
import Game.LobbyPlayer exposing (PlayerMe)
import Json.Encode as Encode
import ServerRequest.JsonHelper exposing (remoteMsg)


nameChangedMsg : Game.Id -> PlayerMe -> Encode.Value
nameChangedMsg gameId changedPlayer =
    Encode.object
        ( (header gameId changedPlayer) ++
        [ ( "name", Encode.string changedPlayer.name ) ] )
        |> remoteMsg "lobbyName"


readyChangedMsg : Game.Id -> PlayerMe -> Encode.Value
readyChangedMsg gameId changedPlayer =
    Encode.object
        ( (header gameId changedPlayer) ++
        [ ( "isReady", Encode.bool changedPlayer.isReady ) ] )
        |> remoteMsg "lobbyReady"


addBotMsg : Game.Id -> PlayerMe -> Encode.Value
addBotMsg gameId requestingPlayer =
    Encode.object
        (header gameId requestingPlayer)
        |> remoteMsg "addBot"


header : Game.Id -> PlayerMe -> List ( String, Encode.Value )
header gameId player =
    [ ( "gameId", Game.encodeId gameId )
    , ( "playerId", Encode.string player.id )
    ]