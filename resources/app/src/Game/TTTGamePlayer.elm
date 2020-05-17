module Game.TTTGamePlayer exposing (Symbol(..), PlayerMe, Player, decoder, meDecoder, meAsPlayer)

import Json.Decode as Decode exposing (Decoder)
import Game.GamePlayer exposing (..)

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


symbolDecoder : Decoder Symbol
symbolDecoder =
    Decode.field "symbol" Decode.string |> Decode.andThen (\str ->
        case str of
            "X" -> Decode.succeed X
            "O" -> Decode.succeed O
            _ -> Decode.fail ("unknown symbol " ++ str)
        )
