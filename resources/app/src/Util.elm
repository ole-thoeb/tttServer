module Util exposing (..)


import Browser
import Html exposing (Html)


updateWith : (subModel -> model) -> (subMsg -> msg) -> ( subModel, Cmd subMsg ) -> ( model, Cmd msg )
updateWith toModel toMsg ( subModel, subCmd ) =
    ( toModel subModel
    , Cmd.map toMsg subCmd
    )


viewPage :  (subMsg -> msg) -> { title: String, body: Html subMsg } -> Browser.Document msg
viewPage toMsg { title, body } =
    { title = title
    , body =
        [ Html.map toMsg body
        ]
    }


dummy : b -> a -> a
dummy b a =
    a