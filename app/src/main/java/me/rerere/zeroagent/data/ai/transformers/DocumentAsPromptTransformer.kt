package me.rerere.zeroagent.data.ai.transformers

import androidx.core.net.toFile
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.document.DocumentExcerptReader
import me.rerere.zeroagent.data.ai.ContextBudgetPlanner
import me.rerere.zeroagent.data.lab.DOCUMENT_CONTEXT_KEY
import me.rerere.zeroagent.data.lab.DOCUMENT_CONTEXT_NAME
import me.rerere.zeroagent.data.lab.replyAttachmentKey
import java.io.File

enum class DocumentReadStatus(val code: String) {
    COMPLETE("complete"),
    EXCERPT("excerpt"),
    UNREADABLE("unreadable"),
    OVER_BUDGET("over_budget"),
}

private const val DOCUMENT_READ_STATUS = "zeroagent_local_document_read"

internal fun UIMessagePart.Document.withReadStatus(status: DocumentReadStatus): UIMessagePart.Document =
    if (readStatus() == status) this else copy(metadata = JsonObject(metadata.orEmpty() +
        (DOCUMENT_READ_STATUS to JsonPrimitive(status.code))))

internal fun UIMessagePart.Document.readStatus(): DocumentReadStatus? {
    val code = (metadata?.get(DOCUMENT_READ_STATUS) as? JsonPrimitive)?.content ?: return null
    return DocumentReadStatus.entries.firstOrNull { it.code == code }
}

object DocumentAsPromptTransformer : InputMessageTransformer {
    private const val MaxCharactersPerAttachment = 12_000
    private const val AttachmentOverhead = 160
    private const val ReadingStatus = "正在讀取附件"

