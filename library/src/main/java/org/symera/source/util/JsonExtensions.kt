package org.symera.source.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

fun String.parseToJsonElement(): JsonElement =
    SymeraJsonProvider.instance.decodeFromString(JsonElement.serializer(), this)

fun JsonElement.toJsonString(): String =
    SymeraJsonProvider.instance.encodeToString(JsonElement.serializer(), this)

fun JsonObject.optStringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

object SymeraJsonProvider {
    @Volatile
    var instance: Json = Json.Default
}
