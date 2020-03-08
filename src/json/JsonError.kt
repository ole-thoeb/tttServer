package json

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonException

sealed class JsonError {
    
    abstract val message: String
    
    data class NotDeserializable(
            override val message: String,
            val cause: Option<SerializationException> = None
    ) : JsonError() {
        constructor(cause: SerializationException) : this(cause.localizedMessage, Some(cause))
    }
    
    data class MalformedJson(
            override val message: String,
            val cause: Option<JsonException> = None
    ) : JsonError() {
        constructor(cause: JsonException) : this(cause.localizedMessage, Some(cause))
    }
    
    data class UnknownError(
            override val message: String,
            val cause: Option<Throwable> = None
    ) : JsonError() {
        constructor(cause: Throwable) : this(cause.localizedMessage, Some(cause))
    }
    
    data class UnknownType(override val message: String) : JsonError()
    
    data class NoSuchField(override val message: String) : JsonError()
    
    companion object {
        fun fromThrowable(t : Throwable): JsonError = when (t) {
            is JsonException -> MalformedJson(t)
            is SerializationException -> NotDeserializable(t)
            else -> UnknownError(t)
        }
    }
}