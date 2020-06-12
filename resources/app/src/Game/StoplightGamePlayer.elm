module Game.StoplightGamePlayer exposing (PlayerMe, Player, decoder, meDecoder, meAsPlayer)

import Json.Decode as Decode exposing (Decoder)
import Game.GamePlayer exposing (..)


type alias PlayerMe =
    { id : String
    , name : String
    , playerRef : PlayerRef
    }


type alias Player =
    { name : String
    , playerRef : PlayerRef
    }


meAsPlayer : PlayerMe -> Player
meAsPlayer playerMe =
    { name = playerMe.name
    , playerRef = playerMe.playerRef
    }


-- DECODER


decoder : Decoder Player
decoder =
    Decode.map2 Player nameDecoder (Decode.field "playerRef" playerRefDecoder)


meDecoder : Decoder PlayerMe
meDecoder =
    Decode.map3 PlayerMe idDecoder nameDecoder (Decode.field "playerRef" playerRefDecoder)