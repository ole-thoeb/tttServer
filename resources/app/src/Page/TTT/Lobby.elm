module Page.TTT.Lobby exposing (Model, init, toSession, Msg, update, view, updateFromWebsocket, subscriptions, getGameMode)

import ClipBoard
import Endpoint
import Game
import Game.Lobby as Lobby exposing (Lobby)
import Game.LobbyPlayer as LobbyPlayer exposing (Player)
import MaterialUI.ColorStateList as ColorStateList
import MaterialUI.Icons.Content as Content
import MaterialUI.Icon as Icon
import MaterialUI.Internal.TextField.Model as TextField
import MaterialUI.MaterilaUI as MaterialUI
import MaterialUI.Select as Select
import MaterialUI.Snackbar as Snackbar
import MaterialUI.TextFieldM as TextField
import Monocle.Lens as Lens exposing (Lens)
import ServerRequest.InLobby as InLobbyRequest
import ServerResponse.InLobby as InLobbyResponse
import Session exposing (Session)
import UIHelper exposing (..)

import Element exposing (..)
import Element.Font as Font
import Element.Border as Border

import MaterialUI.Button as Button
import MaterialUI.Theme as Theme exposing (Theme)

import Html
import Websocket



-- MODEL


type alias Model =
    { session: Session
    , gameMode: Game.Mode
    , lobby: Lobby
    , mui : MaterialUI.Model Session.CustomColor Msg
    }


init : Session -> Game.Mode -> Lobby -> ( Model, Cmd Msg )
init session gameMode lobby =
    ( { session = session
    , gameMode = gameMode
    , lobby = lobby
    , mui = MaterialUI.defaultModel Mui (Session.theme session)
    }
    , Websocket.connect gameMode lobby.gameId
    )


toSession : Model -> Session
toSession =
    .session


getGameMode : Model -> Game.Mode
getGameMode =
    .gameMode

-- UPDATE


type Msg
    = Name String
    | Ready
    | AddBot
    | CopyGameId
    | DifficultySelected String LobbyPlayer.Difficulty
    | Mui MaterialUI.Msg


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
                ( newMui, effects ) = Snackbar.set
                    { text = "Copied Link"
                    , position = Snackbar.leading
                    , duration = Snackbar.short
                    , action = Nothing
                    }
                    "snackbar"
                    model.mui
            in
            ( { model | mui = newMui }
            , Cmd.batch
                [ ClipBoard.copyToClipBoard <| Endpoint.game model.gameMode model.lobby.gameId
                , effects
                ]
            )

        DifficultySelected botId difficulty ->
            ( model, InLobbyRequest.setBotDifficulty lobby.gameId lobby.playerMe botId difficulty |> Websocket.send )

        Mui subMsg ->
            materialUpdate subMsg model


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
                <| TextField.managed model.mui
                    [ width fill
                    ]
                    { index = "nameTf"
                    , label = "Name"
                    , hideLabel = False
                    , type_ = TextField.Outlined
                    , color = Theme.Primary
                    , text = model.lobby.playerMe.name
                    , onChange = Name
                    , errorText = Nothing
                    , helperText = Nothing
                   }
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
                        (Game.idToString model.lobby.gameId)
                        Theme.Body1
                        theme
                    ]
                , Icon.button
                    model.mui
                    []
                    { index = "iconCopy"
                    , icon = Content.content_copy
                    , onClick = CopyGameId
                    , color =
                        { idle = ColorStateList.Color 0.9 Theme.OnBackground
                        , focused = ColorStateList.Color 0.5 Theme.Primary
                        , hovered = ColorStateList.Color 0.9 Theme.Primary
                        , mouseDown = ColorStateList.Color 1 Theme.Primary
                        , disabled = ColorStateList.Color 0.5 Theme.OnBackground
                        }
                    , background = ColorStateList.defaultBackgroundOnBackground
                    , tooltip = "Copy Link"
                    , size = 24
                    }
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
        ] ++ (Lobby.allPlayers model.lobby |> List.map (playerRow model.mui))
        ++ [ Snackbar.view model.mui "snackbar" ]
    }


playerRow : MaterialUI.Model Session.CustomColor Msg -> Player -> Element Msg
playerRow mui player =
    let
        theme = mui.theme
    in
    row
        [ width fill
        , paddingEach { top = 4, bottom = 4, left = 0, right = 0 }
        --, Border.color <| Theme.setAlpha 0.3 theme.color.onBackground
        --, Border.width 2
        --, Border.rounded 6
        , spaceEvenly
        ]
        [ materialText
            [ Font.alignLeft
            , Font.color theme.color.onBackground
            , alignLeft
            , width fill
            , padding 10
            ]
            (LobbyPlayer.name player)
            Theme.Body1
            theme
        , case player of
            LobbyPlayer.Human human ->
                humanRowRight theme human

            LobbyPlayer.Bot bot ->
                botRowRight mui bot
        ]


humanRowRight : Theme a  -> LobbyPlayer.HumanR -> Element Msg
humanRowRight theme player =
    materialText
        [ Font.alignRight
        , Font.color theme.color.onBackground
        , Font.italic
        , alignRight
        , width fill
        , padding 10
        ]
        (if player.isReady then "Ready" else "Not Ready")
        Theme.Body1
        theme


botRowRight : MaterialUI.Model Session.CustomColor Msg -> LobbyPlayer.BotR -> Element Msg
botRowRight mui bot =
    el
        [ padding 10
        , width fill
        ]
        <| Select.outlined
            mui
            [ alignRight
            , width fill
            ]
            { index = "difficultySelect"
            , color = Select.defaultColorPrimary
            , label = "Difficulty"
            , items =
                [ Select.item LobbyPlayer.ChildsPlay
                , Select.item LobbyPlayer.Challenge
                , Select.item LobbyPlayer.Nightmare
                ]
            , toItem = \mode -> case mode of
                LobbyPlayer.ChildsPlay -> { text = "Child's Play" }
                LobbyPlayer.Challenge -> { text = "Challenge" }
                LobbyPlayer.Nightmare -> { text = "Nightmare" }
            , onClick = DifficultySelected bot.id
            , selectedItem = Just bot.difficulty
            }



-- SUBSCRIPTIONS


subscriptions : Model -> Sub Msg
subscriptions model =
    MaterialUI.subscriptions model.mui