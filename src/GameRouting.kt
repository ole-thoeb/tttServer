import game.Game
import game.GameServer
import game.id
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.log
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.util.pipeline.PipelineContext
import io.ktor.websocket.webSocket
import kotlinx.coroutines.launch
import messages.responses.GameResponse

@KtorExperimentalLocationsAPI
fun <L: Game.LobbyImpl, G: Game.InGameImpl> Application.installGameRouting(
        gameServer: GameServer<L, G>,
        gamePreFix: String
) {

    launch {
        for (messages in gameServer.asyncMessages) {
            sendViaWebsocket(messages)
        }
    }

    routing {
        route(gamePreFix) {
            //trace { application.log.trace(it.buildText()) }
            webSocket("/{gameId}/ws") {
                // We check that we actually have a session. We should always have one,
                // since we have defined an interceptor before to set one.
                val session = call.sessions.get<SessionId>() ?: kotlin.run {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
                    log.info("closed because no session")
                    return@webSocket
                }

                val gameId = call.parameters["gameId"] ?: kotlin.run {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No gameId"))
                    log.info("closed socket with $session because no game id was given")
                    return@webSocket
                }


                val welcomeMsg = gameServer.clientJoined(session, GameId(gameId), this)
                send(Frame.Text(welcomeMsg.stringify()))
                try {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                log.info("message from ${session}: $text")
                                sendViaWebsocket(gameServer.handleJsonRequest(session, text))
                            }
                        }
                    }
                } finally {
                    log.info("closed socket with $session")
                    gameServer.clientLeft(session, GameId(gameId), this)
                }
            }
            get("/newGame") {
                val sessionId = call.sessions.get<SessionId>()
                if (sessionId == null) {
                    call.respond(HttpStatusCode.BadRequest, "No Session!")
                    return@get
                }
                val newGame = gameServer.newGame()
                addPlayerToGame(newGame.id, gameServer, sessionId)
            }
            get("/joinGame") { call.respondJson(GameResponse.NoSuchGame("")) }
            get<JoinGame> { joinGame ->
                addPlayerToGame(joinGame.gameId, gameServer)
            }
            get<Rematch> { rematch ->
                val sessionId = call.sessions.get<SessionId>()
                if (sessionId == null) {
                    call.respond(HttpStatusCode.BadRequest, "No Session!")
                    return@get
                }
                call.handleDirectResponse(gameServer.rematch(sessionId, rematch.oldGameId))
            }
        }
    }
}

private suspend fun <L: Game.LobbyImpl, G: Game.InGameImpl> PipelineContext<*, ApplicationCall>.addPlayerToGame(
        gameId: GameId,
        gameServer: GameServer<L, G>,
        paramSessionId: SessionId? = null
) {
    val sessionId = paramSessionId ?: call.sessions.get<SessionId>()
    if (sessionId == null) {
        call.respond(HttpStatusCode.BadRequest, "No Session!")
        return
    }
    call.handleDirectResponse(gameServer.addPlayer(sessionId, gameId))
}


@KtorExperimentalLocationsAPI
@Location("/joinGame/{id}")
data class JoinGame(val id: String) {
    val gameId: GameId = GameId(id)
}

@KtorExperimentalLocationsAPI
@Location("/joinRematch/{id}")
data class Rematch(val id: String) {
    val oldGameId: GameId = GameId(id)
}