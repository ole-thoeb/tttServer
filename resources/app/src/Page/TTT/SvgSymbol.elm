module Page.TTT.SvgSymbol exposing (..)


import Html exposing (Html)
import Svg exposing (..)
import Svg.Attributes exposing (..)


toHtml : List (Svg msg) -> Html msg
toHtml =
    svg
        [ width "100%"
        , preserveAspectRatio "xMinYMin"
        , viewBox "0 0 120 120"
        ]


cross : String -> List (Svg msg)
cross color =
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


circle : String -> List (Svg msg)
circle color =
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


empty : List (Svg msg)
empty =
     []


lineVert : String -> Svg msg
lineVert color =
    line
        [ stroke color
        , strokeWidth "4"
        , x1 "60"
        , y1 "0"
        , x2 "60"
        , y2 "120"
        ]
        []


lineHor : String -> Svg msg
lineHor color =
    line
        [ stroke color
        , strokeWidth "4"
        , x1 "0"
        , y1 "60"
        , x2 "120"
        , y2 "60"
        ]
        []


lineDiaTopBot : String -> Svg msg
lineDiaTopBot color =
    line
        [ stroke color
        , strokeWidth "4"
        , x1 "0"
        , y1 "0"
        , x2 "120"
        , y2 "120"
        ] []


lineDiaBotTop : String -> Svg msg
lineDiaBotTop color =
    line
        [ stroke color
        , strokeWidth "4"
        , x1 "120"
        , y1 "0"
        , x2 "0"
        , y2 "120"
        ] []