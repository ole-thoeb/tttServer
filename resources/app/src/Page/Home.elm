module Page.Home exposing (Msg, view, update, subscriptions, toSession, init, Model, JoinError(..), joinErrorToQueryParam, joinErrorQueryParser)

import Browser.Navigation as Nav
import Endpoint
import Game
import Game.Lobby exposing (Lobby)
import Http
import MaterialUI.Internal.TextField.Model as TextField
import MaterialUI.MaterilaUI as MaterialUI
import MaterialUI.Select as Select
import MaterialUI.TextFieldM as TextField
import ServerResponse.EnterLobby as EnterLobbyResponse exposing (Error(..))
import ServerResponse.InGame as InGameResponse
import ServerResponse.InLobby as InLobbyResponse
import ServerResponse.TTTResponse as TTTResponse
import Session exposing (Session)
import UIHelper exposing (..)

import Element exposing (..)
import Element.Font as Font
import Element.Background as Background
import Element.Border as Border

import MaterialUI.Button as Button
import MaterialUI.Theme as Theme

import Html
import Url.Builder exposing (QueryParameter)
import Url.Parser.Query as Query
import Websocket


-- MODEL


type alias Model =
    { session: Session
    , gameId: String
    , error: Maybe JoinError
    , mode : Game.Mode
    , lobby: Maybe Lobby
    , mui : MaterialUI.Model Session.CustomColor Msg
    }


type JoinError
    = LobbyError EnterLobbyResponse.Error
    | ConnectionError (Maybe Http.Error)



init : Session -> Maybe JoinError -> ( Model, Cmd Msg )
init session maybeError =
    ( { session = session
    , gameId = ""
    , error = maybeError
    , mode = Game.TicTacToe
    , lobby = Nothing
    , mui = MaterialUI.defaultModel Mui (Session.theme session)
    }
    , Websocket.disconnect
    )


toSession : Model -> Session
toSession model =
    model.session


-- UPDATE


type Msg
    = NewTTTGame
    | JoinGame
    | GameId String
    | ServerResponse (Result Http.Error TTTResponse.Response)
    | Mui MaterialUI.Msg
    | ModeSelected Game.Mode


update: Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        NewTTTGame ->
            ( model
            , Http.get
                { url = Endpoint.newGame Game.TicTacToe
                , expect = Http.expectJson (ServerResponse << Result.map TTTResponse.EnterLobbyResponse) EnterLobbyResponse.decoder
                }
            )

        JoinGame ->
            ( model
            , Http.get
                { url = Endpoint.joinGame Game.TicTacToe (Debug.log "join game url" <| Game.idFromString model.gameId)
                , expect = Http.expectJson ServerResponse TTTResponse.decoder
                }
            )

        GameId gameId ->
            ( { model | gameId = gameId }, Cmd.none )

        ServerResponse result ->
            case result of
                Ok response ->
                    let
                        a = Debug.log "response" response
                        navKey = model |> toSession |> Session.navKey
                        navigateToLobby lobby =
                            ( { model | lobby = Just lobby }
                            , Nav.pushUrl navKey
                                <| Endpoint.game Game.TicTacToe lobby.gameId
                            )
                    in
                    case response of
                        TTTResponse.EnterLobbyResponse (EnterLobbyResponse.LobbyState lobby) ->
                            navigateToLobby lobby

                        TTTResponse.EnterLobbyResponse (EnterLobbyResponse.Error error) ->
                            ( { model | error = Just (LobbyError error) }, Cmd.none )

                        TTTResponse.InLobbyResponse (InLobbyResponse.LobbyState lobby) ->
                            navigateToLobby lobby

                        TTTResponse.InGameResponse (InGameResponse.GameState game) ->
                            ( model
                            , Nav.pushUrl navKey
                                <| Endpoint.game Game.TicTacToe game.gameId
                            )

                        TTTResponse.InGameResponse (InGameResponse.PlayerDisc _) ->
                            ( model, Cmd.none )

                Err httpError ->
                    let
                        a = Debug.log "http error" httpError
                    in
                    ( { model | error = Just <| ConnectionError (Just httpError) }, Cmd.none )

        Mui subMsg ->
            materialUpdate subMsg model

        ModeSelected gameMode ->
            ( { model | mode = gameMode }, Cmd.none )


-- VIEW


