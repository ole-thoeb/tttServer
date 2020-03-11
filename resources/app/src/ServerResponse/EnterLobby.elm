module ServerResponse.EnterLobby exposing (decoder, Response(..), Error(..), errorToQueryParam, errorQueryParser)


import Game.Lobby as Lobby exposing (Lobby)
import Json.Decode as Decode exposing (Decoder)
import ServerResponse.JsonHelper exposing (typeDecoder, contentDecoder)
import Url.Parser.Query as Query
import Url.Builder exposing (QueryParameter)


type Response
    = LobbyState Lobby
    | Error Error


type Error
    = LobbyFull Int
    | GameAlreadyStarted String
    | NoSuchGame String


errorToQueryParam : Error -> List QueryParameter
errorToQueryParam error =
    let
        query = Url.Builder.string "lobbyError"
    in
    case error of
        LobbyFull max ->
            [ query <| "full§" ++ String.fromInt max ]

        GameAlreadyStarted id ->
            [ query <| "started§" ++ id ]

        NoSuchGame id ->
            [ query <| "noGame§" ++ id ]


errorQueryParser : Query.Parser (Maybe Error)
errorQueryParser =
    Query.custom "lobbyError" <| \list ->
        case list of
            [ error ] ->
                let
                    parts = String.split "§" error
                in
                case parts of
                    [ "full", max ] ->
                        Maybe.map LobbyFull (String.toInt max)

                    [ "started", id ] ->
                        Just (GameAlreadyStarted id)

                    [ "noGame", id ] ->
                        Just (NoSuchGame id)
                    _ -> Nothing
            _ ->
                Nothing


decoder : Decoder Response
decoder =
    typeDecoder |> Decode.andThen responseDecoder


responseDecoder : String -> Decoder Response
responseDecoder type_ =
    case type_ of
        "lobbyState" -> contentDecoder lobbyStateDecoder
        "lobbyFull" -> contentDecoder lobbyFullDecoder
        "gameAlreadyStarted" -> contentDecoder gameAlreadyStartedDecoder
        "noSuchGame" -> contentDecoder noSuchGameDecoder
        _ -> Decode.fail <| "Unknown type '" ++ type_ ++ "' to EnterLobbyResponse"


lobbyStateDecoder : Decoder Response
lobbyStateDecoder =
    Decode.map LobbyState Lobby.decoder


lobbyFullDecoder : Decoder Response
lobbyFullDecoder =
    Decode.map (Error << LobbyFull) <| Decode.field "maxPlayers" Decode.int


gameAlreadyStartedDecoder : Decoder Response
gameAlreadyStartedDecoder =
    Decode.map (Error << GameAlreadyStarted) gameIdDecoder


noSuchGameDecoder : Decoder Response
noSuchGameDecoder =
    Decode.map (Error << NoSuchGame) gameIdDecoder


gameIdDecoder : Decoder String
gameIdDecoder =
    Decode.field "gameId" Decode.string