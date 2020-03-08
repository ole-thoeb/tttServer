module Session exposing (Session, navKey, fromKey, theme)

import Browser.Navigation as Nav
import MaterialUI.Theme as Theme exposing (Theme)
import MaterialUI.Themes.Dark as Dark

type Session =
    Guest Nav.Key (Theme ())


navKey : Session -> Nav.Key
navKey session =
    case session of
        Guest key _ ->
            key


theme : Session -> Theme ()
theme session =
    case session of
        Guest _ t ->
            t


fromKey : Nav.Key -> Session
fromKey key =
    Guest key Dark.theme--Theme.defaultTheme
