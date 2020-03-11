module Page.Home exposing (Msg, view, update, subscriptions, toSession, init, Model)

import Browser.Navigation as Nav
import Game.Lobby as Lobby exposing (Lobby)
import Http
import ServerResponse.EnterLobby as EnterLobby
import Session exposing (Session)
import UIHelper exposing (..)

import Element exposing (..)
import Element.Font as Font
import Element.Lazy exposing (..)
import Element.Background as Background
import Element.Border as Border

import MaterialUI.Button as Button
import MaterialUI.TextField as TextField
import MaterialUI.Theme as Theme

import Html
import Url exposing (Protocol(..))
import Url.Builder



-- MODEL


type alias Model =
    { session: Session
    , gameId: String
    , error: Maybe JoinError
    , lobby: Maybe Lobby
    }


type JoinError
    = LobbyError EnterLobby.Error
    | ConnectionError Http.Error


init : Session -> Maybe EnterLobby.Error -> ( Model, Cmd Msg )
init session maybeError =
    ( { session = session
    , gameId = ""
    , error = Maybe.map LobbyError maybeError
    , lobby = Nothing
    }
    , Cmd.none
    )


toSession : Model -> Session
toSession model =
    model.session


-- UPDATE


type Msg
    = NewTTTGame
    | JoinGame
    | GameId String
    | ServerResponse (Result Http.Error EnterLobby.Response)


update: Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        NewTTTGame ->
            ( model
            , Http.get
                { url = (Url.Builder.absolute [ "newGame" ] [])
                , expect = Http.expectJson ServerResponse EnterLobby.decoder
                }
            )

        JoinGame ->
            ( model
            , Http.get
                { url = (Url.Builder.absolute [ "joinGame", Debug.log "join game url" model.gameId ] [])
                , expect = Http.expectJson ServerResponse EnterLobby.decoder
                }
            )

        GameId gameId ->
            ( { model | gameId = gameId }, Cmd.none )

        ServerResponse result ->
            case result of
                Ok response ->
                    let
                        a = Debug.log "response" response
                    in
                    case response of
                        EnterLobby.LobbyState lobby ->
                            ( { model | lobby = Just lobby }
                            , Nav.pushUrl (model |> toSession |> Session.navKey)
                                <| Url.Builder.absolute
                                    [ "game"
                                    , lobby.gameId
                                    ] []
                            )

                        EnterLobby.Error error ->
                            ( { model | error = Just (LobbyError error) }, Cmd.none )

                Err httpError ->
                    let
                        a = Debug.log "http error" httpError
                    in
                    ( { model | error = Just (ConnectionError httpError) }, Cmd.none )


-- VIEW


view : Model -> { title: String, body: Html.Html Msg }
view model =
    let
        theme = model |> toSession |> Session.theme
    in
    { title = "Home"
    , body =
        layout
            [ Background.color theme.color.background
            , Font.color theme.color.onBackground
            ]
            <| column
                [ centerX
                , width (fill |> maximum 900)
                , padding 16
                , spacing 16
                ]
                <| [ row
                    [ width fill
                    , spacing 10
                    ]
                    [ materialText
                        [ width <| fillPortion 2
                        , Font.alignRight
                        , padding 16
                        ]
                        "Join/Create Game"
                        Theme.H5
                        theme
                    , nonEl
                        [ width <| fillPortion 1
                        , padding 16 -- emulate button padding
                        ]
                    ]
                , row
                    [ width fill
                    , spacing 10
                    ]
                    [ el
                        [ width <| fillPortion 2 ]
                        <| TextField.text
                            [ width fill
                            ]
                            { label = "Game id"
                            , hideLabel = False
                            , type_ = TextField.Outlined
                            , color = Theme.Primary
                            , text = model.gameId
                            , onChange = GameId
                            , state = TextField.Idle
                            , errorText = Nothing
                            , helperText = Nothing
                            }
                            theme
                    , Button.outlined
                        [ alignLeft
                        , width <| fillPortion 1
                        ]
                        { icon = Nothing
                        , color = Theme.Primary
                        , text = "Join Game"
                        , onPress = Just JoinGame
                        , disabled = False
                        }
                        theme
                    ]
                , row
                    [ width fill
                    , spacing 10
                    ]
                    [ nonEl [ width <| fillPortion 2 ]
                    ,Button.outlined
                        [ alignLeft
                        , width <| fillPortion 1
                        ]
                        { icon = Nothing
                        , color = Theme.Primary
                        , text = "Create Game"
                        , onPress = Just NewTTTGame
                        , disabled = False
                        }
                        theme
                    ]
                ] ++ case model.error of
                    Just error ->
                        [ errorCard (error |> toErrorMsg) theme ]

                    Nothing ->
                        []

    }


toErrorMsg : JoinError -> String
toErrorMsg joinError =
    case joinError of
        LobbyError lobbyError ->
            case lobbyError of
                EnterLobby.LobbyFull maxPlayers ->
                    "The Game is full. There are a maximum of " ++ String.fromInt maxPlayers ++ " players allowed."

                EnterLobby.GameAlreadyStarted id ->
                    "The Game " ++ id ++ " has already started."

                EnterLobby.NoSuchGame id ->
                    "The Game " ++ id ++ " does not exist."

        ConnectionError error ->
            "Failed to reach the server."


errorCard : String -> Theme.Theme a -> Element Msg
errorCard errorMsg theme =
    row
        [ width fill
        , spacing 10
        ]
        [ el
            [ Background.color theme.color.error
            , Border.rounded 6
            , padding 16
            , width fill
            ]
            <| paragraph
                [ Font.color theme.color.onError
                , centerX
                ]
                [ materialText [ centerX ] errorMsg Theme.Body1 theme
                ]
        ]



-- SUBSCRIPTIONS


subscriptions : Model -> Sub Msg
subscriptions model =
    Sub.none
