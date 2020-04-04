module MaterialUI.Icon exposing (Icon, makeIcon, view, button)

import Color exposing (Color)
import Element exposing (Element)
import Element.Background as Background
import Element.Border as Border
import Html.Attributes
import MaterialUI.Icons.Internal as Internal
import MaterialUI.Theme as Theme exposing (Theme)
import Svg exposing (Svg)


type Icon msg
    = Icon (Internal.Icon msg)


makeIcon : Internal.Icon msg -> Icon msg
makeIcon =
    Icon


view : Theme a -> Theme.Color a -> Int -> Icon msg -> Element msg
view theme colorkey size (Icon icon) =
    let
        color =
            Theme.getColor colorkey theme
                |> Element.toRgb
                |> Color.fromRgba
    in
    Element.el
        [ Element.width <| Element.px size
        , Element.height <| Element.px size
        ]
        (Element.html
            (Svg.svg []
                [ icon color size ]
            )
        )

button : List (Element.Attribute msg) ->  Theme a -> Theme.Color a -> Int -> Icon msg -> Element msg
button attributes theme colorKey size (Icon icon) =
    let
        color = Theme.getColor colorKey theme
        iconColor = color
            |> Element.toRgb
            |> Color.fromRgba

        padding = 8
        attr = attributes ++
            [ Element.width <| Element.px (size + 2 * padding)
            , Element.height <| Element.px (size + 2 * padding)
            , Element.padding padding
            , Border.rounded 50
            , Element.mouseDown
                [ Background.color (color |> Theme.setAlpha 0.2)
                ]
            , Element.focused
                [ Background.color (color |> Theme.setAlpha 0.15)
                ]
            , Element.mouseOver
                [ Background.color (color |> Theme.setAlpha 0.1)
                ]
            ]
    in
    Element.el attr
        (Element.html
            (Svg.svg []
                [ icon iconColor size ]
            )
        )