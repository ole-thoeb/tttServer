module Page.NotFound exposing (view, init, Model, toSession, update, Msg)

import Browser.Navigation as Nav
import Endpoint
import Html exposing (Html)
import Session exposing (Session)
import UIHelper exposing (..)

import Element exposing (..)
import Element.Background as Background
import Element.Border as Border
import Element.Font as Font
import MaterialUI.Button as Button

import MaterialUI.Theme as Theme
import Url.Builder


-- MODEL


type alias Model =
    { session: Session
    }


init : Session -> ( Model, Cmd msg )
init session =
    ( { session = session
      }
    , Cmd.none
    )


toSession : Model -> Session
toSession model =
    model.session



-- UPDATE


type Msg
    = HomeClicked


update: Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        HomeClicked ->
            ( model, Nav.pushUrl (Session.navKey (toSession model)) Endpoint.home )


-- VIEW


view : Model -> { title : String, body : Html Msg }
view model =
    let
        theme = model |> toSession |> Session.theme
    in
    { title = "Page Not Found"
    , body =
        layout
            [ Background.color theme.color.background
            , Font.color theme.color.onBackground
            , height fill
            , width fill
            ]
            <| column
                [ centerX
                , width (fill |> maximum 900)
                , padding 16
                , spacing 16
                ]
                <| [ materialText
                    [ centerX
                    , padding 16
                    ]
                    "Page Not Found"
                    Theme.H3
                    theme
                , Button.outlined
                    [ width (fill |> maximum 300)
                    , centerX
                    ]
                    { icon = Nothing
                    , color = Theme.Primary
                    , text = "Take me Home"
                    , onPress = Just HomeClicked
                    , disabled = False
                    }
                    theme
               ]


    }