module Game exposing (Id, Mode(..), idDecoder, encodeId, idFromString, idToString)

import Json.Decode as Decode exposing (Decoder)
import Json.Encode as Encode


type Id
    = Id String


idToString : Id -> String
idToString (Id string) =
    string


idFromString : String -> Id
idFromString =
    Id


idDecoder : Decoder Id
idDecoder =
    Decode.map idFromString Decode.string


encodeId : Id -> Encode.Value
encodeId id =
    Encode.string <| idToString id


type Mode
    = TicTacToe
    | Misery
    | Stoplight
