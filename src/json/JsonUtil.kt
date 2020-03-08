package json

import arrow.Kind
import arrow.core.*
import arrow.typeclasses.MonadError
import fromNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.JsonObject

typealias JsonString = String

internal val LocaleJsonConfig get() = JsonConfiguration(prettyPrint = true)

internal val packageJson get() = Json(LocaleJsonConfig)


fun <F> JsonObject.getString(key: String, ME: MonadError<F, JsonError>): Kind<F, String> = ME.fx.monad {
    val (field) = getPrimitiveOrNull(key)
            .fromNull(ME) { JsonError.NoSuchField("$this has no field $key") }
    val (content) = field.contentOrNull
            .fromNull(ME) { JsonError.NoSuchField("field $key is not of type String") }
    content
}

fun <F> JsonObject.getObject(key: String, ME: MonadError<F, JsonError>): Kind<F, JsonObject> =
        getObjectOrNull(key)
                .fromNull(ME) { JsonError.NoSuchField("$this has no field $key of type Object") }

fun <T, F> JsonParser<F>.parsRequest(
    json: JsonString,
    deserializers: ListK<JsonTypeDeserializer<T, *>>
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