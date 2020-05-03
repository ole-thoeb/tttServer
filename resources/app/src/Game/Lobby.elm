module Game.Lobby exposing (Lobby, decoder, playerNameOfLobby, playerReadyOfLobby, allPlayers)


import Game
import Game.LobbyPlayer as LobbyPlayer
import Json.Decode as Decode exposing (Decoder)
import Monocle.Lens as Lens exposing (Lens)


type alias Lobby =
    { gameId: Game.Id
    , players: List LobbyPlayer.Player
    , playerMe: LobbyPlayer.PlayerMe
    }


decoder : Decoder Lobby
decoder =
    Decode.map3 Lobby
        (Decode.field "gameId" Game.idDecoder)
        (Decode.field "players" <| Decode.list LobbyPlayer.decoder)
        (Decode.field "playerMe" LobbyPlayer.meDecoder)


allPlayers : Lobby -> List LobbyPlayer.Player
allPlayers lobby =
    (LobbyPlayer.Player lobby.playerMe.name lobby.playerMe.isReady) :: lobby.players


-- LENS


playerMeOfLobby : Lens Lobby LobbyPlayer.PlayerMe
playerMeOfLobby =
    Lens .playerMe (\player lobby -> { lobby | playerMe = player })


playerNameOfLobby : Lens Lobby String
playerNameOfLobby =
    Lens.compose playerMeOfLobby LobbyPlayer.nameOfPlayerMe


playerReadyOfLobby : Lens Lobby Bool
playerReadyOfLobby =
    Lens.compose playerMeOfLobby LobbyPlayer.readyOfPlayerMe