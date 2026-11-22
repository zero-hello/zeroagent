package me.rerere.zeroagent.data.lab

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal object LabPreferences {
    const val FILE_NAME = "zeroagent_lab"
    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun retireUnusedOptions(context: Context) {
        val preferences = preferences(context)
        val retired = setOf(
            "handoff", "todo_capture", "self_check", "reply_context", "rewrite", "project_desk",
            "recipes", "grounded_qa", "document_diff", "validate_output", "artifact", "preview",
            "acceptance", "focus", "voice_draft", "redaction", "image_extract", "practice",
            "requirements_card", "problem_report", "calculator", "batch", "decision_board",
            "mood_weather_mode", "mood_weather_sign", "mood_weather_report", "text_motion",
        )
        val stored = retired.filter(preferences::contains)
        if (stored.isNotEmpty()) {
            preferences.edit().also { editor -> stored.forEach(editor::remove) }.apply()
        }
    }

    fun get(context: Context, key: String, default: String = ""): String =
        preferences(context).getString(key, default) ?: default

    fun set(context: Context, key: String, value: String) {
        preferences(context).edit().putString(key, value).apply()
    }

    fun setAll(context: Context, values: Map<String, String>) {
        if (values.isEmpty()) return
        val editor = preferences(context).edit()
        values.forEach { (key, value) -> editor.putString(key, value) }
        editor.apply()
    }

    /** Portable restore confirms the write before reporting success. */
    fun commitAll(context: Context, values: Map<String, String>) {
        if (values.isEmpty()) return
        val editor = preferences(context).edit()
        values.forEach { (key, value) -> editor.putString(key, value) }
        if (!editor.commit()) throw java.io.IOException("Could not persist portable settings")
    }

    fun remove(context: Context, key: String) {
        preferences(context).edit().remove(key).apply()
    }

    fun enabled(context: Context, key: String): Boolean = get(context, key) == "true"

    fun flow(context: Context, key: String, default: String = ""): Flow<String> = callbackFlow {
        val preferences = preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == null || changed == key) trySend(get(context, key, default))
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(get(context, key, default))
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
