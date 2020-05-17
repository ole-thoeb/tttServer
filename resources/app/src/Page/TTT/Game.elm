module Page.TTT.Game exposing (Model, toSession, fromLobby, Msg, update, view, subscriptions)

import Browser.Navigation as Nav
import Element
import Endpoint
import Game
import Game.Lobby as Lobby
import Game.MiseryGame exposing (MiseryGame)
import Game.TTTGame exposing (TTTGame)
import Html
import Http
import Json.Decode as Decode exposing (Decoder)
import Page.Home as Home
import Page.TTT.TTTInGame as TTTGame
import Page.TTT.MiseryInGame as MiseryGame
import Page.TTT.Lobby as Lobby
import ServerResponse.EnterLobby as EnterLobbyResponse
import ServerResponse.TTTInGame as TTTResponse
import ServerResponse.MiseryInGame as MiseryResponse
import ServerResponse.InLobby as InLobbyResponse
import ServerResponse.GameResponse as GameResponse
import Session exposing (Session)
import UIHelper
import Url.Builder

import Util exposing (dummy, updateWith)
import Websocket

-- MODEL


type Model
    = Lobby Lobby.Model
    | InGame InGameModel
    | Loading Session Game.Mode


type InGameModel
    = TTTGame TTTGame.Model
    | MiseryGame MiseryGame.Model


toSession : Model -> Session
toSession model =
    case model of
        Lobby lobby ->
            Lobby.toSession lobby

        InGame (TTTGame inGame) ->
            TTTGame.toSession inGame

        InGame (MiseryGame inGame) ->
            MiseryGame.toSession inGame

        Loading session _->
            session


getGameMode : Model -> Game.Mode
getGameMode model =
    case model of
        Lobby lobby ->
            Lobby.getGameMode lobby

        InGame (TTTGame _) ->
            Game.TicTacToe

        InGame (MiseryGame _) ->
            Game.Misery

        Loading _ mode ->
            mode



fromLobby : Session -> Game.Id -> Game.Mode -> Maybe Lobby.Lobby -> ( Model, Cmd Msg )
fromLobby session gameId gameMode maybeLobby =
    case maybeLobby of
        Just lobby ->
            Lobby.init session gameMode lobby
                |> updateWith Lobby GotLobbyMsg

        Nothing ->
            ( Loading session gameMode
            , Http.get
                { url = Endpoint.joinGame gameMode gameId
                , expect = Http.expectJson JoinResponse (GameResponse.decoder GameResponse.defaultInGameDecoder)
                }
            )


-- UPDATE


type Msg
    = GotLobbyMsg Lobby.Msg
    | GotInGameMsg InGameMsg
    | JoinResponse (Result Http.Error (GameResponse.Response GameResponse.DefaultInGame))
    | WebSocketIn String


