package me.rerere.zeroagent.data.sync

import kotlinx.serialization.Serializable
import me.rerere.ai.ui.UIMessagePart

@Serializable
internal data class PortableDraft(val text: String, val attachments: List<UIMessagePart>) {
    fun validate() {
        require(text.length <= 100_000 && (text.isNotBlank() || attachments.isNotEmpty()) && attachments.size <= 100) {
            "草稿內容或附件數量超出限制。"
        }
        require(attachments.all { it.portableDraftAttachmentUrl() != null }) { "草稿包含不支援的附件。" }
    }

    suspend fun remap(rewrite: suspend (String) -> String): PortableDraft = copy(attachments = attachments.map { part ->
        val url = rewrite(requireNotNull(part.portableDraftAttachmentUrl()))
        require(url.isNotEmpty()) { "草稿附件遺失或無法備份。" }
        when (part) {
            is UIMessagePart.Image -> part.copy(url = url, metadata = null)
            is UIMessagePart.Audio -> part.copy(url = url, metadata = null)
            is UIMessagePart.Video -> part.copy(url = url, metadata = null)
            is UIMessagePart.Document -> part.copy(url = url, metadata = null)
            else -> error("Unsupported draft attachment")
        }
    })
}

internal fun UIMessagePart.portableDraftAttachmentUrl(): String? = when (this) {
    is UIMessagePart.Image -> url
    is UIMessagePart.Audio -> url
    is UIMessagePart.Video -> url
    is UIMessagePart.Document -> url
    else -> null
}
