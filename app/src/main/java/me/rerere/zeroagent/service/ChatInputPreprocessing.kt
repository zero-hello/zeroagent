package me.rerere.zeroagent.service

import android.net.Uri
import androidx.core.net.toUri
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.files.FilesManager
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.data.model.AssistantAffectScope
import me.rerere.zeroagent.data.model.replaceRegexes

internal fun preprocessUserInputParts(
    parts: List<UIMessagePart>,
    assistant: Assistant,
): List<UIMessagePart> {
    return parts.map { part ->
        when (part) {
            is UIMessagePart.Text -> {
                part.copy(
                    text = part.text.replaceRegexes(
                        assistant = assistant,
                        scope = AssistantAffectScope.USER,
                        visual = false
                    )
                )
            }

            else -> part
        }
    }
}

internal fun UIMessagePart.copyWithForkedFileUrl(copiedFiles: MutableMap<String, Uri>, filesManager: FilesManager): UIMessagePart {
    fun copyLocalFileIfNeeded(url: String): String {
        if (!url.startsWith("file:")) return url
        return copiedFiles.getOrPut(url) {
            requireNotNull(filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()) {
                "無法複製分岔所需的附件，請確認檔案及儲存空間。"
            }
        }.toString()
    }

    return when (this) {
        is UIMessagePart.Image -> copy(url = copyLocalFileIfNeeded(url))
        is UIMessagePart.Document -> copy(url = copyLocalFileIfNeeded(url))
        is UIMessagePart.Video -> copy(url = copyLocalFileIfNeeded(url))
        is UIMessagePart.Audio -> copy(url = copyLocalFileIfNeeded(url))
        else -> this
    }
}
