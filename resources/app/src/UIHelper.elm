module UIHelper exposing (..)

import Element exposing (..)
import Element.Font as Font
import Html exposing (Html)
import MaterialUI.Theme as Theme exposing (Theme)
import Element.Background as Background



materialText : List (Element.Attribute msg) -> String -> Theme.FontScale -> Theme.Theme a ->  Element msg
materialText attr displayStr fontScale theme =
    let
        font = Theme.getFont fontScale theme
    in
    el (attr ++ Theme.fontToAttributes font)
        (text <| Theme.applyCase font.fontcase displayStr)


nonEl : List (Element.Attribute msg) -> Element msg
nonEl attributes =
    el attributes none


loading : Theme a -> Element msg
loading theme =
    el
        [ Background.color theme.color.background
        , width fill
        , height fill
        ]
        <| materialText
            [ centerX
            , centerY
            , Font.color theme.color.onBackground
            ]
            "Loading..."
            Theme.Body1
            theme


defaultTopColumn : String -> Theme a -> List (Element msg) -> Html msg
defaultTopColumn title theme rows =
    layout
        [ Background.color theme.color.background
        , Font.color theme.color.onBackground
        ]
        <| column
            [ centerX
            , width (fill |> maximum 900)
            , padding 16
            , spacing 16
            ]
            <| [ row
                [ width fill
                , spacing 10
                ]
                [ materialText
                    [ Font.alignRight
                    , alignRight
                    , width <| fillPortion 2
                    , padding 16
                    ]
                    title
                    Theme.H5
                    theme
                , nonEl
                    [ width <| fillPortion 1
                    , padding 16 -- emulate button padding
                    ]
                ]
            ] ++ rows