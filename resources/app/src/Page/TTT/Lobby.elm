module Page.TTT.Lobby exposing (Model, init, toSession, Msg, update, view)

import Browser.Navigation as Nav
import ClipBoard
import Game.Lobby as Lobby exposing (Lobby)
import Http
import Monocle.Lens as Lens exposing (Lens)
import ServerRequest.InLobby as InLobby
import ServerResponse.EnterLobby as EnterLobby
import Session exposing (Session)
import UIHelper exposing (..)

import Element exposing (..)
import Element.Font as Font
import Element.Background as Background
import Element.Border as Border

import MaterialUI.Button as Button
import MaterialUI.TextField as TextField
import MaterialUI.Theme as Theme

import Html
import Url.Builder
import Websocket



-- MODEL


type alias Model =
    { session: Session
    , lobby: Lobby
    }


init : Session -> Lobby -> ( Model, Cmd Msg )
init session lobby =
    ( { session = session
    , lobby = lobby
    }
    , Websocket.connect lobby.gameId
    )


toSession : Model -> Session
toSession model =
    model.session


-- UPDATE


type Msg
    = Name String
    | Ready
    | CopyGameId


update: Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    let
        lobby = model.lobby
    in
    case msg of
        Name name ->
            let
                newLobby = Lobby.playerNameOfLobby.set name lobby
            in
            ( { model | lobby = newLobby }, InLobby.nameChangedMsg newLobby.gameId newLobby.playerMe |> Websocket.send )

        Ready ->
            let
                newLobby = Lens.modify Lobby.playerReadyOfLobby not lobby
            in
            ( { model | lobby = newLobby }, InLobby.readyChangedMsg newLobby.gameId newLobby.playerMe |> Websocket.send )

        CopyGameId ->
            let
                a = Debug.log "copying gameId" ""
            in
            ( model, ClipBoard.copyToClipBoard <| Url.Builder.absolute [ "game", model.lobby.gameId ] [] )



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
                    [ column
                        [ width <| fillPortion 2
                        , padding 16
                        , spacing 8
                        ]
                        [ materialText
                            [ Font.alignRight
                            , alignRight
                            ]
                            "Lobby"
                            Theme.H5
                            theme
                        , materialText
                            [ Font.alignRight
                            , alignRight
                            ]
                            model.lobby.gameId
                            Theme.Subtitle2
                            theme
                        ]
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
                            { label = "Name"
                            , hideLabel = False
                            , type_ = TextField.Outlined
                            , color = Theme.Primary
                            , text = model.lobby.playerMe.name
                            , onChange = Name
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
                        , text = "Invite Link"
                        , onPress = Just CopyGameId
                        , disabled = False
                        }
                        theme
                    ]
                , row
                    [ width fill
                    , spacing 10
                    ]
                    [ nonEl [ width <| fillPortion 2 ]
                    , let
                        bText = if model.lobby.playerMe.isReady then "Ready" else "Not Ready"
                    in
                    Button.outlined
                        [ alignLeft
                        , width <| fillPortion 1
                        ]
                        { icon = Nothing
                        , color = Theme.Primary
                        , text = bText
                        , onPress = Just Ready
                        , disabled = False
                        }
                        theme
                    ]
                ]
    }