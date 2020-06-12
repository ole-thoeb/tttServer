module Page.TTT.Board exposing (Board, Symbol(..), defaultSymbolColor, view, lineFormGameStatus, toCssString)

import Array exposing (Array)
import Color
import Element exposing (..)
import Element.Background as Background
import Element.Events as Events
import Game.Game as GameState
import MaterialUI.Theme as Theme exposing (Theme)
import Page.TTT.SvgSymbol as SvgSymbol
import Session
import Svg exposing (Svg)


type alias Board msg s =
    { line : Maybe ( Int, Int, Int )
    , cells : Array s
    , onClick : Int -> msg
    , symbolView : Maybe s -> List (Svg msg)
    }


type Symbol
    = X
    | O
    | Empty


defaultSymbolColor : Symbol -> Theme.Color Session.CustomColor
defaultSymbolColor symbol =
    case symbol of
        X ->
            Theme.Alternative Session.Player1Color

        O ->
            Theme.Alternative Session.Player2Color

        Empty ->
            Theme.Primary


toCssString : Color -> String
toCssString color =
    Element.toRgb color
        |> Color.fromRgba
        |> Color.toCssString


lineFormGameStatus : GameState.Status -> Maybe ( Int, Int, Int )
lineFormGameStatus status =
    case status of
        GameState.OnGoing ->
            Nothing

        GameState.Draw ->
            Nothing

        GameState.Win _ f1 f2 f3 ->
            Just ( f1, f2, f3 )


view : Theme a -> Board msg s -> Element msg
view theme board =
    column
        [ centerX
        , width (fill |> maximum 500)
        ]
        [ row
            [ spaceEvenly
            , width fill
            ]
            [ boardCell 0 board
            , hLine theme
            , boardCell 1 board
            , hLine theme
            , boardCell 2 board
            ]
        , vLine theme
        , row
            [ spaceEvenly
            , width fill
            ]
            [ boardCell 3 board
            , hLine theme
            , boardCell 4 board
            , hLine theme
            , boardCell 5 board
            ]
        , vLine theme
        , row
            [ spaceEvenly
            , width fill
            ]
            [ boardCell 6 board
            , hLine theme
            , boardCell 7 board
            , hLine theme
            , boardCell 8 board
            ]
        ]


boardCell : Int -> Board msg s -> Element msg
boardCell cellNumber board =
    let
        svgIcon =
            SvgSymbol.toHtml <|
                gameStatusToLine board.line cellNumber
                    ++ (Array.get cellNumber board.cells |> board.symbolView)
    in
    el
        [ width fill
        , Events.onClick (board.onClick cellNumber)
        ]
        (html svgIcon)


gameStatusToLine : Maybe ( Int, Int, Int ) -> Int -> List (Svg msg)
gameStatusToLine line cellNumber =
    case line of
        Nothing ->
            []

        Just ( f1, f2, f3 ) ->
            if Debug.log "cNum" cellNumber == f1 || cellNumber == f2 || cellNumber == f3 then
                if f1 + 1 == f2 && f2 + 1 == f3 then
                    [ SvgSymbol.lineHor "white" ]

                else if f1 + 3 == f2 && f2 + 3 == f3 then
                    [ SvgSymbol.lineVert "white" ]

                else if f1 + 4 == f2 && f2 + 4 == f3 then
                    [ SvgSymbol.lineDiaTopBot "white" ]

                else if f1 + 2 == f2 && f2 + 2 == f3 then
                    [ SvgSymbol.lineDiaBotTop "white" ]

                else
                    Debug.log "no sequence match" []

            else
                Debug.log "no field match" []


vLine : Theme a -> Element msg
vLine theme =
    el
        [ width fill
        , height <| px 2
        , Background.color <| Theme.setAlpha 0.3 theme.color.onBackground
        ]
        none


hLine : Theme a -> Element msg
hLine theme =
    el
        [ width <| px 2
        , height fill
        , Background.color <| Theme.setAlpha 0.3 theme.color.onBackground
        ]
        none
