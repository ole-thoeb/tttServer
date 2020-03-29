module Page.Rematch exposing (Model, init, toSession, update, Msg, view, subscriptions)

import Browser.Navigation as Nav
import Element
import Html
import Http
import Page.Home as Home
import ServerResponse.EnterLobby as EnterLobby
import Session exposing (Session)
import UIHelper
import Url.Builder
import Websocket


type Model =
    Loading Session


init : Session -> String -> ( Model, Cmd Msg )
init session oldGameId =
    ( Loading session
    , Cmd.batch
        [ Websocket.disconnect
        , Http.get
            { url = (Url.Builder.absolute [ "rematch", oldGameId ] [])
            , expect = Http.expectJson ServerResponse EnterLobby.decoder
            }
        ]
    )


toSession : Model -> Session
toSession model =
    case model of
        Loading session -> session


-- UPDATE


type Msg
    = ServerResponse (Result Http.Error EnterLobby.Response)


update: Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    let
        session = model |> toSession
    in
    case msg of
        ServerResponse result ->
            case result of
                Ok response ->
                    case response of
                        EnterLobby.LobbyState lobby ->
                            ( model
                            , Nav.pushUrl (session |> Session.navKey)
                                <| Url.Builder.absolute
                                    [ "game"
                                    , lobby.gameId
                                    ] []
                            )

                        EnterLobby.Error error ->
                            ( model
                            , Nav.pushUrl (Session.navKey session) (Url.Builder.absolute []
                                (Home.joinErrorToQueryParam <| Home.LobbyError error))
                            )

                Err error ->
                    ( model
                    , Nav.pushUrl (Session.navKey session) (Url.Builder.absolute []
                        (Home.joinErrorToQueryParam <| Home.ConnectionError (Just error)))
                    )


-- VIEW


view : Model -> { title: String, body: Html.Html Msg }
view model =
    let
        theme = model |> toSession |> Session.theme
    in
    { title = "Rematch"
    , body = Element.layout [] (UIHelper.loading theme)
    }


-- SUBSCRIPTIONS


subscriptions : Model -> Sub Msg
subscriptions _ =
    Sub.none
