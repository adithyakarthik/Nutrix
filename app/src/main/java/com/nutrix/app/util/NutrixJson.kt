package com.nutrix.app.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * One JSON configuration for the whole app.
 *
 * [Json.ignoreUnknownKeys] is load-bearing twice over: it lets a row written by a newer build
 * be read by an older one, and it stops an unexpected field in an API response from taking
 * down a food scan.
 */
object NutrixJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        explicitNulls = false
    }

    fun parseObject(raw: String): JsonObject = instance.parseToJsonElement(raw).jsonObject

    fun encode(element: JsonElement): String = instance.encodeToString(JsonElement.serializer(), element)
}
