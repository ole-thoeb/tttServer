import game.GameServer
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.log
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.Frame
import io.ktor.response.respondText
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import json.JsonSerializable
import json.JsonString

suspend fun Application.sendViaWebsocket(msgs: Messages) {
    msgs.forEach { (player, msg) ->
        val jsonString = msg.stringify()
        log.info("response vie Websocket ${player.playerId}: $jsonString")
        player.sockets.forEach { socket ->
            socket.send(Frame.Text(jsonString))
        }
    }
}

suspend fun ApplicationCall.respondJson(json: JsonSerializable) {
    respondJson(json.stringify())
}

suspend fun ApplicationCall.respondJson(json: JsonString) {
    application.log.info("response to ${sessions.get<SessionId>()}: $json")
    respondText(json, ContentType.Application.Json, HttpStatusCode.OK)
}


suspend fun ApplicationCall.handleDirectResponse(response: GameServer.DirectResponse) {
    respondJson(response.calleeResponse)
    application.sendViaWebsocket(response.otherResponses)
}