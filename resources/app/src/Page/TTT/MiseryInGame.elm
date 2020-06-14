module Page.TTT.MiseryInGame exposing (Model, Msg, init, toSession, update, updateFromWebsocket, view)

import Browser.Navigation as Nav
import Element exposing (..)
import Element.Background as Background
import Element.Font as Font
import Endpoint
import Game
import Game.MiseryGame as MiseryGame exposing (CellState, MiseryGame)
import Html
import MaterialUI.Theme as Theme exposing (Theme)
import Page.TTT.Board as Board
import Page.TTT.GameUI as GameUI
import Page.TTT.SvgSymbol as SvgSymbol
import ServerRequest.MiseryInGame as InGameRequest
import ServerResponse.MiseryInGame as InGameResponse
import Session exposing (Session)
import Websocket



-- MODEL


type alias Model =
    { session : Session
    , game : MiseryGame
    }


init : Session -> MiseryGame -> ( Model, Cmd Msg )
init session game =
    ( { session = session
      , game = game
      }
    , Websocket.connect Game.Misery game.gameId
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
            ( model, Nav.pushUrl navKey <| Endpoint.rematch Game.Misery game.gameId )

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
    in
    { title = "TTTGame"
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
                    , symbolView = \maybeSymbol -> case maybeSymbol of
                         Just MiseryGame.X ->
                             SvgSymbol.circle (Theme.getColor (Theme.Alternative Session.Player1Color) theme
                                |> Board.toCssString)

                         _ ->
                             SvgSymbol.empty
                    }
                ]
    }

