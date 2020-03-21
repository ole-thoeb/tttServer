module Page.TTT.InGame exposing (..)


import Array exposing (Array)
import Element exposing (..)
import Element.Background as Background
import Element.Border as Border
import Element.Events as Events
import Element.Font as Font
import Element.Input as Input
import Game.TTTGame as TTTGame exposing (CellState, TTTGame)
import Game.TTTGamePlayer as TTTGamePlayer exposing (Symbol)
import Html
import MaterialUI.Icon as Icon exposing (Icon)
import MaterialUI.Icons.Navigation as Navigation
import MaterialUI.Icons.Toggle as Toggle
import MaterialUI.Theme as Theme exposing (Theme)
import Page.TTT.SvgSymbol as SvgSymbol
import ServerRequest.InGame as InGameRequest
import ServerResponse.InGame as InGameResponse
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
    , Websocket.connect game.gameId
    )


toSession : Model -> Session
toSession =
    .session


-- UPDATE


type Msg
    = CellClicked Int


update: Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    let
        game = model.game
    in
    case msg of
        CellClicked index ->
            ( model, Websocket.send (InGameRequest.setPiece game.gameId game.playerMe.id index) )


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
                [ row
                   [ spaceEvenly
                   , width fill
                   ]
                   [ (playerHeader theme game.playerMe game.meTurn Left)
                   , (playerHeader theme game.opponent (not game.meTurn) Right)
                   ]
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


type Alignment
    = Left
    | Right


playerHeader : Theme a  ->
    { player
    | name : String
    , color : String
    , symbol : Symbol
    } ->
    Bool ->
    Alignment ->
    Element Msg
playerHeader theme player highlight alignment =
    let
        borderColor = if highlight then theme.color.secondary else theme.color.onBackground
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
        , Icon.view theme Theme.SecondaryVariant 20 symbolIcon
        ]


boardCell : Theme a -> Int -> TTTGame -> Element Msg
boardCell theme cellNumber game =
    let
        board = game.board
        svgIcon = case Array.get cellNumber board of
            Just TTTGame.X ->
                SvgSymbol.cross "red"

            Just TTTGame.O ->
                SvgSymbol.circle "blue"

            _ -> SvgSymbol.empty
    in
    el
        [ width fill
        , Events.onClick (CellClicked cellNumber)
        ]
        (html svgIcon)


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
