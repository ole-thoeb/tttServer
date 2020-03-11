module ServerResponse.InLobby exposing (Response(..), decoder)

import Game.Lobby as Lobby exposing (Lobby)
import Json.Decode as Decode exposing (Decoder)
import ServerResponse.JsonHelper exposing (contentDecoder, typeDecoder)


type Response
    = LobbyState Lobby


decoder : Decoder Response
decoder =
    typeDecoder |> Decode.andThen responseDecoder


responseDecoder : String -> Decoder Response
responseDecoder type_ =
    case type_ of
        "lobbyState" -> contentDecoder lobbyStateDecoder
        _ -> Decode.fail <| "Unknown type '" ++ type_ ++ "' to InLobbyResponse"


lobbyStateDecoder : Decoder Response
lobbyStateDecoder =
    Decode.map LobbyState Lobby.decoder