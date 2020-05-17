module Page.TTT.TTTInGame exposing (Model, init, toSession, Msg, update, updateFromWebsocket, view)


import Array exposing (Array)
import Browser.Navigation as Nav
import Element exposing (..)
import Element.Background as Background
import Element.Border as Border
import Element.Font as Font
import Endpoint
import Game
import Game.Game as GameStatus
import Game.GamePlayer as GamePlayer
import Game.TTTGame as TTTGame exposing (CellState, TTTGame)
import Game.TTTGamePlayer as TTTGamePlayer exposing (Symbol)
import Html
import MaterialUI.Button as Button
import MaterialUI.Icon as Icon exposing (Icon)
import MaterialUI.Icons.Navigation as Navigation
import MaterialUI.Icons.Toggle as Toggle
import MaterialUI.Theme as Theme exposing (Theme)
import Page.TTT.Board as Board
import ServerRequest.TTTInGame as InGameRequest
import ServerResponse.TTTInGame as InGameResponse
import Session exposing (Session)
import UIHelper exposing (materialText)
import Websocket


-- MODEL


type alias Model =
    { session: Session
    , game: TTTGame
    }


init : Session -> TTTGame -> ( Model, Cmd Msg )
init session game =
    ( { session = session
    , game = game
    }
    , Websocket.connect Game.TicTacToe game.gameId
    )


toSession : Model -> Session
toSession =
    .session


-- UPDATE


type Msg
    = CellClicked Int
    | Rematch
    | Leave


update: Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    let
        game = model.game
        navKey = model |> toSession |> Session.navKey
    in
    case msg of
        CellClicked index ->
            ( model, Websocket.send (InGameRequest.setPiece game.gameId game.playerMe.id index) )

        Rematch ->
            ( model, Nav.pushUrl navKey <| Endpoint.rematch Game.TicTacToe game.gameId )

        Leave ->
            ( model
            , Nav.pushUrl navKey Endpoint.home
            )


updateFromWebsocket : InGameResponse.Response -> Model -> ( Model, Cmd Msg )
updateFromWebsocket response model =
    case response of
        InGameResponse.PlayerDisc discPlayerName ->
            ( model, Cmd.none )

        InGameResponse.GameState tttGame ->
            ( { model | game = tttGame }, Cmd.none )


-- VIEW


view : Model -> { title: String, body: Html.Html Msg }
view model =
    let
        theme = model |> toSession |> Session.theme
        game = model.game
    in
    { title = "TTTGame"
    , body =
        layout
            [ Background.color theme.color.background
            , Font.color theme.color.onBackground
            ] <| column
                [ centerX
                , width (fill |> maximum 900)
                , padding 16
                , spacing 16
                ]
                [ header model
                , Board.view theme
                    { onClick = CellClicked
                    , cells = game.board |> Array.map (\symbol ->
                        case symbol of
                            TTTGame.X -> Board.X
                            TTTGame.O -> Board.O
                            TTTGame.Empty -> Board.Empty
                    )
                    , line = case game.status of
                        GameStatus.OnGoing -> Nothing
                        GameStatus.Draw -> Nothing
                        GameStatus.Win _ f1 f2 f3 -> Just ( f1, f2, f3 )
                    , symbolColor = Board.defaultSymbolColor
                    }
                ]
    }


header : Model -> Element Msg
header model =
    let
        game = model.game
        theme = model |> toSession |> Session.theme
    in
    case game.status of
        GameStatus.OnGoing ->
            row
                [ spaceEvenly
                , width fill
                ]
                [ (playerHeader theme game.playerMe game.meTurn Left)
                , (playerHeader theme game.opponent (not game.meTurn) Right)
                ]

        GameStatus.Draw ->
            row
                [ width fill
                , spacing 8
                ]
                [ materialText
                    [ Font.alignLeft
                    , width <| fillPortion 2
                    ]
                    "Draw"
                    Theme.H3
                    theme
                , headerButtonColumn theme
                ]

        GameStatus.Win winner _ _ _ ->
            row
                [ width fill
                , spacing 8
                ]
                [ materialText
                    [ Font.alignLeft
                    , width <| fillPortion 2
                    ]
                    (if game.playerMe.playerRef == winner then "Victory" else "Defeat")
                    Theme.H3
                    theme
                , headerButtonColumn theme
                ]


headerButtonColumn : Theme a -> Element Msg
headerButtonColumn theme =
    column
        [ width <| fillPortion 1
        , spacing 8
        ]
        [ Button.outlined
            [ width fill
            ]
            { icon = Nothing
            , color = Theme.Primary
            , text = "Rematch"
            , onPress = Just Rematch
            , disabled = False
            }
            theme
        , Button.outlined
            [ width fill
            ]
            { icon = Nothing
            , color = Theme.Primary
            , text = "Leave"
            , onPress = Just Leave
            , disabled = False
            }
            theme
        ]


type Alignment
    = Left
    | Right


playerHeader : Theme Session.CustomColor  ->
    { player
    | name : String
    --, color : String
    , symbol : Symbol
    , playerRef : GamePlayer.PlayerRef
    } ->
    Bool ->
    Alignment ->
    Element Msg
playerHeader theme player highlight alignment =
    let
        playerColor = Theme.Alternative <| case player.playerRef of
                GamePlayer.P1 -> Session.Player1Color
                GamePlayer.P2 -> Session.Player2Color


        borderColor = if highlight then Theme.getColor playerColor theme else theme.color.onBackground
        symbolIcon = case player.symbol of
            TTTGamePlayer.X ->
                Navigation.close

            TTTGamePlayer.O ->
                Toggle.radio_button_unchecked

        (fontAlign, align) = case alignment of
            Left ->
                ( Font.alignLeft, alignLeft )

            Right ->
                ( Font.alignRight, alignRight )
    in
    row
        [ width shrink
        , Border.color <| Theme.setAlpha 0.3 borderColor
        , Border.width 2
        , Border.rounded 6
        , spacing 8
        , padding 8
        ]
        [ materialText
            [ fontAlign
            , Font.color theme.color.onBackground
            , align
            ]
            player.name
            Theme.Body1
            theme
        , Icon.view theme playerColor 20 symbolIcon
        ]

