module Page.TTT.StoplightInGame exposing (Model, Msg, init, toSession, update, updateFromWebsocket, view)

import Browser.Navigation as Nav
import Element exposing (..)
import Element.Background as Background
import Element.Font as Font
import Endpoint
import Game
import Game.StoplightGame as StoplightGame exposing (CellState, StoplightGame)
import Html
import MaterialUI.Theme as Theme exposing (Theme)
import Page.TTT.Board as Board
import Page.TTT.GameUI as GameUI
import Page.TTT.SvgSymbol as SvgSymbol
import ServerRequest.StoplightInGame as InGameRequest
import ServerResponse.StoplightInGame as InGameResponse
import Session exposing (Session)
import Websocket



-- MODEL


type alias Model =
    { session : Session
    , game : StoplightGame
    }


init : Session -> StoplightGame -> ( Model, Cmd Msg )
init session game =
    ( { session = session
      , game = game
      }
    , Websocket.connect Game.Stoplight game.gameId
    )


toSession : Model -> Session
toSession =
    .session



-- UPDATE


type Msg
    = CellClicked Int
    | Rematch
    | Leave


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    let
        game =
            model.game

        navKey =
            model |> toSession |> Session.navKey
    in
    case msg of
        CellClicked index ->
            ( model, Websocket.send (InGameRequest.setPiece game.gameId game.playerMe.id index) )

        Rematch ->
            ( model, Nav.pushUrl navKey <| Endpoint.rematch Game.Stoplight game.gameId )

        Leave ->
            ( model
            , Nav.pushUrl navKey Endpoint.home
            )


updateFromWebsocket : InGameResponse.Response -> Model -> ( Model, Cmd Msg )
updateFromWebsocket response model =
    case response of
        InGameResponse.PlayerDisc discPlayerName ->
            ( model, Cmd.none )

        InGameResponse.GameState tttGame ->
            ( { model | game = tttGame }, Cmd.none )



-- VIEW


view : Model -> { title : String, body : Html.Html Msg }
view model =
    let
        theme =
            model |> toSession |> Session.theme

        game =
            model.game

        getCssColor color =
            theme
                |> Theme.getColor color
                |> Board.toCssString
    in
    { title = "Stoplights"
    , body =
        layout
            [ Background.color theme.color.background
            , Font.color theme.color.onBackground
            ]
        <|
            column
                [ centerX
                , width (fill |> maximum 900)
                , padding 16
                , spacing 16
                ]
                [ GameUI.twoPlayerHeader
                     { rematch = Rematch
                     , leave = Leave
                     , playerMe = game.playerMe
                     , opponent = game.opponent
                     , game = game
                     }
                  theme
                , Board.view theme
                    { onClick = CellClicked
                    , cells = game.board
                    , line = Board.lineFormGameStatus game.status
                    , symbolView = \maybeSymbol ->
                        case maybeSymbol of
                            Just StoplightGame.Green ->
                                SvgSymbol.circle <| getCssColor (Theme.Alternative Session.GreenColor)

                            Just StoplightGame.Yellow ->
                                SvgSymbol.circle <| getCssColor (Theme.Alternative Session.YellowColor)

                            Just StoplightGame.Red ->
                                SvgSymbol.circle <| getCssColor (Theme.Alternative Session.RedColor)

                            _ -> SvgSymbol.empty
                    }
                ]
    }
