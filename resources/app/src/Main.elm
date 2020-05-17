module Main exposing (..)


import Browser
import Browser.Navigation as Nav
import Page.Rematch as Rematch
import Page.TTT.Game as Game
import Route exposing (Route)
import Url

import Page.Home as Home
import Page.Blank as Blank
import Page.NotFound as NotFound
import Session exposing (Session)
import Util exposing (..)



-- MODEL


type Model
    = Initial Session
    | Home Home.Model
    | Game Game.Model
    | Rematch Rematch.Model
    | NotFound NotFound.Model


-- UPDATE


type Msg
    = UrlChanged Url.Url
    | LinkClicked Browser.UrlRequest
    | GotHomeMsg Home.Msg
    | GotGameMsg Game.Msg
    | GotRematchMsg Rematch.Msg
    | GotNotFoundMsg NotFound.Msg


toSession : Model -> Session
toSession model =
    case model of
        Home home ->
            Home.toSession home

        Initial session ->
            session

        NotFound notFound ->
            NotFound.toSession notFound

        Rematch rematch ->
            Rematch.toSession rematch

        Game game ->
            Game.toSession game



changeRouteTo : Route -> Model -> ( Model, Cmd Msg )
changeRouteTo route model =
    let
        session = toSession model
    in
    case route of
        Route.Home maybeError ->
            Home.init session maybeError
                |> updateWith Home GotHomeMsg

        Route.Game gameMode gameId ->
            let
                lobby = case model of
                     Home home -> home.lobby
                     _ -> Nothing
            in
            Game.fromLobby session gameId gameMode lobby
                |> updateWith Game GotGameMsg

        Route.Rematch gameMode oldGameId ->
            Rematch.init session oldGameId gameMode
                |> updateWith Rematch GotRematchMsg

        Route.NotFound ->
            NotFound.init session
                |> updateWith NotFound GotNotFoundMsg


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case ( msg, model ) of
        ( UrlChanged url, _ ) ->
            changeRouteTo (Route.fromUrl url) model

        ( LinkClicked urlRequest, _ ) ->
            case urlRequest of
                Browser.Internal url ->
                  ( model, Nav.pushUrl (Session.navKey (toSession model)) (Url.toString url) )

                Browser.External href ->
                  ( model, Nav.load href )

        ( GotHomeMsg subMsg, Home home ) ->
            Home.update subMsg home
                |> updateWith Home GotHomeMsg

        ( GotGameMsg subMsg, Game game ) ->
            Game.update subMsg game
                |> updateWith Game GotGameMsg

        ( GotRematchMsg subMsg, Rematch rematch ) ->
                    Rematch.update subMsg rematch
                        |> updateWith Rematch GotRematchMsg

        ( GotNotFoundMsg subMsg, NotFound notFound ) ->
            NotFound.update subMsg notFound
                |> updateWith NotFound GotNotFoundMsg

        ( _, _) ->
            ( model, Cmd.none )


-- VIEW


view : Model -> Browser.Document Msg
view model =
    let
        viewHelper { title, body } =
            { title = title
            , body = [ body ]
            }
    in
    case model of
        Home home ->
            viewPage GotHomeMsg (Home.view home)

        Initial _ ->
            viewHelper Blank.view

        NotFound notFound ->
            viewPage GotNotFoundMsg (NotFound.view notFound)

        Rematch rematch ->
            viewPage GotRematchMsg (Rematch.view rematch)

        Game game ->
            viewPage GotGameMsg (Game.view game)


-- SUBSCRIPTIONS


subscriptions : Model -> Sub Msg
subscriptions model =
    case model of
        Home home ->
             Sub.map GotHomeMsg (Home.subscriptions home)

        Initial _ ->
            Sub.none

        Game game ->
            Sub.map GotGameMsg (Game.subscriptions game)

        Rematch rematch ->
            Sub.map GotRematchMsg (Rematch.subscriptions rematch)

        NotFound _ ->
            Sub.none


-- MAIN


main =
    Browser.application
        { init = init
        , update = update
        , view = view
        , subscriptions = subscriptions
        , onUrlChange = UrlChanged
        , onUrlRequest = LinkClicked
        }


init : () -> Url.Url -> Nav.Key -> ( Model, Cmd Msg )
init flags url key =
    changeRouteTo (Route.fromUrl url) (Initial (Session.fromKey key))
