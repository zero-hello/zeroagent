package me.rerere.zeroagent.data.ai.tools

import android.os.SystemClock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

/** Temporary consent for one user turn. No permission is saved across process restarts. */
internal object WorkspaceReadGrants {
    private const val TTL_MS = 10 * 60 * 1000L
    private const val PREFIX = "/workspace/"
    private data class Grant(
        val workspace: String,
        val turn: Uuid,
        val folder: String,
        val access: ConversationAccessSnapshot,
        val expiresAt: Long,
    )
    private val grants = java.util.concurrent.ConcurrentHashMap<Uuid, Grant>()

    fun path(tool: UIMessagePart.Tool): String? {
        if (tool.toolName != "workspace_read_file" && tool.toolName != "workspace_read_folder") return null
        val json = tool.inputAsJson() as? JsonObject ?: return null
        val value = (json["path"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        if (!value.startsWith(PREFIX) || value != value.trim() || '\\' in value || '\u0000' in value) return null
        return value.removePrefix(PREFIX).trimEnd('/').takeIf { path ->
            path.isNotBlank() && path.split('/').none { it.isBlank() || it == "." || it == ".." }
        }
    }

    fun canOffer(tool: UIMessagePart.Tool): Boolean = tool.toolName == "workspace_read_folder" && path(tool) != null

    fun grant(chat: Uuid, workspace: String, turn: Uuid, folder: String, access: ConversationAccessSnapshot) {
        val now = SystemClock.elapsedRealtime()
        grants.entries.removeIf { it.value.expiresAt <= now }
        if (ConversationAccess.snapshot(chat) == access) {
            grants[chat] = Grant(workspace, turn, folder, access, now + TTL_MS)
        }
    }

    fun allows(chat: Uuid, workspace: String, turn: Uuid, path: String): Boolean {
        val grant = grants[chat] ?: return false
        if (grant.expiresAt <= SystemClock.elapsedRealtime() || grant.access != ConversationAccess.snapshot(chat)) {
            grants.remove(chat, grant)
            return false
        }
        return grant.workspace == workspace && grant.turn == turn &&
            (path == grant.folder || path.startsWith(grant.folder + "/"))
    }

    fun clear(chat: Uuid) { grants.remove(chat) }
}