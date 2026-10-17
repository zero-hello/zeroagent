package me.rerere.zeroagent.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.json.*
import me.rerere.zeroagent.utils.JsonInstant

private const val LEGACY_NAMESPACE = "me.rerere.rikkahub."
private const val CURRENT_NAMESPACE = "me.rerere.zeroagent."

/** Rewrite serialized class discriminators only; never alter user prompts or content. */
internal fun migrateNamespaceJson(raw: String): String {
    if (!raw.contains(LEGACY_NAMESPACE)) return raw
    fun transform(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (key == "type" && value is JsonPrimitive && value.isString &&
                value.content.startsWith(LEGACY_NAMESPACE)) {
                JsonPrimitive(CURRENT_NAMESPACE + value.content.removePrefix(LEGACY_NAMESPACE))
            } else transform(value)
        })
        is JsonArray -> JsonArray(element.map(::transform))
        else -> element
    }
    return runCatching {
        val original = JsonInstant.parseToJsonElement(raw)
        val migrated = transform(original)
        if (original == migrated) raw else JsonInstant.encodeToString(migrated)
    }.getOrDefault(raw)
}

/** Run before historical migrations, whose class names now use the product namespace. */
class NamespaceMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap().values.filterIsInstance<String>().any { migrateNamespaceJson(it) != it }
    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            currentData.asMap().forEach { (key, value) ->
                if (value is String) this[stringPreferencesKey(key.name)] = migrateNamespaceJson(value)
            }
        }
    override suspend fun cleanUp() = Unit
}
