package org.symera.source.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Response
import org.symera.source.online.DEFAULT_MAXIMUM_BODY_BYTES
import org.symera.source.online.bodyString

val DefaultSourceJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
    explicitNulls = false
}

inline fun <reified T> String.parseAs(json: Json = DefaultSourceJson): T = json.decodeFromString(this)

inline fun <reified T> Response.parseAs(
    json: Json = DefaultSourceJson,
    maximumBytes: Long = DEFAULT_MAXIMUM_BODY_BYTES,
): T = use { response -> response.bodyString(maximumBytes).parseAs(json) }

fun String.parseToJsonElement(json: Json = DefaultSourceJson): JsonElement = json.parseToJsonElement(this)

fun JsonElement.toJsonString(json: Json = DefaultSourceJson): String = json.encodeToString(JsonElement.serializer(), this)

fun JsonObject.optStringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
