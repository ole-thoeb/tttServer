module Game.TTTGamePlayer exposing (Symbol(..), PlayerMe, Player, decoder, meDecoder, playerRefDecoder, PlayerRef(..), meAsPlayer)

import Json.Decode as Decode exposing (Decoder)


type Symbol = X | O


type alias PlayerMe =
    { id : String
    , name : String
    --, color : String
    , symbol : Symbol
    , playerRef : PlayerRef
    }


type alias Player =
    { name : String
    --, color : String
    , symbol : Symbol
    , playerRef : PlayerRef
    }


type PlayerRef = P1 | P2


meAsPlayer : PlayerMe -> Player
meAsPlayer playerMe =
    { name = playerMe.name
    , symbol =  playerMe.symbol
    --, color = playerMe.color
    , playerRef = playerMe.playerRef
    }


-- DECODER


decoder : Decoder Player
decoder =
    Decode.map3 Player nameDecoder {-colorDecoder-} symbolDecoder (Decode.field "playerRef" playerRefDecoder)


meDecoder : Decoder PlayerMe
meDecoder =
    Decode.map4 PlayerMe idDecoder nameDecoder {-colorDecoder-} symbolDecoder (Decode.field "playerRef" playerRefDecoder)


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


playerRefDecoder : Decoder PlayerRef
playerRefDecoder =
    Decode.string |> Decode.andThen
        (\ref -> case ref of
            "P1" -> Decode.succeed P1
            "P2" -> Decode.succeed P2
            _ -> Decode.fail ("unknown playerRef " ++ ref)
        )