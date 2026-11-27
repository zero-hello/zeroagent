package me.rerere.zeroagent.data.lab

import android.content.Context

internal fun replyPreferencePrompt(context: Context): String? {
    val name = LabPreferences.get(context, "reply_name").trim().take(32)
    val tone = LabPreferences.get(context, "reply_tone", "natural")
    val length = LabPreferences.get(context, "reply_length", "normal")
    val preferences = buildList {
        if (name.isNotEmpty()) add("Preferred occasional form of address: ${name.replace(Regex("[\\p{Cntrl}<>]"), " ")}")
        when (tone) {
            "friendly" -> add("Prefer a warm, direct tone.")
            "professional" -> add("Prefer a matter-of-fact, professional tone.")
        }
        when (length) {
            "short" -> add("Prefer a concise answer.")
            "detailed" -> add("Prefer enough detail to explain the answer clearly.")
        }
    }
    if (preferences.isEmpty()) return null
    return preferences.joinToString(" ") + " These are soft preferences. Follow the current request first. Avoid repeating the user's name or forcing a tone."
}
