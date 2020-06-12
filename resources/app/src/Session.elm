module Session exposing (Session, navKey, fromKey, theme, CustomColor(..))

import Browser.Navigation as Nav
import Element
import MaterialUI.MaterilaUI as MaterialUI
import MaterialUI.Theme exposing (Theme)
import MaterialUI.Themes.Default as DefaultTheme

type CustomColor
    = Player1Color
    | Player2Color
    | GreenColor
    | YellowColor
    | RedColor


type Session =
    Guest Nav.Key (Theme CustomColor)


navKey : Session -> Nav.Key
navKey session =
    case session of
        Guest key _ ->
            key


theme : Session -> Theme CustomColor
theme session =
    case session of
        Guest _ t ->
            t


fromKey : Nav.Key -> Session
fromKey key =
    Guest key defaultDarkTheme--Theme.defaultTheme


defaultDarkTheme : Theme CustomColor
defaultDarkTheme =
    let
        baseTheme = DefaultTheme.dark
        baseColor = baseTheme.color
    in
    { baseTheme
    | color =
        { baseColor
        | primary = Element.rgb255 102 187 106
        , primaryVariant = Element.rgb255 152 238 153
        , secondary = Element.rgb255 255 202 40
        , secondaryVariant = Element.rgb255 255 253 97
        , alternative =
            [ ( Player1Color, Element.rgb255 236 64 122 )
            , ( Player2Color, Element.rgb255 92 107 192 )
            , ( GreenColor, Element.rgb255 102 187 106 )
            , ( YellowColor, Element.rgb255 220 210 111 )
            , ( RedColor, Element.rgb255 236 64 122 )
            ]
        }
    }