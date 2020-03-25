module Page.TTT.Game exposing (Model, toSession, fromLobby, Msg, update, view, subscriptions)

import Browser.Navigation as Nav
import Game.Lobby as Lobby
import Html
import Http
import Json.Decode as Decode exposing (Decoder)
import Page.Blank
import Page.Home as Home
import Page.TTT.InGame as InGame
import Page.TTT.Lobby as Lobby
import ServerResponse.EnterLobby as EnterLobbyResponse
import ServerResponse.InGame as InGameResponse
import ServerResponse.InLobby as InLobbyResponse
import ServerResponse.TTTResponse as TTTResponse
import Session exposing (Session)
import Url.Builder

import Util exposing (dummy, updateWith)
import Websocket

-- MODEL


type Model
    = Lobby Lobby.Model
    | InGame InGame.Model
    | Loading Session


toSession : Model -> Session
toSession model =
    case model of
        Lobby lobby ->
            Lobby.toSession lobby

        InGame inGame ->
            InGame.toSession inGame

        Loading session ->
            session


fromLobby : Session -> String -> Maybe Lobby.Lobby -> ( Model, Cmd Msg )
fromLobby session gameId maybeLobby =
    case maybeLobby of
        Just lobby ->
            Lobby.init session lobby
                |> updateWith Lobby GotLobbyMsg

        Nothing ->
            ( Loading session
            , Http.get
                { url = (Url.Builder.absolute [ "joinGame", gameId ] [])
                , expect = Http.expectJson JoinResponse TTTResponse.decoder
                }
            )


-- UPDATE


type Msg
    = GotLobbyMsg Lobby.Msg
    | GotInGameMsg InGame.Msg
    | JoinResponse (Result Http.Error TTTResponse.Response)
    | WebSocketIn String


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    let
        session = model |> toSession
    in
    case ( msg, model ) of
        ( GotLobbyMsg subMsg, Lobby lobby ) ->
            Lobby.update subMsg lobby
                |> updateWith Lobby GotLobbyMsg

        ( GotInGameMsg subMsg, InGame inGame ) ->
            InGame.update subMsg inGame
                |> updateWith InGame GotInGameMsg

        ( JoinResponse result, Loading _ ) ->
            case result of
                Ok response ->
                    case response of
                        TTTResponse.EnterLobbyResponse (EnterLobbyResponse.LobbyState lobby) ->
                            Lobby.init session lobby
                                |> updateWith Lobby GotLobbyMsg

                        TTTResponse.EnterLobbyResponse (EnterLobbyResponse.Error error) ->
                            ( model
                            , Nav.pushUrl (Session.navKey session) (Url.Builder.absolute [] (Home.joinErrorToQueryParam <| Home.LobbyError error))
                            )

                        TTTResponse.InLobbyResponse (InLobbyResponse.LobbyState lobby) ->
                            Lobby.init session lobby
                                |> updateWith Lobby GotLobbyMsg

                        TTTResponse.InGameResponse (InGameResponse.GameState game) ->
                            InGame.init session game
                                |> updateWith InGame GotInGameMsg

                        TTTResponse.InGameResponse (InGameResponse.PlayerDisc _) ->
                            ( model, Cmd.none )

                Err error ->
                    ( model
                    , Nav.pushUrl (Session.navKey session) (Url.Builder.absolute [] (Home.joinErrorToQueryParam <| Home.ConnectionError (Just error))))

        ( WebSocketIn message, _ ) ->
            case model of
                Lobby lobby ->
                    case Decode.decodeString InLobbyResponse.decoder message of
                        Ok response ->
                            Lobby.updateFromWebsocket response lobby
                                |> updateWith Lobby GotLobbyMsg

                        Err _ ->
                            case Decode.decodeString InGameResponse.decoder message of
                                Ok (InGameResponse.GameState game) ->
                                    InGame.init session game
                                        |> updateWith InGame GotInGameMsg

                                Err error ->
                                    ( Debug.log ("json error lobby" ++ (Decode.errorToString error)) model, Cmd.none )

                                _ ->
                                    ( model, Cmd.none)

                InGame inGame ->
                    case Decode.decodeString InGameResponse.decoder message of
                        Ok response ->
                            InGame.updateFromWebsocket response inGame
                                |> updateWith InGame GotInGameMsg

                        Err error ->
                            ( dummy (Debug.log "json error inGame" error) model, Cmd.none )

                Loading _ ->
                    ( model, Cmd.none )

        ( _, _ ) ->
            ( model, Cmd.none )


-- VIEW


view : Model -> { title: String, body: Html.Html Msg }
view model =
    case model of
        Lobby lobby ->
            viewFragment GotLobbyMsg (Lobby.view lobby)

        InGame inGame ->
            viewFragment GotInGameMsg (InGame.view inGame)

        Loading session ->
            Page.Blank.view


viewFragment : (subMsg -> Msg) -> { title: String, body: Html.Html subMsg } ->  { title: String, body: Html.Html Msg }
viewFragment toMsg { title, body } =
    { title = title
    , body = Html.map toMsg body
    }


-- SUBSCRIPTIONS

subscriptions : Model -> Sub Msg
subscriptions model =
    case model of
        Lobby lobby ->
            Websocket.receive WebSocketIn

        InGame session ->
            Websocket.receive WebSocketIn

        Loading session ->
            Sub.none


-- GAME RESPONSE
