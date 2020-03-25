module ServerResponse.TTTResponse exposing (Response(..), decoder)


import Json.Decode as Decode exposing (Decoder)
import ServerResponse.EnterLobby as EnterLobbyResponse
import ServerResponse.InGame as InGameResponse
import ServerResponse.InLobby as InLobbyResponse


type Response
    = EnterLobbyResponse EnterLobbyResponse.Response
    | InLobbyResponse InLobbyResponse.Response
    | InGameResponse InGameResponse.Response


decoder : Decoder Response
decoder =
    Decode.oneOf
        [ Decode.map EnterLobbyResponse EnterLobbyResponse.decoder
        , Decode.map InLobbyResponse InLobbyResponse.decoder
        , Decode.map InGameResponse InGameResponse.decoder
        ]