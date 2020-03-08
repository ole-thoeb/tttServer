module Game.LobbyPlayer exposing (meDecoder, decoder, PlayerMe, Player, nameOfPlayerMe, readyOfPlayerMe)

import Json.Decode as Decode exposing (Decoder)
import Monocle.Lens as Lens exposing (Lens)

type alias Player =
    { name: String
    , isReady: Bool
    }

type alias PlayerMe =
    { id: String
    , name: String
    , isReady: Bool
    }


-- SERIALISATION


decoder : Decoder Player
decoder =
    Decode.map2 Player nameDecoder readyDecoder


meDecoder : Decoder PlayerMe
meDecoder =
    Decode.map3 PlayerMe idDecoder nameDecoder readyDecoder


idDecoder : Decoder String
idDecoder =
    Decode.field "id" Decode.string


nameDecoder : Decoder String
nameDecoder =
    Decode.field "name" Decode.string


readyDecoder : Decoder Bool
readyDecoder =
    Decode.field "isReady" Decode.bool



-- LENS


nameOfPlayerMe : Lens PlayerMe String
nameOfPlayerMe =
    Lens .name (\name player -> { player | name = name })


readyOfPlayerMe : Lens PlayerMe Bool
readyOfPlayerMe =
    Lens .isReady (\ready player -> { player | isReady = ready })