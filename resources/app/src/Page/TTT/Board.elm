module Page.TTT.Board exposing (Board, Symbol(..), defaultSymbolColor, view)

import Array exposing (Array)
import Color
import Element exposing (..)
import Element.Background as Background
import Element.Events as Events
import MaterialUI.Theme as Theme exposing (Theme)
import Page.TTT.SvgSymbol as SvgSymbol
import Session
import Svg exposing (Svg)


type alias Board msg a =
    { line : Maybe ( Int, Int, Int )
    , cells : Array Symbol
    , onClick : Int -> msg
    , symbolColor : Symbol -> Theme.Color a
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


view : Theme a -> Board msg a -> Element msg
view theme board =
    column
        [ centerX
        , width (fill |> maximum 500)
        ]
        [ row
            [ spaceEvenly
            , width fill
            ]
            [ boardCell theme 0 board
            , hLine theme
            , boardCell theme 1 board
            , hLine theme
            , boardCell theme 2 board
            ]
        , vLine theme
        , row
            [ spaceEvenly
            , width fill
            ]
            [ boardCell theme 3 board
            , hLine theme
            , boardCell theme 4 board
            , hLine theme
            , boardCell theme 5 board
            ]
        , vLine theme
        , row
            [ spaceEvenly
            , width fill
            ]
            [ boardCell theme 6 board
            , hLine theme
            , boardCell theme 7 board
            , hLine theme
            , boardCell theme 8 board
            ]
        ]


boardCell : Theme a -> Int -> Board msg a -> Element msg
boardCell theme cellNumber board =
    let
        toCssString color =
            Element.toRgb color
                |> Color.fromRgba
                |> Color.toCssString

        svgIcon =
            SvgSymbol.toHtml <|
                gameStatusToLine board.line cellNumber
                    ++ (case Array.get cellNumber board.cells of
                            Just X ->
                                SvgSymbol.cross (Theme.getColor (board.symbolColor X) theme |> toCssString)

                            Just O ->
                                SvgSymbol.circle (Theme.getColor (board.symbolColor O) theme |> toCssString)

                            _ ->
                                SvgSymbol.empty
                       )
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
