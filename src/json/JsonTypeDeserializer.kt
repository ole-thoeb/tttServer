package json

import arrow.Kind
import arrow.typeclasses.MonadError
import tryCatch
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

interface JsonTypeDeserializer<T, C> {
    val typeConstructor: (C) -> T
    
    val contentDeserializer: DeserializationStrategy<C>
    @Suppress("PropertyName")
    val TYPE: String
    
    val deserializeJson: Json get() = packageJson
    
    fun <F> fromContentJson(content: JsonObject, ME: MonadError<F, JsonError>): Kind<F, T> =
            ME.tryCatch(JsonError.Companion::fromThrowable) {
                typeConstructor(deserializeJson.decodeFromJsonElement(contentDeserializer, content))
            }
}