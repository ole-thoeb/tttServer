import arrow.core.ListK
import arrow.core.k
import io.ktor.application.*
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.defaultResource
import io.ktor.http.content.resource
import io.ktor.http.content.static
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.locations.get
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.pipeline.PipelineContext
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import messages.responses.TTTResponse
import org.slf4j.event.Level
import java.time.Duration

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalLocationsAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    val tttGameServer = TTTGameServer(this, testing)
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }

    install(Locations)

    install(WebSockets) {
        pingPeriod = if (testing) Duration.ofMinutes(1) else Duration.ofSeconds(5)
    }

    install(Sessions) {
        cookie<SessionId>("SESSION")
    }

    intercept(ApplicationCallPipeline.Features) {
        if (call.sessions.get<SessionId>() == null) {
            call.sessions.set(SessionId.create())
        }
    }

    installGameRouting(tttGameServer, "ttt")

    routing {
        static {
            defaultIndexHtml()
            resource("app.js", "app.js", "app")
            static("*") {
                defaultIndexHtml()
                static("*") {
                    defaultIndexHtml()
                    static("*") {
                        defaultIndexHtml()
                    }
                }
            }
        }
    }
}

fun Route.defaultIndexHtml() {
    defaultResource("index.html", "app")
}
