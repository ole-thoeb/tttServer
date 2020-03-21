module Game.TTTGamePlayer exposing (Symbol(..), PlayerMe, Player, decoder, meDecoder)

import Json.Decode as Decode exposing (Decoder)


type Symbol = X | O


type alias PlayerMe =
    { id : String
    , name : String
    , color : String
    , symbol : Symbol
    }


type alias Player =
    { name : String
    , color : String
    , symbol : Symbol
    }


-- DECODER


decoder : Decoder Player
decoder =
    Decode.map3 Player nameDecoder colorDecoder symbolDecoder


meDecoder : Decoder PlayerMe
meDecoder =
    Decode.map4 PlayerMe idDecoder nameDecoder colorDecoder symbolDecoder


idDecoder : Decoder String
idDecoder =
    Decode.field "id" Decode.string


nameDecoder : Decoder String
nameDecoder =
    Decode.field "name" Decode.string


colorDecoder : Decoder String
colorDecoder =
    Decode.field "color" Decode.string


symbolDecoder : Decoder Symbol
symbolDecoder =
    Decode.field "symbol" Decode.string |> Decode.andThen (\str ->
        case str of
            "X" -> Decode.succeed X
            "O" -> Decode.succeed O
            _ -> Decode.fail ("unknown symbol " ++ str)
        )