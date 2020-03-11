module Route exposing (Route(..), fromUrl)


import ServerResponse.EnterLobby as EnterLobby
import Url exposing (Url)
import Url.Parser as Parser exposing ((</>), (<?>),  Parser, oneOf, s, string)


type Route
    = Home (Maybe EnterLobby.Error)
    | Game String
    | NotFound


parser : Parser (Route -> a) a
parser =
    oneOf
        [ Parser.map Home (Parser.top <?> EnterLobby.errorQueryParser)
        , Parser.map Game (s "game" </> string)
        ]


fromUrl : Url -> Route
fromUrl url =
    Parser.parse parser url
        |> Maybe.withDefault NotFound
        |> Debug.log "decoded route"