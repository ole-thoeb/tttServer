module MaterialUI.Internal.Message exposing (Msg(..))

import MaterialUI.Internal.Component exposing (Index)
import MaterialUI.Internal.Icon.Model as Icon
import MaterialUI.Internal.Select.Model as Select
import MaterialUI.Internal.TextField.Model as TextField
import MaterialUI.Internal.Tooltip.Model as Tooltip
import MaterialUI.Internal.Snackbar.Model as Snackbar


type Msg
    = TextFieldMsg Index TextField.Msg
    | IconMsg Index Icon.Msg
    | TooltipMsg Index Tooltip.Msg
    | SnackbarMsg Index Snackbar.Msg
    | SelectMsg Index Select.Msg