type InGameMsg
    = TTTMsg TTTGame.Msg
    | MiseryMsg MiseryGame.Msg


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    let
        session = model |> toSession
    in
    case ( msg, model ) of
        ( GotLobbyMsg subMsg, Lobby lobby ) ->
            Lobby.update subMsg lobby
                |> updateWith Lobby GotLobbyMsg

        ( GotInGameMsg (TTTMsg subMsg), InGame (TTTGame inGame) ) ->
            TTTGame.update subMsg inGame
                |> updateWith (InGame << TTTGame) (GotInGameMsg << TTTMsg)

        ( GotInGameMsg (MiseryMsg subMsg), InGame (MiseryGame inGame) ) ->
            MiseryGame.update subMsg inGame
                 |> updateWith (InGame << MiseryGame) (GotInGameMsg << MiseryMsg)

        ( JoinResponse result, Loading _ gameMode ) ->
            case result of
                Ok response ->
                    case response of
                        GameResponse.EnterLobbyResponse (EnterLobbyResponse.LobbyState lobby) ->
                            Lobby.init session gameMode lobby
                                |> updateWith Lobby GotLobbyMsg

                        GameResponse.EnterLobbyResponse (EnterLobbyResponse.Error error) ->
                            ( model
                            , Nav.pushUrl (Session.navKey session) (Url.Builder.absolute [] (Home.joinErrorToQueryParam <| Home.LobbyError error))
                            )

                        GameResponse.InLobbyResponse (InLobbyResponse.LobbyState lobby) ->
                            Lobby.init session gameMode lobby
                                |> updateWith Lobby GotLobbyMsg

                        GameResponse.InGameResponse (GameResponse.TTTResponse (TTTResponse.GameState game)) ->
                            startTTTGame session game

                        GameResponse.InGameResponse (GameResponse.MiseryResponse (MiseryResponse.GameState game)) ->
                            startMiseryGame session game

                        _ ->
                            ( model, Cmd.none )

                Err error ->
                    ( model
                    , Nav.pushUrl (Session.navKey session) (Url.Builder.absolute [] (Home.joinErrorToQueryParam <| Home.ConnectionError (Just error))))

        ( WebSocketIn message, _ ) ->
            case model of
                Lobby lobby ->
                    case Decode.decodeString InLobbyResponse.decoder message of
                        Ok response ->
                            Lobby.updateFromWebsocket response lobby
                                |> updateWith Lobby GotLobbyMsg

                        Err _ ->
                            case Decode.decodeString GameResponse.defaultInGameDecoder message of
                                Ok (GameResponse.TTTResponse (TTTResponse.GameState game)) ->
                                    startTTTGame session game

                                Ok (GameResponse.MiseryResponse (MiseryResponse.GameState game)) ->
                                    startMiseryGame session game

                                Err error ->
                                    ( Debug.log ("json error lobby" ++ (Decode.errorToString error)) model, Cmd.none )

                                _ ->
                                    ( model, Cmd.none)

                InGame inGame ->
                    case ( Decode.decodeString GameResponse.defaultInGameDecoder message, inGame ) of
                        ( Ok (GameResponse.TTTResponse response), TTTGame tttGame ) ->
                            TTTGame.updateFromWebsocket response tttGame
                                |> updateWith (InGame << TTTGame) (GotInGameMsg << TTTMsg)

                        ( Ok (GameResponse.MiseryResponse response), MiseryGame miseryGame ) ->
                            MiseryGame.updateFromWebsocket response miseryGame
                                |> updateWith (InGame << MiseryGame) (GotInGameMsg << MiseryMsg)

                        ( Err error, _ ) ->
                            ( dummy (Debug.log "json error inGame" error) model, Cmd.none )

                        ( _, _ ) ->
                            ( model, Cmd.none )

                Loading _ _ ->
                    ( model, Cmd.none )

        ( _, _ ) ->
            ( model, Cmd.none )


startTTTGame : Session -> TTTGame -> ( Model, Cmd Msg )
startTTTGame session game =
    TTTGame.init session game
        |> updateWith (InGame << TTTGame) (GotInGameMsg << TTTMsg)


startMiseryGame : Session -> MiseryGame -> ( Model, Cmd Msg )
startMiseryGame session game =
    MiseryGame.init session game
        |> updateWith (InGame << MiseryGame) (GotInGameMsg << MiseryMsg)

-- VIEW


view : Model -> { title: String, body: Html.Html Msg }
view model =
    case model of
        Lobby lobby ->
            viewFragment GotLobbyMsg (Lobby.view lobby)

        InGame (TTTGame inGame) ->
            viewFragment (GotInGameMsg << TTTMsg) (TTTGame.view inGame)

        InGame (MiseryGame inGame) ->
            viewFragment (GotInGameMsg << MiseryMsg) (MiseryGame.view inGame)

        Loading session _ ->
            { title = "TTT"
            , body = Element.layout [] (UIHelper.loading <| Session.theme session)
            }


viewFragment : (subMsg -> Msg) -> { title: String, body: Html.Html subMsg } ->  { title: String, body: Html.Html Msg }
viewFragment toMsg { title, body } =
    { title = title
    , body = Html.map toMsg body
    }


-- SUBSCRIPTIONS

subscriptions : Model -> Sub Msg
subscriptions model =
    case model of
        Lobby lobby ->
            Sub.batch
                [ Websocket.receive WebSocketIn
                , Lobby.subscriptions lobby
                    |> Sub.map GotLobbyMsg
                ]

        InGame session ->
            Websocket.receive WebSocketIn

        Loading _ _ ->
            Sub.none


-- GAME RESPONSE
