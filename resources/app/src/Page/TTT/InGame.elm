module Page.TTT.InGame exposing (Model, init, toSession, Msg, update, updateFromWebsocket, view)


import Array exposing (Array)
import Browser.Navigation as Nav
import Color
import Element exposing (..)
import Element.Background as Background
import Element.Border as Border
import Element.Events as Events
import Element.Font as Font
import Endpoint
import Game
import Game.TTTGame as TTTGame exposing (CellState, TTTGame)
import Game.TTTGamePlayer as TTTGamePlayer exposing (Symbol)
import Html
import MaterialUI.Button as Button
import MaterialUI.Icon as Icon exposing (Icon)
import MaterialUI.Icons.Navigation as Navigation
import MaterialUI.Icons.Toggle as Toggle
import MaterialUI.Theme as Theme exposing (Theme)
import Page.TTT.SvgSymbol as SvgSymbol
import ServerRequest.InGame as InGameRequest
import ServerResponse.InGame as InGameResponse
import Session exposing (Session)
import Svg exposing (Svg)
import UIHelper exposing (materialText)
import Url.Builder
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
                , column
                    [ centerX
                    , width (fill |> maximum 500)
                    ]
                    [ row
                        [ spaceEvenly
                        , width fill
                        ]
                        [ boardCell theme 0 game
                        , hLine theme
                        , boardCell theme 1 game
                        , hLine theme
                        , boardCell theme 2 game
                        ]
                    , vLine theme
                    , row
                        [ spaceEvenly
                        , width fill
                        ]
                        [ boardCell theme 3 game
                        , hLine theme
                        , boardCell theme 4 game
                        , hLine theme
                        , boardCell theme 5 game
                        ]
                    , vLine theme
                    , row
                        [ spaceEvenly
                        , width fill
                        ]
                        [ boardCell theme 6 game
                        , hLine theme
                        , boardCell theme 7 game
                        , hLine theme
                        , boardCell theme 8 game
                        ]
                    ]
                ]
    }


header : Model -> Element Msg
header model =
    let
        game = model.game
        theme = model |> toSession |> Session.theme
    in
    case game.status of
        TTTGame.OnGoing ->
            row
                [ spaceEvenly
                , width fill
                ]
                [ (playerHeader theme game.playerMe game.meTurn Left)
                , (playerHeader theme game.opponent (not game.meTurn) Right)
                ]

        TTTGame.Draw ->
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

        TTTGame.Win winner field1 filed2 field3 ->
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
    , color : String
    , symbol : Symbol
    , playerRef : TTTGamePlayer.PlayerRef
    } ->
    Bool ->
    Alignment ->
    Element Msg
playerHeader theme player highlight alignment =
    let
        playerColor = Theme.Alternative <| case player.playerRef of
                TTTGamePlayer.P1 -> Session.Player1Color
                TTTGamePlayer.P2 -> Session.Player2Color


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


boardCell : Theme (Session.CustomColor) -> Int -> TTTGame -> Element Msg
boardCell theme cellNumber game =
    let
        board = game.board
        toCssString color = Element.toRgb color
            |> Color.fromRgba
            |> Color.toCssString

        colorKeyForSymbol symbol = Theme.Alternative <| case  TTTGame.playerOfSymbol game symbol |> .playerRef of
            TTTGamePlayer.P1 -> Session.Player1Color
            TTTGamePlayer.P2 -> Session.Player2Color

        svgIcon = SvgSymbol.toHtml <| gameStatusToLine game.status cellNumber ++ case Array.get cellNumber board of
            Just TTTGame.X ->
               SvgSymbol.cross (Theme.getColor (colorKeyForSymbol TTTGamePlayer.X) theme |> toCssString)

            Just TTTGame.O ->
                SvgSymbol.circle (Theme.getColor (colorKeyForSymbol TTTGamePlayer.O) theme |> toCssString)

            _ -> SvgSymbol.empty
    in
    el
        [ width fill
        , Events.onClick (CellClicked cellNumber)
        ]
        (html svgIcon)


gameStatusToLine : TTTGame.Status -> Int-> List (Svg msg)
gameStatusToLine status cellNumber =
    case status of
        TTTGame.OnGoing -> []
        TTTGame.Draw -> []
        TTTGame.Win _ f1 f2 f3 ->
            if Debug.log "cNum" cellNumber == f1 || cellNumber == f2 || cellNumber == f3 then
                if f1 + 1 == f2 && f2 + 1 == f3 then
                    [ SvgSymbol.lineHor "white" ]
                else if  f1 + 3 == f2 && f2 + 3 == f3 then
                    [ SvgSymbol.lineVert "white" ]
                else if f1 + 4 == f2 && f2 + 4 == f3 then
                    [ SvgSymbol.lineDiaTopBot "white" ]
                else if f1 + 2 == f2 && f2 + 2 == f3 then
                    [ SvgSymbol.lineDiaBotTop "white" ]
                else Debug.log "no sequence match" []
            else Debug.log "no field match" []


vLine : Theme a -> Element Msg
vLine theme =
    el
        [ width fill
        , height <| px 2
        , Background.color <| Theme.setAlpha 0.3 theme.color.onBackground
        ]
        none


hLine : Theme a -> Element Msg
hLine theme =
    el
        [ width <| px 2
        , height fill
        , Background.color <| Theme.setAlpha 0.3 theme.color.onBackground
        ]
        none
