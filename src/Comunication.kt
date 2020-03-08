import json.JsonSerializable
import json.JsonString
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.Frame
import io.ktor.response.respondText

suspend fun sendViaWebsocket(msgs: Messages) {
    msgs.forEach { (player, msg) ->
        player.sockets.forEach { socket ->
            socket.send(Frame.Text(msg.stringify()))
        }
    }
}

suspend fun ApplicationCall.respondJson(json: JsonSerializable) {
    respondJson(json.stringify())
}

suspend fun ApplicationCall.respondJson(json: JsonString) {
    respondText(json, ContentType.Application.Json,  HttpStatusCode.OK)
}