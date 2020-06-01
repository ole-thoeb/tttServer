module Game.LobbyPlayer exposing
    ( BotR
    , Difficulty(..)
    , HumanR
    , Player(..)
    , PlayerMe
    , decoder
    , encodeDifficulty
    , isReady
    , meDecoder
    , name
    , nameOfPlayerMe
    , readyOfPlayerMe
    )

import Json.Decode as Decode exposing (Decoder)
import Json.Encode as Encode
import Monocle.Lens as Lens exposing (Lens)


type Difficulty
    = ChildsPlay
    | Challenge
    | Nightmare


type Player
    = Human HumanR
    | Bot BotR


type alias HumanR =
    { name : String
    , isReady : Bool
    }


type alias BotR =
    { name : String
    , id : String
    , isReady : Bool
    , difficulty : Difficulty
    }


type alias PlayerMe =
    { id : String
    , name : String
    , isReady : Bool
    }



-- SERIALISATION


decoder : Decoder Player
decoder =
    Decode.oneOf
        [ botDecoder
        , humanDecoder
        ]


meDecoder : Decoder PlayerMe
meDecoder =
    Decode.map3 PlayerMe idDecoder nameDecoder readyDecoder


humanDecoder : Decoder Player
humanDecoder =
    Decode.map2 HumanR nameDecoder readyDecoder
        |> Decode.map Human


botDecoder : Decoder Player
botDecoder =
    Decode.map4 BotR nameDecoder idDecoder readyDecoder (Decode.field "difficulty" difficultyDecoder)
        |> Decode.map Bot


idDecoder : Decoder String
idDecoder =
    Decode.field "id" Decode.string


nameDecoder : Decoder String
nameDecoder =
    Decode.field "name" Decode.string


readyDecoder : Decoder Bool
readyDecoder =
    Decode.field "isReady" Decode.bool


difficultyDecoder : Decoder Difficulty
difficultyDecoder =
    Decode.string
        |> Decode.andThen
            (\stateStr ->
                case stateStr of
                    "CHILDS_PLAY" ->
                        Decode.succeed ChildsPlay

                    "CHALLENGE" ->
                        Decode.succeed Challenge

                    "NIGHTMARE" ->
                        Decode.succeed Nightmare

                    _ ->
                        Decode.fail ("Unknown difficulty state " ++ stateStr)
            )


encodeDifficulty : Difficulty -> Encode.Value
encodeDifficulty difficulty =
    case difficulty of
        ChildsPlay ->
            Encode.string "CHILDS_PLAY"

        Challenge ->
            Encode.string "CHALLENGE"

        Nightmare ->
            Encode.string "NIGHTMARE"



-- LENS


nameOfPlayerMe : Lens PlayerMe String
nameOfPlayerMe =
    Lens .name (\nameArg player -> { player | name = nameArg })


readyOfPlayerMe : Lens PlayerMe Bool
readyOfPlayerMe =
    Lens .isReady (\ready player -> { player | isReady = ready })



-- GETTER


name : Player -> String
name player =
    case player of
        Human humanR ->
            humanR.name

        Bot botR ->
            botR.name


isReady : Player -> Bool
isReady player =
    case player of
        Human humanR ->
            humanR.isReady

        Bot botR ->
            botR.isReady
