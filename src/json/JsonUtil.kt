package json

import arrow.Kind
import arrow.core.*
import arrow.typeclasses.MonadError
import fromNull
import kotlinx.serialization.json.*

typealias JsonString = String

internal val packageJson get() = Json {
    prettyPrint = true
    encodeDefaults = true
}


fun <F> JsonObject.getString(key: String, ME: MonadError<F, JsonError>): Kind<F, String> = ME.fx.monad {
    val (field) = (get(key) as? JsonPrimitive)
            .fromNull(ME) { JsonError.NoSuchField("$this has no field $key") }
    val (content) = field.contentOrNull
            .fromNull(ME) { JsonError.NoSuchField("field $key is not of type String") }
    content
}

fun <F> JsonObject.getObject(key: String, ME: MonadError<F, JsonError>): Kind<F, JsonObject> =
        (get(key) as? JsonObject)
                .fromNull(ME) { JsonError.NoSuchField("$this has no field $key of type Object") }

fun <T, F> JsonParser<F>.parsRequest(
    json: JsonString,
    deserializers: ListK<JsonTypeDeserializer<out T, *>>
): Kind<F, T> = fx.monad {
    val (jObj) = parsJsonObj(json)
    val (type) = jObj.getStringK("type")
    val (content) = jObj.getObjectK("content")
    deserializers.firstOrNone { it.TYPE == type }
            .fold({
                raiseError<T>(JsonError.UnknownType("Request of $type is not known"))
            }, {
                it.fromJson(content)
            }).bind()
}