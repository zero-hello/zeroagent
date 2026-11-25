package me.rerere.zeroagent.data.ai.tools

import android.content.Context
import kotlin.uuid.Uuid

/** Device-local approval preferences, deliberately separate from exported chat data. */
internal class SharedPreferencesConversationAccessStore(context: Context) : ConversationAccessStore {
    private val preferences = context.applicationContext.getSharedPreferences("conversation_access", Context.MODE_PRIVATE)

    override fun load(): SavedConversationAccess {
        fun parse(value: Any?): ConversationAccessMode =
            ConversationAccessMode.entries.firstOrNull { it.name == value } ?: ConversationAccessMode.ASK
        val modes = preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(CHAT_PREFIX)) return@mapNotNull null
            val id = runCatching { Uuid.parse(key.removePrefix(CHAT_PREFIX)) }.getOrNull() ?: return@mapNotNull null
            id to parse(value)
        }.toMap()
        return SavedConversationAccess(parse(preferences.getString(DEFAULT_KEY, null)), modes)
    }

    override fun save(id: Uuid, mode: ConversationAccessMode): Boolean = preferences.edit()
        .putString(DEFAULT_KEY, mode.name)
        .putString("$CHAT_PREFIX$id", mode.name)
        .commit()

    override fun saveChatOnly(id: Uuid, mode: ConversationAccessMode): Boolean = preferences.edit()
        .putString("$CHAT_PREFIX$id", mode.name)
        .commit()

    private companion object {
        const val CHAT_PREFIX = "chat:"
        const val DEFAULT_KEY = "default_mode"
    }
}
