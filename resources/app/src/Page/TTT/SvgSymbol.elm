module Page.TTT.SvgSymbol exposing (..)


import Html exposing (Html)
import Svg exposing (..)
import Svg.Attributes exposing (..)


cross : String -> Html msg
cross color =
    svg
        [ width "100%"
        , preserveAspectRatio "xMinYMin"
        , viewBox "0 0 120 120"
        ]
        [ line
            [ stroke color
            , strokeWidth "2"
            , x1 "10"
            , y1 "10"
            , x2 "110"
            , y2 "110"
            ]
            []
        , line
            [ stroke color
            , strokeWidth "2"
            , x1 "110"
            , y1 "10"
            , x2 "10"
            , y2 "110"
            ]
            []
        ]

circle : String -> Html msg
circle color =
    svg
        [ width "100%"
        , preserveAspectRatio "xMinYMin"
        , viewBox "0 0 120 120"
        ]
        [ Svg.circle
            [ cx "60"
            , cy "60"
            , r "50"
            , stroke color
            , strokeWidth "2"
            , fill "none"
            ]
            []
        ]

empty : Html msg
empty =
     svg
        [ width "100%"
        , preserveAspectRatio "xMinYMin"
        , viewBox "0 0 120 120"
        ]
        []