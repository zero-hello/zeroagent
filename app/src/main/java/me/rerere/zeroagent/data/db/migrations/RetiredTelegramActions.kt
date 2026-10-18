package me.rerere.zeroagent.data.db.migrations

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Preserve recipes for review, but scrub retired action arguments and stop their automatic execution. */
internal fun retireTelegramActions(raw: String): String? {
    val root = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return null
    val actions = when (root) {
        is JsonArray -> root
        is JsonObject -> root["actions"] as? JsonArray
        else -> null
    } ?: return null
    var changed = false
    val retired = JsonArray(actions.map { element ->
        val action = element as? JsonObject ?: return@map element
        val name = (action["tool"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (!name.startsWith("telegram_")) return@map element
        changed = true
        JsonObject(action.toMutableMap().apply { put("args", JsonObject(emptyMap())) })
    })
    if (!changed) return null
    return when (root) {
        is JsonObject -> JsonObject(root.toMutableMap().apply {
            put("actions", retired)
            put("enabled", JsonPrimitive(false))
        })
        else -> retired
    }.toString()
}
