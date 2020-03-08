module ColorMe exposing (primary, secondary, onPrimary, onSecondary, secondaryA, primaryA, surface, onSurface)

import Element

primary : Element.Color
primary =
    darkBlue


primaryA : Float -> Element.Color
primaryA alpha =
    primary |> setAlpha alpha


onPrimary : Element.Color
onPrimary =
    white


secondary : Element.Color
secondary =
    green


secondaryA : Float -> Element.Color
secondaryA alpha =
    secondary |> setAlpha alpha


onSecondary : Element.Color
onSecondary =
    black


surface : Element.Color
surface =
    white


onSurface : Element.Color
onSurface =
    black




-- COLORS


white: Element.Color
white =
    Element.rgb255 255 255 255


black: Element.Color
black =
    Element.rgb255 0 0 0


green: Element.Color
green =
    Element.rgb255 27 193 37


darkBlue: Element.Color
darkBlue =
    Element.rgb255 6 10 106



-- HELPER

setAlpha: Float ->  Element.Color -> Element.Color
setAlpha alpha color =
    let
        rgba = Element.toRgb color
    in
        Element.fromRgb { rgba | alpha = alpha }