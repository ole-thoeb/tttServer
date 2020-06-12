module Page.TTT.StoplightInGame exposing (Model, Msg, init, toSession, update, updateFromWebsocket, view)

import Browser.Navigation as Nav
import Element exposing (..)
import Element.Background as Background
import Element.Border as Border
import Element.Font as Font
import Endpoint
import Game
import Game.Game as GameState
import Game.GamePlayer as GamePlayer
import Game.StoplightGame as StoplightGame exposing (CellState, StoplightGame)
import Html
import MaterialUI.Button as Button
import MaterialUI.Theme as Theme exposing (Theme)
import Page.TTT.Board as Board
import Page.TTT.SvgSymbol as SvgSymbol
import ServerRequest.StoplightInGame as InGameRequest
import ServerResponse.StoplightInGame as InGameResponse
import Session exposing (Session)
import UIHelper exposing (materialText)
import Websocket



-- MODEL


type alias Model =
    { session : Session
    , game : StoplightGame
    }


init : Session -> StoplightGame -> ( Model, Cmd Msg )
init session game =
    ( { session = session
      , game = game
      }
    , Websocket.connect Game.Stoplight game.gameId
    )


toSession : Model -> Session
toSession =
    .session



-- UPDATE


type Msg
    = CellClicked Int
    | Rematch
    | Leave


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    let
        game =
            model.game

        navKey =
            model |> toSession |> Session.navKey
    in
    case msg of
        CellClicked index ->
            ( model, Websocket.send (InGameRequest.setPiece game.gameId game.playerMe.id index) )

        Rematch ->
            ( model, Nav.pushUrl navKey <| Endpoint.rematch Game.Stoplight game.gameId )

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


view : Model -> { title : String, body : Html.Html Msg }
view model =
    let
        theme =
            model |> toSession |> Session.theme

        game =
            model.game

        getCssColor color =
            theme
                |> Theme.getColor color
                |> Board.toCssString
    in
    { title = "Stoplights"
    , body =
        layout
            [ Background.color theme.color.background
            , Font.color theme.color.onBackground
            ]
        <|
            column
                [ centerX
                , width (fill |> maximum 900)
                , padding 16
                , spacing 16
                ]
                [ header model
                , Board.view theme
                    { onClick = CellClicked
                    , cells = game.board
                    , line = Board.lineFormGameStatus game.status
                    , symbolView = \maybeSymbol ->
                        case maybeSymbol of
                            Just StoplightGame.Green ->
                                SvgSymbol.circle <| getCssColor (Theme.Alternative Session.GreenColor)

                            Just StoplightGame.Yellow ->
                                SvgSymbol.circle <| getCssColor (Theme.Alternative Session.YellowColor)

                            Just StoplightGame.Red ->
                                SvgSymbol.circle <| getCssColor (Theme.Alternative Session.RedColor)

                            _ -> SvgSymbol.empty
                    }
                ]
    }


header : Model -> Element Msg
header model =
    let
        game =
            model.game

        theme =
            model |> toSession |> Session.theme
    in
    case game.status of
        GameState.OnGoing ->
            row
                [ spaceEvenly
                , width fill
                ]
                [ playerHeader theme game.playerMe game.meTurn Left
                , playerHeader theme game.opponent (not game.meTurn) Right
                ]

        GameState.Draw ->
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

        GameState.Win winner _ _ _ ->
            row
                [ width fill
                , spacing 8
                ]
                [ materialText
                    [ Font.alignLeft
                    , width <| fillPortion 2
                    ]
                    (if game.playerMe.playerRef == winner then
                        "Victory"

                     else
                        "Defeat"
                    )
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


playerHeader :
    Theme Session.CustomColor
    ->
        { player
            | name : String
            , playerRef : GamePlayer.PlayerRef
        }
    -> Bool
    -> Alignment
    -> Element Msg
playerHeader theme player highlight alignment =
    let
        playerColor =
            Theme.Alternative <|
                case player.playerRef of
                    GamePlayer.P1 ->
                        Session.Player1Color

                    GamePlayer.P2 ->
                        Session.Player2Color

        borderColor =
            if highlight then
                Theme.setAlpha 0.6 (Theme.getColor playerColor theme)

            else
                Theme.setAlpha 0.3 (theme.color.onBackground)


        ( fontAlign, align ) =
            case alignment of
                Left ->
                    ( Font.alignLeft, alignLeft )

                Right ->
                    ( Font.alignRight, alignRight )
    in
    el
        [ width shrink
        , Border.color <| Theme.setAlpha 0.3 borderColor
        , Border.width 2
        , Border.rounded 6
        , padding 8
        ]
    <|
        materialText
            [ fontAlign
            , Font.color theme.color.onBackground
            , align
            ]
            player.name
            Theme.Body1
            theme
