module UIHelper exposing (..)

import Element exposing (..)
import Element.Font as Font
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
