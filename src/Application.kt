import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.features.*
import org.slf4j.event.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.websocket.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.defaultResource
import io.ktor.http.content.resource
import io.ktor.http.content.static
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.locations.get
import io.ktor.sessions.*
import messages.responses.TTTResponse
import java.time.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    val tttGameServer = TTTGameServer()
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }
    
    install(Locations)

    install(WebSockets) {
        pingPeriod = Duration.ofMinutes(1)
    }

    install(Sessions) {
        cookie<SessionId>("SESSION")
    }

    intercept(ApplicationCallPipeline.Features) {
        if (call.sessions.get<SessionId>() == null) {
            call.sessions.set(SessionId.create())
        }
    }

    routing {
        trace { application.log.trace(it.buildText()) }
        webSocket("/ttt/{gameId}/ws") {
            val gameId = call.parameters["gameId"] ?: kotlin.run {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No gameId"))
                return@webSocket
            }

            // We check that we actually have a session. We should always have one,
            // since we have defined an interceptor before to set one.
            val session = call.sessions.get<SessionId>() ?: kotlin.run {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
                return@webSocket
            }

            val welcomeMsg = tttGameServer.clientJoined(session,  GameId(gameId), this)
            send(Frame.Text(welcomeMsg.stringify()))
            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            log.info("message from ${session}: $text")
                            //server.handleJsonRequest(session, text)
                        }
                    }
                }
            } finally {
                tttGameServer.clientLeft(session, this)
            }
        }
        get("/newGame") {
            val sessionId = call.sessions.get<SessionId>()
            if (sessionId == null) {
                call.respond(HttpStatusCode.BadRequest, "No Session!")
                return@get
            }
            val newGame = tttGameServer.newGame()
            val msgs = tttGameServer.addNewPlayer(sessionId, newGame.id)
            val respondMsg = msgs.entries.firstOrNull { it.key.sessionId == sessionId }
            if (respondMsg != null) {
                call.respondJson(respondMsg.value)
            }
            sendViaWebsocket(msgs)
        }
        get("/joinGame") { call.respondJson(TTTResponse.NoSuchGame("")) }
        get<JoinGame> { idContainer ->
            val gameId = /*call.parameters["id"]*/idContainer.gameId
//            if (gameId == null) {
//                call.respond(HttpStatusCode.BadRequest, "No GameId!")
//                return@get
//            }
            val sessionId = call.sessions.get<SessionId>()
            if (sessionId == null) {
                call.respond(HttpStatusCode.BadRequest, "No Session!")
                return@get
            }
            val msgs = tttGameServer.addNewPlayer(sessionId, GameId(gameId))
            if (msgs.isCouldNotMatchGame()) {
                call.respondJson(TTTResponse.NoSuchGame(gameId))
            } else {
                val respondMsg = msgs.entries.firstOrNull { it.key.sessionId == sessionId }
                    ?: throw IllegalStateException("no join response was produced")
                call.respondJson(respondMsg.value)
                sendViaWebsocket(msgs)
            }
        }
        static {
            defaultIndexHtml()
            resource("app.js", "app.js", "app")
            static("*") {
                defaultIndexHtml()
                static("*") {
                    defaultIndexHtml()
                }
            }
        }
    }
}

fun Route.defaultIndexHtml() {
    defaultResource("index.html", "app")
}


@Location("/joinGame/{gameId}")
data class JoinGame(val gameId: String)