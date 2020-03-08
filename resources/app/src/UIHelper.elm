module UIHelper exposing (..)

import Element exposing (..)
import MaterialUI.Theme as Theme



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