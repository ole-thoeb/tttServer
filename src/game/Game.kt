package game

import GameId
import PlayerId
import SessionId
import arrow.core.ListK
import arrow.core.MapK
import arrow.core.k
import arrow.optics.Lens
import io.ktor.http.cio.websocket.WebSocketSession
import kotlinx.coroutines.Job


sealed class Game<out L : Game.LobbyImpl, out G : Game.InGameImpl> {

    interface LobbyImpl {
        val id: GameId
    }

    interface InGameImpl {
        val id: GameId
    }

    data class Lobby<L : LobbyImpl>(val impl: L) : Game<L, Nothing>() {
        inline fun update(transform: L.() -> L): Lobby<L> = copy(impl = impl.transform())
        fun update(newLobby: L): Lobby<L> = copy(impl = newLobby)
        inline fun <T> map(transform: L.() -> T): T = impl.transform()
    }

    data class InGame<G : InGameImpl>(val impl: G) : Game<Nothing, G>() {
        inline fun update(transform: G.() -> G): InGame<G> = copy(impl = impl.transform())
        inline fun <T> map(transform: G.() -> T): T = impl.transform()
    }
}

val Game<*, *>.id: GameId
    get() = when (this) {
        is Game.Lobby -> impl.id
        is Game.InGame -> impl.id
    }

interface LobbyImplWithPlayer<out H : Player.HumanImpl, out B : Player.BotImpl> : Game.LobbyImpl {
    val players: ListK<Player<H, B>>
}

interface InGameImplWithPlayer<out H : Player.HumanImpl, out B : Player.BotImpl> : Game.InGameImpl {
    val players: ListK<Player<H, B>>
}

val <H : Player.HumanImpl, B : Player.BotImpl, L : LobbyImplWithPlayer<H, B>> Game.Lobby<L>.players: ListK<Player<H, B>>
    get() = impl.players

val <H : Player.HumanImpl, B : Player.BotImpl, G : InGameImplWithPlayer<H, B>> Game.InGame<G>.players: ListK<Player<H, B>>
    get() = impl.players

val <L : LobbyImplWithPlayer<*, *>, G : InGameImplWithPlayer<*, *>> Game<L, G>.humanPlayersG: ListK<Player.Human<*>>
    get() = when (this) {
        is Game.Lobby -> (this as Game.Lobby).humanPlayers
        is Game.InGame -> (this as Game.InGame).humanPlayers
    }

val <HL : Player.HumanImpl, BL : Player.BotImpl,
        L : LobbyImplWithPlayer<HL, BL>>
        Game.Lobby<L>.humanPlayers: ListK<Player.Human<HL>>
    get() = impl.players.mapNotNull {
        if (it is Player.Human<HL>) it else null
    }.k()

val <HG : Player.HumanImpl, BG : Player.BotImpl,
        G : InGameImplWithPlayer<HG, BG>>
        Game.InGame<G>.humanPlayers: ListK<Player.Human<HG>>
    get() = impl.players.mapNotNull {
        if (it is Player.Human<HG>) it else null
    }.k()

sealed class Player<out H : Player.HumanImpl, out B : Player.BotImpl> {
    interface HumanImpl {
        val playerId: PlayerId get() = technical.playerId
        val name: String
        val technical: TechnicalPlayer
    }

    interface BotImpl {
        val playerId: PlayerId
        val name: String
    }

    data class Human<H : HumanImpl>(val impl: H) : Player<H, Nothing>() {
        inline fun update(transform: H.() -> H): Human<H> = copy(impl = impl.transform())
        inline fun <T> map(transform: H.() -> T): T = impl.transform()

        companion object {
            fun <H : HumanImpl> impl(): Lens<Human<H>, H> = Lens(
                    get = { it.impl },
                    set = { human, impl -> human.copy(impl = impl) }
            )
        }
    }

    data class Bot<B : BotImpl>(val impl: B) : Player<Nothing, B>() {
        inline fun update(transform: B.() -> B): Bot<B> = copy(impl = impl.transform())
        inline fun <T> map(transform: B.() -> T): T = impl.transform()
    }
}

val Player<*, *>.playerId: PlayerId
    get() = when (this) {
        is Player.Human -> impl.playerId
        is Player.Bot -> impl.playerId
    }

val Player<*, *>.name: String
    get() = when (this) {
        is Player.Human -> impl.name
        is Player.Bot -> impl.name
    }

val Player.Human<*>.technical: TechnicalPlayer get() = impl.technical


//fun <G : Game> G.addSocket(sessionId: SessionId, webSocket: WebSocketSession): G {
//    return modifyTechnical({ it.technical.sessionId == sessionId }) {
//        it.addSocket(webSocket)
//    } as G
//}
//
//fun <G : Game> G.removeSocket(sessionId: SessionId, webSocket: WebSocketSession): G {
//    return modifyTechnical({ it.technical.sessionId == sessionId }) {
//        it.removeSocket(webSocket)
//    } as G
//}
//
//fun <G : Game> G.addJob(playerId: PlayerId, key: String, job: Job): G {
//    return modifyTechnical({ it.playerId == playerId }) {
//        it.addJob(key, job)
//    } as G
//}
//
//fun <G : Game> G.cancelJob(playerId: PlayerId, key: String): G {
//    return modifyTechnical({ it.playerId == playerId }) {
//        it.cancelJob(key)
//    } as G
//}

//
//val Game.humanPlayers: ListK<Game.Player.Human>
//    get() = players.filter(Game.Player.Human::class).k()

operator fun <H : Player.HumanImpl, B : Player.BotImpl> List<Player<H, B>>.get(playerId: PlayerId): Player<H, B>? =
        firstOrNull { it.playerId == playerId }

operator fun <H : Player.HumanImpl> List<Player.Human<H>>.get(sessionId: SessionId): Player.Human<H>? =
        firstOrNull { it.technical.sessionId == sessionId }

data class TechnicalPlayer(
        val playerId: PlayerId = PlayerId.create(),
        val sessionId: SessionId,
        val sockets: ListK<WebSocketSession> = ListK.empty(),
        val jobs: MapK<String, Job> = emptyMap<String, Job>().k()
) {

    fun addSocket(webSocket: WebSocketSession): TechnicalPlayer = copy(sockets = (sockets + webSocket).k())
    fun removeSocket(webSocket: WebSocketSession): TechnicalPlayer = copy(sockets = (sockets - webSocket).k())

    fun addJob(key: String, job: Job): TechnicalPlayer = copy(jobs = (jobs + (key to job)).k())
    fun cancelJob(key: String): TechnicalPlayer {
        val job = jobs[key]
        return if (job != null) {
            job.cancel()
            copy(jobs = (jobs - key).k())
        } else {
            this
        }
    }

    companion object {
        val DUMMY = TechnicalPlayer(PlayerId("DUMMY_PLAYER"), SessionId("DUMMY_SESSION"), ListK.empty(), emptyMap<String, Job>().k())
    }
}

fun Player.HumanImpl.hasNoSockets(): Boolean = technical.sockets.isEmpty()

fun Player.Human<*>.hasNoSockets(): Boolean = impl.hasNoSockets()
