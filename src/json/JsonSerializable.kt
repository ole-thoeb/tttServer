package json

import kotlinx.serialization.json.JsonElement

interface JsonSerializable {
    fun toJson(): JsonElement
    fun stringify(): JsonString = toJson().toString()
}