view : Model -> { title: String, body: Html.Html Msg }
view model =
    let
        theme = model |> toSession |> Session.theme
    in
    { title = "Home"
    , body = defaultTopColumn "Join/Create Game" theme
        <| [ row
            [ width fill
            , spacing 10
            ]
            [ el
                [ width <| fillPortion 2 ]
                <| TextField.managed model.mui
                    [ width fill
                    ]
                    { index = "gameIdTf"
                    , label = "Game id"
                    , hideLabel = False
                    , type_ = TextField.Outlined
                    , color = Theme.Primary
                    , text = model.gameId
                    , onChange = GameId
                    , errorText = Nothing
                    , helperText = Nothing
                    }
            , Button.outlined
                [ alignLeft
                , width <| fillPortion 1
                ]
                { icon = Nothing
                , color = Theme.Primary
                , text = "Join Game"
                , onPress = Just JoinGame
                , disabled = False
                }
                theme
            ]
        , row
            [ width fill
            , spacing 10
            ]
            [ el
                [ width <| fillPortion 2 ]
                <| Select.outlined
                    model.mui
                    [ width fill ]
                    { index = "modeSelect"
                    , color = Select.defaultColorPrimary
                    , label = "Mode"
                    , items =
                        [ Select.item Game.TicTacToe
                        , Select.item Game.Misery
                        ]
                    , toItem = \mode -> case mode of
                        Game.TicTacToe -> { text = "Tic Tac Toe" }
                        Game.Misery -> { text = "Misery" }
                    , onClick = ModeSelected
                    , selectedItem = Just model.mode
                    }
            ,Button.outlined
                [ alignLeft
                , width <| fillPortion 1
                ]
                { icon = Nothing
                , color = Theme.Primary
                , text = "Create Game"
                , onPress = Just NewTTTGame
                , disabled = False
                }
                theme
            ]
        ] ++ case model.error of
            Just error ->
                [ errorCard (error |> toErrorMsg) theme ]

            Nothing ->
                []

    }


toErrorMsg : JoinError -> String
toErrorMsg joinError =
    case joinError of
        LobbyError lobbyError ->
            case lobbyError of
                EnterLobbyResponse.LobbyFull maxPlayers ->
                    "The Game is full. There are a maximum of " ++ String.fromInt maxPlayers ++ " players allowed."

                EnterLobbyResponse.GameAlreadyStarted id ->
                    "The Game " ++ id ++ " has already started."

                EnterLobbyResponse.NoSuchGame id ->
                    "The Game " ++ id ++ " does not exist."

        ConnectionError error ->
            "Failed to reach the server."


errorCard : String -> Theme.Theme a -> Element Msg
errorCard errorMsg theme =
    row
        [ width fill
        , spacing 10
        ]
        [ el
            [ Background.color theme.color.error
            , Border.rounded 6
            , padding 16
            , width fill
            ]
            <| paragraph
                [ Font.color theme.color.onError
                , centerX
                ]
                [ materialText [ centerX ] errorMsg Theme.Body1 theme
                ]
        ]



-- SUBSCRIPTIONS


subscriptions : Model -> Sub Msg
subscriptions model =
    MaterialUI.subscriptions model.mui


-- QUERY HELPER


joinErrorToQueryParam : JoinError -> List QueryParameter
joinErrorToQueryParam error =
    let
        query = Url.Builder.string "lobbyError"
    in
    case error of
        LobbyError (LobbyFull max) ->
            [ query <| "full§" ++ String.fromInt max ]

        LobbyError (GameAlreadyStarted id) ->
            [ query <| "started§" ++ id ]

        LobbyError (NoSuchGame id) ->
            [ query <| "noGame§" ++ id ]

        ConnectionError _ ->
            [ query <| "internal" ]


joinErrorQueryParser : Query.Parser (Maybe JoinError)
joinErrorQueryParser =
    Query.custom "lobbyError" <| \list ->
        case list of
            [ error ] ->
                let
                    parts = String.split "§" error
                in
                case parts of
                    [ "full", max ] ->
                        Maybe.map (LobbyError << LobbyFull) (String.toInt max)

                    [ "started", id ] ->
                        Just <| LobbyError (GameAlreadyStarted id)

                    [ "noGame", id ] ->
                        Just <| LobbyError (NoSuchGame id)

                    [ "internal" ] ->
                        Just <| ConnectionError Nothing

                    _ -> Nothing
            _ ->
                Nothing
