package me.rerere.zeroagent.data.chat

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

@Serializable
internal data class ComposerDraft(val text: String, val attachments: List<UIMessagePart>)

@Serializable
internal data class UnsentChatMessage(val id: String, val parts: List<UIMessagePart>, val answer: Boolean)

internal class ChatDraftStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("conversation_drafts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun observeUnsentConversationIds() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("unsent:")) trySend(unsentConversationIds())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(unsentConversationIds())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    private fun unsentConversationIds(): List<Uuid> = preferences.all.keys.mapNotNull { key ->
        if (!key.startsWith("unsent:")) return@mapNotNull null
        val id = runCatching { Uuid.parse(key.removePrefix("unsent:")) }.getOrNull() ?: return@mapNotNull null
        id.takeIf { readUnsent(it).isNotEmpty() }
    }.sortedBy { it.toString() }

    fun read(id: Uuid): ComposerDraft? = preferences.getString(id.toString(), null)?.let { stored ->
        runCatching { json.decodeFromString<ComposerDraft>(stored) }.getOrNull()
    }

    fun remove(id: Uuid) = preferences.edit().remove(id.toString()).apply()

    @Synchronized
    fun readUnsent(id: Uuid): List<UnsentChatMessage> = preferences.getString("unsent:$id", null)?.let { stored ->
        runCatching { json.decodeFromString<List<UnsentChatMessage>>(stored) }
            .recoverCatching { listOf(json.decodeFromString<UnsentChatMessage>(stored)) }
            .getOrDefault(emptyList())
    }.orEmpty()

    @Synchronized
    fun writeUnsent(id: Uuid, message: UnsentChatMessage, durable: Boolean = false): Boolean {
        val pending = readUnsent(id)
        val next = if (pending.any { it.id == message.id }) {
            pending.map { if (it.id == message.id) message else it }
        } else pending + message
        return storeUnsent(id, next, durable)
    }

    @Synchronized
    fun removeUnsent(id: Uuid, messageId: Uuid, durable: Boolean = false): Boolean {
        val pending = readUnsent(id)
        val remaining = pending.filterNot { it.id == messageId.toString() }
        return if (pending.size == remaining.size) true else storeUnsent(id, remaining, durable)
    }

    @Synchronized
    fun clearUnsent(id: Uuid) {
        storeUnsent(id, emptyList(), durable = false)
    }

    private fun storeUnsent(id: Uuid, messages: List<UnsentChatMessage>, durable: Boolean): Boolean {
        val key = "unsent:$id"
        val edit = preferences.edit()
        if (messages.isEmpty()) edit.remove(key) else edit.putString(key, json.encodeToString(messages))
        if (!durable) {
            edit.apply()
            return true
        }
        val previous = preferences.getString(key, null)
        if (edit.commit()) return true
        // SharedPreferences updates its in-memory value even when the disk write fails.
        // Restore the previous value so an unsuccessful edit or removal leaves the queue intact.
        preferences.edit().putString(key, previous).apply()
        return false
    }

    fun restorePortableDrafts(drafts: Map<Uuid, ComposerDraft>, replace: Boolean) {
        if (drafts.isEmpty()) return
        val editor = preferences.edit()
        for ((id, draft) in drafts) {
            if (!replace && preferences.contains(id.toString())) continue
            editor.putString(id.toString(), json.encodeToString(draft))
        }
        if (!editor.commit()) throw java.io.IOException("Could not persist imported drafts")
    }

    fun restorePortableText(drafts: Map<Uuid, String>, replace: Boolean) {
        if (drafts.isEmpty()) return
        val editor = preferences.edit()
        for ((id, text) in drafts) {
            if (!replace && preferences.contains(id.toString())) continue
            val attachments = read(id)?.attachments.orEmpty()
            editor.putString(id.toString(), json.encodeToString(ComposerDraft(text, attachments)))
        }
        if (!editor.commit()) throw java.io.IOException("Could not persist imported drafts")
    }

    fun write(id: Uuid, draft: ComposerDraft) {
        val key = id.toString()
        if (draft.text.isEmpty() && draft.attachments.isEmpty()) {
            if (preferences.contains(key)) preferences.edit().remove(key).apply()
        } else {
            preferences.edit().putString(key, json.encodeToString(draft)).apply()
        }
    }
}
