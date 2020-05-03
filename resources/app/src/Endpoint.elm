module Endpoint exposing (game, home, joinGame, joinRematch, newGame, rematch, websocket)

import Game
import Url.Builder


-- REMOTE

joinGame : Game.Mode -> Game.Id -> String
joinGame =
    endpointHelper "joinGame"


joinRematch : Game.Mode -> Game.Id -> String
joinRematch =
    endpointHelper "joinRematch"


newGame : Game.Mode -> String
newGame mode =
    Url.Builder.absolute
        [ modePreFix mode, "newGame" ]
        []


websocket : Game.Mode -> Game.Id -> String
websocket mode id =
    Url.Builder.absolute
        [ modePreFix mode, Game.idToString id, "ws" ]
        []


-- LOCAL


rematch : Game.Mode -> Game.Id -> String
rematch =
    endpointHelper "rematch"


game : Game.Mode -> Game.Id -> String
game =
    endpointHelper "game"


home : String
home =
    Url.Builder.absolute [] []


-- HELPER


endpointHelper : String -> Game.Mode -> Game.Id -> String
endpointHelper middleSegment mode id =
    Url.Builder.absolute
        [ modePreFix mode, middleSegment, Game.idToString id ]
        []


modePreFix : Game.Mode -> String
modePreFix mode =
    case mode of
        Game.TicTacToe ->
            "ttt"

        Game.Misery ->
            "misery"
