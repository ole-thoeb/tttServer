package json

import arrow.Kind
import arrow.core.Option
import arrow.typeclasses.MonadError
import kotlinx.serialization.decodeFromString
import tryCatch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class JsonParser<F>(private val ME: MonadError<F, JsonError>) : MonadError<F, JsonError> by ME {
    fun parsJsonObj(json: JsonString): Kind<F, JsonObject> = parsJson(json).flatMap { element ->
        Option.fromNullable(element as? JsonObject).fromOption {
            JsonError.MalformedJson(""""$element" is not an JsonObject""")
        }
    }
    
    fun parsJson(json: JsonString): Kind<F, JsonElement> =
            tryCatch(JsonError.Companion::fromThrowable) { packageJson.decodeFromString(json) }
    
    fun JsonObject.getStringK(key: String): Kind<F, String> = getString(key, this@JsonParser)
    fun JsonObject.getObjectK(key: String): Kind<F, JsonObject> = getObject(key, this@JsonParser)
    
    fun <T, C> JsonTypeDeserializer<T, C>.fromJson(jsonContent: JsonObject): Kind<F, T> =
            fromContentJson(jsonContent, this@JsonParser)
}