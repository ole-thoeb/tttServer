module Page.TTT.Game exposing (Model, toSession, fromLobby, Msg, update, view, subscriptions)

import Game.Lobby as Lobby
import Html
import Http
import Json.Decode as Decode
import Page.Blank
import Page.TTT.Lobby as Lobby
import ServerResponse.EnterLobby as EnterLobby
import Session exposing (Session)
import Url.Builder

import Util exposing (updateWith)
import Websocket

-- MODEL


type Model
    = Lobby Lobby.Model
    | InGame Session
    | Loading Session


toSession : Model -> Session
toSession model =
    case model of
        Lobby lobby ->
            Lobby.toSession lobby

        InGame session ->
            session

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
                , expect = Http.expectJson JoinResponse EnterLobby.decoder
                }
            )


-- UPDATE


type Msg
    = GotLobbyMsg Lobby.Msg
    | GotInGameMsg
    | JoinResponse (Result Http.Error EnterLobby.Response)
    | WebSocketIn Decode.Value


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case ( msg, model ) of
        ( GotLobbyMsg subMsg, Lobby lobby ) ->
            Lobby.update subMsg lobby
                |> updateWith Lobby GotLobbyMsg

        ( GotInGameMsg, InGame _) ->
            ( model, Cmd.none )

        ( JoinResponse result, Loading session) ->
            case result of
                Ok response ->
                    case response of
                        EnterLobby.LobbyState lobby ->
                            Lobby.init session lobby
                                |> updateWith Lobby GotLobbyMsg

                        EnterLobby.LobbyFull max ->
                            Debug.todo "handle error"

                        EnterLobby.GameAlreadyStarted gameId ->
                            Debug.todo "handle error"

                        EnterLobby.NoSuchGame gameId ->
                            Debug.todo "handle error"

                Err error ->
                    Debug.todo "handle error"

        ( _, _ ) ->
            ( model, Cmd.none )


-- VIEW


view : Model -> { title: String, body: Html.Html Msg }
view model =
    case model of
        Lobby lobby ->
            viewFragment GotLobbyMsg (Lobby.view lobby)

        InGame session ->
            Debug.todo "Implement InGame view"

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
            Sub.none

        Loading session ->
            Sub.none