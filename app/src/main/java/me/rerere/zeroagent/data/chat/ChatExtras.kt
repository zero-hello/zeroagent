package me.rerere.zeroagent.data.chat

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

internal object ChatExtras {
    val themeIds = setOf("default", "zeroagent", "sakura", "ocean", "spring", "autumn", "black", "minimal", "claude")

    private fun preferences(context: Context) = context.getSharedPreferences("chat_extras", Context.MODE_PRIVATE)
    private fun tuningKey(conversation: Uuid, model: Uuid) = "tuning:$conversation:$model:"

    fun tuningUpdates(context: Context, conversation: Uuid) = callbackFlow {
        val prefs = preferences(context)
        val prefix = "tuning:$conversation:"
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == null || changed.startsWith(prefix)) trySend(Unit)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setTemperature(context: Context, conversation: Uuid, model: Uuid, value: Float?) {
        val key = tuningKey(conversation, model) + "temperature"
        val editor = preferences(context).edit()
        if (value == null) editor.remove(key) else editor.putFloat(key, value)
        editor.apply()
    }

    fun hasTemperatureOverride(context: Context, conversation: Uuid, model: Uuid) =
        preferences(context).contains(tuningKey(conversation, model) + "temperature")

    fun setReasoning(context: Context, conversation: Uuid, model: Uuid, value: ReasoningLevel) {
        preferences(context).edit().putString(tuningKey(conversation, model) + "reasoning", value.name).apply()
    }

    fun applyTuning(context: Context, conversation: Uuid, model: Model): Model {
        val prefs = preferences(context)
        val key = tuningKey(conversation, model.id)
        val temperature = prefs.getFloat(key + "temperature", Float.NaN).takeIf { it.isFinite() }
        val reasoning = ReasoningLevel.entries.firstOrNull { it.name == prefs.getString(key + "reasoning", null) }
        if (temperature == null && reasoning == null) return model
        return model.copy(generation = model.generation.copy(
            temperature = temperature ?: model.generation.temperature,
            reasoningLevel = reasoning ?: model.generation.reasoningLevel,
        ))
    }

    fun theme(context: Context, conversation: Uuid): String =
        preferences(context).getString("theme:$conversation", "default") ?: "default"

    fun setTheme(context: Context, conversation: Uuid, id: String) {
        require(id in themeIds)
        preferences(context).edit().putString("theme:$conversation", id).apply()
    }

    fun themeFlow(context: Context, conversation: Uuid) = callbackFlow {
        val prefs = preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme:$conversation" || key == null) trySend(theme(context, conversation))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(theme(context, conversation))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun themeTool(context: Context, conversation: Uuid) = Tool(
        name = "set_chat_theme",
        description = "Change this conversation's palette. Values: ${themeIds.joinToString()}. Apply only when helpful or requested. 'default' restores the app theme.",
        parameters = { stringArg("theme", "An allowed palette ID") },
        execute = { input ->
            val theme = argument(input, "theme")
            if (theme !in themeIds || theme == null) result("Unknown theme")
            else {
                setTheme(context, conversation, theme)
                result("Chat theme updated to $theme")
            }
        },
    )

    private fun argument(input: JsonElement, name: String): String? = runCatching {
        input.jsonObject[name]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun result(text: String) = listOf(UIMessagePart.Text(text))

    private fun stringArg(name: String, description: String) = InputSchema.Obj(
        properties = buildJsonObject {
            put(name, buildJsonObject { put("type", "string"); put("description", description) })
        },
        required = listOf(name),
    )
}
