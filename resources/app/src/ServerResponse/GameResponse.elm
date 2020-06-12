module ServerResponse.GameResponse exposing (Response(..), decoder, DefaultInGame(..), defaultInGameDecoder)


import Json.Decode as Decode exposing (Decoder)
import ServerResponse.EnterLobby as EnterLobbyResponse
import ServerResponse.InLobby as InLobbyResponse
import ServerResponse.MiseryInGame as MiseryResponse
import ServerResponse.StoplightInGame as StoplightResponse
import ServerResponse.TTTInGame as TTTResponse


type Response g
    = EnterLobbyResponse EnterLobbyResponse.Response
    | InLobbyResponse InLobbyResponse.Response
    | InGameResponse g


decoder : Decoder g -> Decoder (Response g)
decoder inGameDecoder =
    Decode.oneOf
        [ Decode.map EnterLobbyResponse EnterLobbyResponse.decoder
        , Decode.map InLobbyResponse InLobbyResponse.decoder
        , Decode.map InGameResponse inGameDecoder
        ]



type DefaultInGame
    = TTTResponse TTTResponse.Response
    | MiseryResponse MiseryResponse.Response
    | StoplightResponse StoplightResponse.Response


defaultInGameDecoder : Decoder DefaultInGame
defaultInGameDecoder =
    Decode.oneOf
        [ Decode.map TTTResponse TTTResponse.decoder
        , Decode.map MiseryResponse MiseryResponse.decoder
        , Decode.map StoplightResponse StoplightResponse.decoder
        ]