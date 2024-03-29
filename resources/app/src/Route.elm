module Route exposing (Route(..), fromUrl)

import Game
import Page.Home as Home
import Url exposing (Url)
import Url.Parser as Parser exposing ((</>), (<?>), Parser, oneOf, s, string)


type Route
    = Home (Maybe Home.JoinError)
    | Game Game.Mode Game.Id
    | Rematch Game.Mode Game.Id
    | NotFound


parser : Parser (Route -> a) a
parser =
    oneOf
        [ Parser.map Home (Parser.top <?> Home.joinErrorQueryParser)
        , Parser.map Game (modeParser </> s "game" </> idParser)
        , Parser.map Rematch (modeParser </> s "rematch" </> idParser)
        ]


idParser : Parser (Game.Id -> a) a
idParser =
    Parser.map Game.idFromString string


modeParser : Parser (Game.Mode -> a) a
modeParser =
    Parser.custom "GAME_MODE" modeFromPrefix


modeFromPrefix : String -> Maybe Game.Mode
modeFromPrefix prefix =
    case prefix of
        "ttt" ->
            Just Game.TicTacToe

        "misery" ->
            Just Game.Misery

        "stoplight" ->
            Just Game.Stoplight

        _ ->
            Nothing


fromUrl : Url -> Route
fromUrl url =
    Parser.parse parser url
        |> Maybe.withDefault NotFound
        |> Debug.log "decoded route"