    override suspend fun transform(ctx: TransformerContext, messages: List<UIMessage>): List<UIMessage> =
        withContext(Dispatchers.IO) {
            val documentsPresent = messages.any { message -> message.parts.any { it is UIMessagePart.Document } }
            val scope = this
            if (documentsPresent) ctx.processingStatus.value = ReadingStatus
            try {
                val otherMessages = if (documentsPresent) messages.map { message ->
                    message.copy(parts = message.parts.filterNot { it is UIMessagePart.Document })
                } else emptyList()
                val existingInput = if (documentsPresent) ContextBudgetPlanner.estimateContextTokens(otherMessages) else 0
                var remaining = documentCharacterBudget(ctx.model.contextLength, ctx.assistant.maxTokens, existingInput)
                var incomplete = 0
                var unreadable = 0
                var overBudget = 0
                val transformed = messages.asReversed().map { message ->
                    val documentPrompts = mutableMapOf<Int, UIMessagePart.Text>()
                    for ((index, part) in message.parts.withIndex().reversed()) {
                        if (part !is UIMessagePart.Document) continue
                        scope.ensureActive()
                        val file = localFile(part.url)
                        val limit = (remaining - AttachmentOverhead).coerceIn(0, MaxCharactersPerAttachment)
                        val excerpt = if (limit > 0 && file != null) {
                            try {
                                DocumentExcerptReader.read(file, part.mime, limit) { scope.ensureActive() }
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                        val status = when {
                            file?.isFile != true -> DocumentReadStatus.UNREADABLE
                            limit == 0 -> DocumentReadStatus.OVER_BUDGET
                            excerpt == null -> DocumentReadStatus.UNREADABLE
                            excerpt.truncated -> DocumentReadStatus.EXCERPT
                            else -> DocumentReadStatus.COMPLETE
                        }
                        ctx.onDocumentRead(message.id, part.url, status)
                        val content = when (status) {
                            DocumentReadStatus.UNREADABLE -> {
                                unreadable++
                                if (file?.isFile != true) "[附件讀取失敗：檔案已搬移或無法存取。]"
                                else "[附件未能擷取可讀文字：格式、容量限制或掃描頁面可能需要另行處理。]"
                            }
                            DocumentReadStatus.OVER_BUDGET -> {
                                overBudget++
                                "[附件未讀取：這次請求的附件額度已用完。]"
                            }
                            DocumentReadStatus.EXCERPT -> {
                                incomplete++
                                val partial = requireNotNull(excerpt)
                                "${partial.text}\n[附件僅提供節錄。${partial.inspected.orEmpty()}]"
                            }
                            DocumentReadStatus.COMPLETE -> requireNotNull(excerpt).text
                        }
                        remaining = (remaining - AttachmentOverhead - (excerpt?.text?.length ?: 0)).coerceAtLeast(0)
                        val path = resolveWorkspacePath(part.url)?.let { " path=\"${escapeXml(it)}\"" }.orEmpty()
                        documentPrompts[index] = UIMessagePart.Text(
                            text = "<UploadFile name=\"${escapeXml(part.fileName)}\"$path>\n" +
                                "<![CDATA[${content.replace("]]>", "]]]]><![CDATA[>")}]]>\n</UploadFile>",
                            metadata = buildJsonObject {
                                replyAttachmentKey(message, part)?.let { put(DOCUMENT_CONTEXT_KEY, JsonPrimitive(it)) }
                                put(DOCUMENT_CONTEXT_NAME, JsonPrimitive(part.fileName))
                            },
                        )
                    }
                    message.copy(parts = message.parts.flatMapIndexed { index, part ->
                        when (part) {
                            is UIMessagePart.Document -> listOfNotNull(documentPrompts[index])
                            is UIMessagePart.Image -> {
                                val path = resolveWorkspacePath(part.url)
                                if (path == null) listOf(part) else listOf(
                                    UIMessagePart.Text("<UploadFile name=\"${escapeXml(File(path).name)}\" path=\"${escapeXml(path)}\" />"),
                                    part,
                                )
                            }
                            else -> listOf(part)
                        }
                    })
                }.asReversed()
                if (documentsPresent) {
                    val notices = buildList {
                        if (incomplete > 0) add("$incomplete 份僅提供節錄")
                        if (unreadable > 0) add("$unreadable 份未能讀取")
                        if (overBudget > 0) add("$overBudget 份超出本次額度")
                    }
                    if (notices.isNotEmpty()) ctx.processingStatus.compareAndSet(ReadingStatus, notices.joinToString("、"))
                }
                transformed
            } finally {
                if (documentsPresent && ctx.processingStatus.value == ReadingStatus) ctx.processingStatus.value = null
            }
        }

    internal fun documentCharacterBudget(contextTokens: Int?, outputTokens: Int?, otherInputTokens: Int): Int {
        val context = contextTokens?.takeIf { it > 0 } ?: 16_000
        val output = outputTokens?.takeIf { it > 0 } ?: (context / 4).coerceIn(256, 4_096)
        val systemAndToolsReserve = (context / 16).coerceIn(128, 2_048)
        // Use one token per character for the document allowance. Chinese generally has a
        // higher token-to-character ratio than the generic estimator for conversation history.
        val available = context.toLong() - output - systemAndToolsReserve - otherInputTokens
        return minOf(available, context.toLong() / 4, 24_000L).coerceAtLeast(0).toInt()
    }

    private fun localFile(url: String): File? = runCatching { url.toUri().toFile() }.getOrNull()

    private fun escapeXml(text: String): String = text.replace("&", "&amp;")
        .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // 上传文件保存在 filesDir/upload 下, 该目录通过 proot 挂载到 workspace 的 /upload
    // 返回文件在 workspace 内的绝对路径, 便于 AI 用 workspace 工具直接读取原始文件
    private fun resolveWorkspacePath(url: String): String? {
        val file = runCatching { url.toUri().toFile() }.getOrNull() ?: return null
        return resolveWorkspacePathForFile(file)
    }

    // 纯函数, 不依赖 android.net.Uri, 以便单元测试覆盖 upload 目录网关判断
    internal fun resolveWorkspacePathForFile(file: File): String? {
        if (file.parentFile?.name != "upload") return null
        return "/upload/${file.name}"
    }

}
