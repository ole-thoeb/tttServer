module Page.TTT.Lobby exposing (Model, init, toSession, Msg, update, view, updateFromWebsocket)

import ClipBoard
import Element.Events as Events
import Game.Lobby as Lobby exposing (Lobby)
import Game.LobbyPlayer exposing (Player)
import MaterialUI.Icons.Content as Content
import MaterialUI.Icon as Icon
import Monocle.Lens as Lens exposing (Lens)
import ServerRequest.InLobby as InLobbyRequest
import ServerResponse.InLobby as InLobbyResponse
import Session exposing (Session)
import UIHelper exposing (..)

import Element exposing (..)
import Element.Font as Font
import Element.Background as Background
import Element.Border as Border

import MaterialUI.Button as Button
import MaterialUI.TextField as TextField
import MaterialUI.Theme as Theme exposing (Theme)

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
    | AddBot
    | CopyGameId


update: Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    let
        lobby = model.lobby
    in
    case msg of
        Name name ->
            let
                limitedName = String.slice 0 19 name
                newLobby = Lobby.playerNameOfLobby.set limitedName lobby
            in
            ( { model | lobby = newLobby }, InLobbyRequest.nameChangedMsg newLobby.gameId newLobby.playerMe |> Websocket.send )

        Ready ->
            let
                newLobby = Lens.modify Lobby.playerReadyOfLobby not lobby
            in
            ( { model | lobby = newLobby }, InLobbyRequest.readyChangedMsg newLobby.gameId newLobby.playerMe |> Websocket.send )

        AddBot ->
            ( model, InLobbyRequest.addBotMsg lobby.gameId lobby.playerMe |> Websocket.send )

        CopyGameId ->
            let
                a = Debug.log "copying gameId" ""
            in
            ( model, ClipBoard.copyToClipBoard <| Url.Builder.absolute [ "game", model.lobby.gameId ] [] )


updateFromWebsocket : InLobbyResponse.Response -> Model -> ( Model, Cmd Msg )
updateFromWebsocket response model =
    case response of
        InLobbyResponse.LobbyState lobby ->
            let
                newLobby = { lobby | playerMe = model.lobby.playerMe }
            in
            ( { model | lobby = newLobby}, Cmd.none )


-- VIEW


view : Model -> { title: String, body: Html.Html Msg }
view model =
    let
        theme = model |> toSession |> Session.theme
    in
    { title = "Home"
    , body = defaultTopColumn "Lobby" theme
        <| [ row
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
            , let
                bText = if model.lobby.playerMe.isReady then "Not Ready" else "Ready"
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
        , row
            [ width fill
            , spacing 10
            ]
            [ row
                [ width <| fillPortion 2
                , padding 4
                ]
                [ column
                    [ spacing 4
                    , width fill
                    ]
                    [ materialText
                        [ Font.alignLeft
                        , alignLeft
                        ]
                        "Game Id"
                        Theme.Overline
                        theme
                    , materialText
                        [ Font.alignLeft
                        , alignLeft
                        ]
                        model.lobby.gameId
                        Theme.Body1
                        theme
                    ]
                , Icon.button
                    [ Events.onClick CopyGameId ]
                    theme Theme.OnBackground 24 Content.content_copy
                ]
            , Button.outlined
                [ alignLeft
                , width <| fillPortion 1
                ]
                { icon = Nothing
                , color = Theme.Primary
                , text = "Add Bot"
                , onPress = Just AddBot
                , disabled = False
                }
                theme
            ]
        ] ++ (Lobby.allPlayers model.lobby |> List.map (playerRow theme))
    }


playerRow : Theme a  -> Player -> Element Msg
playerRow theme player =
    row
        [ width fill
        , paddingEach { top = 4, bottom = 4, left = 0, right = 0 }
        , Border.color <| Theme.setAlpha 0.3 theme.color.onBackground
        , Border.width 2
        , Border.rounded 6
        ]
        [ materialText
            [ Font.alignLeft
            , Font.color theme.color.onBackground
            , alignLeft
            , padding 10
            ]
            player.name
            Theme.Body1
            theme
        , materialText
            [ Font.alignRight
            , Font.color theme.color.onBackground
            , Font.italic
            , alignRight
            , padding 10
            ]
            (if player.isReady then "Ready" else "Not Ready")
            Theme.Body1
            theme
        ]