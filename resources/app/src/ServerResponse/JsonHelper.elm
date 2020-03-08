module ServerResponse.JsonHelper exposing (typeDecoder, contentDecoder)


import Json.Decode as Decode exposing (Decoder)


typeDecoder : Decoder String
typeDecoder =
    Decode.field "type" Decode.string


contentDecoder : Decoder a -> Decoder a
contentDecoder decoder =
    Decode.field "content" decoder