module Route exposing (Route(..), fromUrl)


import Page.Home as Home
import Url exposing (Url)
import Url.Parser as Parser exposing ((</>), (<?>),  Parser, oneOf, s, string)


type Route
    = Home (Maybe Home.JoinError)
    | Game String
    | Rematch String
    | NotFound


parser : Parser (Route -> a) a
parser =
    oneOf
        [ Parser.map Home (Parser.top <?> Home.joinErrorQueryParser)
        , Parser.map Game (s "game" </> string)
        , Parser.map Rematch (s "joinRematch" </> string)
        ]


fromUrl : Url -> Route
fromUrl url =
    Parser.parse parser url
        |> Maybe.withDefault NotFound
        |> Debug.log "decoded route"