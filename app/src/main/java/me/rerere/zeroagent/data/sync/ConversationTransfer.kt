package me.rerere.zeroagent.data.sync

import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToStream
import me.rerere.ai.core.MessageRole
import me.rerere.zeroagent.data.model.Avatar
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.chat.ChatDraftStore
import me.rerere.zeroagent.data.chat.ComposerDraft
import me.rerere.zeroagent.data.datastore.DisplaySetting
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.db.AppDatabase
import me.rerere.zeroagent.data.lab.LabPreferences
import me.rerere.zeroagent.data.lab.LabMemoryReview
import me.rerere.zeroagent.data.repository.MemoryRepository
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.productivity.ProjectStore
import me.rerere.zeroagent.data.repository.ConversationRepository
import me.rerere.zeroagent.service.ChatService
import me.rerere.zeroagent.ui.components.ui.ChatPrivacy
import me.rerere.zeroagent.utils.JsonInstant
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.uuid.Uuid

@Serializable
internal data class ConversationTransferArchive(
    val format: String,
    val conversations: List<Conversation> = emptyList(),
    val locked: Set<Uuid> = emptySet(),
    val display: DisplaySetting? = null,
    val labPreferences: Map<String, String> = emptyMap(),
    val featureData: PortableLabData? = null,
)

internal class ConversationTransfer(
    private val context: Context,
    private val repository: ConversationRepository,
    private val settings: SettingsStore,
    private val database: AppDatabase,
    private val chatService: ChatService,
) {
    private val maximumArchiveBytes = 256L * 1024 * 1024
    private val maximumManifestBytes = 12L * 1024 * 1024
    private val assetPattern = Regex("attachments/[a-zA-Z0-9_-]{1,96}(\\.[a-zA-Z0-9]{1,10})?")
    private val settingsKeys = setOf("reply_name", "reply_tone", "reply_length", "cat_paws")

    suspend fun export(target: Uri, ids: Set<Uuid>, includeDisplay: Boolean, includePreferences: Boolean, selectedFeatures: Set<PortableLabSection>) = withContext(Dispatchers.IO) {
        require(ids.size <= 200) { "單次最多匯出 200 個聊天室。" }
        val storedFeatureData = PortableLabData.read(context, selectedFeatures, ids)
        val assets = linkedMapOf<String, File>()
        val paths = hashMapOf<File, String>()
        val source = context.filesDir.canonicalFile
        fun stageAsset(url: String): String {
            val uri = url.toUri()
            if (uri.scheme != "file") return ""
            val file = uri.toFile().canonicalFile
            require(file.isFile && file.toPath().startsWith(source.toPath())) { "有附件已遺失或無法匯出。" }
            require(file.relativeTo(source).invariantSeparatorsPath.substringBefore('/') in setOf("upload", "images", "videos", "tool_outputs")) {
                "這個附件不在可攜式聊天資料範圍內。"
            }
            return paths.getOrPut(file) {
                val extension = file.extension.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,10}")) }.orEmpty()
                val name = "attachments/${Uuid.random()}" + extension.takeIf(String::isNotEmpty)?.let { ".$it" }.orEmpty()
                assets[name] = file
                name
            }
        }
        val conversations = ids.map { id ->
            require(!ChatPrivacy.chatLocked(context, id)) { "鎖定的聊天室不可匯出到一般備份。" }
            val conversation = requireNotNull(repository.getConversationById(id)) { "有一個聊天室已不存在。" }
            conversation.mapAttachments { stageAsset(it) }
        }
        val portableDrafts = if (PortableLabSection.DRAFTS in selectedFeatures && PortableLabSection.DRAFTS.available(context)) {
            val store = ChatDraftStore(context)
            conversations.mapNotNull { conversation ->
                val draft = store.read(conversation.id)?.takeIf { it.text.isNotBlank() || it.attachments.isNotEmpty() }
                    ?: return@mapNotNull null
                val portable = PortableDraft(draft.text, draft.attachments)
                portable.validate()
                conversation.id.toString() to portable.remap { stageAsset(it) }
            }.toMap().takeIf { it.isNotEmpty() }
        } else null
        val portableRelays = if (PortableLabSection.RELAY in selectedFeatures && PortableLabSection.RELAY.available(context)) {
            conversations.mapNotNull { conversation ->
                repository.getCompaction(conversation.id)?.let { summary ->
                    conversation.id.toString() to PortableRelay.from(summary, conversation)
                }
            }.toMap().takeIf { it.isNotEmpty() }
        } else null
        val memory = if (PortableLabSection.MEMORY in selectedFeatures && PortableLabSection.MEMORY.available(context)) {
            val activeScopes = settings.settingsFlow.first().assistants.map { it.id.toString() }.toSet() +
                ProjectStore.get(context).projects.value.map { it.id } + MemoryRepository.GLOBAL_MEMORY_ID
            val sourceRecords = database.memoryDao().getAllMemories().filter { it.assistantId in activeScopes }
            require(sourceRecords.all { isPortableMemoryText(it.content) }) {
                "部分記憶可能包含憑證、過長或無法匯出。請先在記憶抽屜整理，再勾選這項資料。"
            }
            PortableMemory(
                scopes = sourceRecords.groupBy({ it.assistantId }, { it.content }).mapValues { it.value.distinct() },
                capturePaused = LabPreferences.enabled(context, LabMemoryReview.CAPTURE_PAUSED),
                blocked = LabPreferences.get(context, LabMemoryReview.BLOCKED).split(',').filter(String::isNotBlank).toSet(),
            )
        } else null
        val featureData = if (portableRelays == null && portableDrafts == null && memory == null) storedFeatureData else {
            (storedFeatureData ?: PortableLabData()).copy(relays = portableRelays, drafts = portableDrafts, memory = memory)
        }
        require(conversations.isNotEmpty() || includeDisplay || includePreferences || featureData != null) { "請先選擇要匯出的資料。" }
        featureData?.validate(conversations.mapTo(hashSetOf()) { it.id.toString() })
        val configuration = settings.settingsFlow.first()
        val display = if (includeDisplay) configuration.displaySetting.copy(userAvatar = Avatar.Dummy, chatCustomFontPath = "", chatCustomFontName = "") else null
        val preferences = if (includePreferences) settingsKeys.associateWith { LabPreferences.get(context, it) }.filterValues { it.isNotBlank() } else emptyMap()
        val manifest = ConversationTransferArchive(
            format = "zeroagent-conversations-1", conversations = conversations, display = display,
            labPreferences = preferences, featureData = featureData,
        )
        val assetBytes = assets.values.sumOf { it.length() }
        require(assetBytes <= maximumArchiveBytes) { "所選聊天或附件超出單次匯出上限。" }
        val temp = File.createTempFile("portable-chat-", ".zip", context.cacheDir)
        try {
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                val manifestBytes = writePortableManifest(zip, manifest, minOf(maximumManifestBytes, maximumArchiveBytes - assetBytes))
                zip.closeEntry()
                var remaining = maximumArchiveBytes - manifestBytes
                for ((name, file) in assets) {
                    zip.putNextEntry(ZipEntry(name))
                    remaining -= file.inputStream().use { it.copyBounded(zip, remaining) }
                    zip.closeEntry()
                }
            }
            val targetStream = requireNotNull(context.contentResolver.openOutputStream(target, "wt")) { "無法寫入所選位置。" }
            targetStream.use { fileOut -> temp.inputStream().use { it.copyBounded(fileOut, maximumArchiveBytes + maximumManifestBytes) } }
        } finally { temp.delete() }
    }

    internal data class Preview(
        val file: File,
        val payload: ConversationTransferArchive,
        val attachmentCount: Int,
        val matchingIds: Set<Uuid>,
        val lockedMatches: Int,
    ) {
        val matches get() = matchingIds.size
    }

    internal data class ApplyResult(val conversationCount: Int, val warnings: List<String> = emptyList())

    suspend fun preview(uri: Uri): Preview = withContext(Dispatchers.IO) {
        val file = File.createTempFile("portable-import-", ".zip", context.cacheDir)
        try {
            val source = requireNotNull(context.contentResolver.openInputStream(uri)) { "無法讀取匯入檔案。" }
            source.use { input -> file.outputStream().use { input.copyBounded(it, maximumArchiveBytes + maximumManifestBytes) } }
            ZipFile(file).use { zip ->
                require(zip.size() in 1..2001) { "備份項目過多或沒有可用項目。" }
                val entries = zip.entries().asSequence().toList()
                require(entries.distinctBy { it.name }.size == entries.size) { "備份內有重複項目或項目過多。" }
                val manifest = requireNotNull(zip.getEntry("manifest.json")) { "找不到匯入清單。" }
                val bytes = java.io.ByteArrayOutputStream()
                verifyPortableEntry(zip, manifest, maximumManifestBytes, bytes)
                val payload = JsonInstant.decodeFromString<ConversationTransferArchive>(bytes.toString(Charsets.UTF_8.name()))
                require(payload.format == "zeroagent-conversations-1" && payload.conversations.size <= 200 &&
                    payload.conversations.distinctBy { it.id }.size == payload.conversations.size) { "匯入格式或聊天室數量不符。" }
                require(payload.conversations.isNotEmpty() || payload.display != null || payload.labPreferences.isNotEmpty() || payload.featureData != null) { "備份沒有可用的資料。" }
                payload.featureData?.validate(payload.conversations.mapTo(hashSetOf()) { it.id.toString() })
                val incomingById = payload.conversations.associateBy { it.id.toString() }
                payload.featureData?.relays?.forEach { (id, relay) -> relay.validateAgainst(incomingById.getValue(id)) }
                require(payload.locked.isEmpty()) { "這份備份含鎖定的聊天室，無法匯入一般備份。" }
                require(payload.labPreferences.all { (key, value) -> (key in settingsKeys || key in setOf("text_motion", "mood_weather_mode")) && value.length <= 100 && value.none(Char::isISOControl) &&
                    (key != "mood_weather_mode" || value in setOf("off", "manual", "morning", "occasional")) }) {
                    "備份含無效的偏好設定。"
                }
                payload.display?.let { display ->
                    require(display.fontSizeRatio in 0.5f..3f && display.chatLineSpacing in 0.5f..3f &&
                        display.bubbleOpacity in 0f..1f && display.volumeKeyScrollRatio in 0.1f..10f) {
                        "備份中的顯示設定超出可用範圍。"
                    }
                }
                val files = entries.filter { it.name != "manifest.json" }
                require(files.all { !it.isDirectory && it.name.matches(assetPattern) && it.size in 0..maximumArchiveBytes } &&
                    files.sumOf { it.size } <= maximumArchiveBytes) { "附件清單不合法或容量超出上限。" }
                val used = hashSetOf<String>()
                for (conversation in payload.conversations) {
                    require(conversation.messageNodes.distinctBy { it.id }.size == conversation.messageNodes.size) {
                        "備份含重複的訊息節點。"
                    }
                    require(conversation.messageNodes.all { node -> node.selectIndex in node.messages.indices && node.messages.all { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT } }) { "備份含無法載入的訊息版本。" }
                    conversation.createAt.toEpochMilli()
                    conversation.updateAt.toEpochMilli()
                    conversation.mapAttachments { url ->
                        if (url.isNotEmpty()) {
                            require(url.matches(assetPattern) && zip.getEntry(url) != null) { "附件缺失或路徑不合法。" }
                            used += url
                        }
                        url
                    }
                }
                payload.featureData?.drafts?.values?.forEach { draft ->
                    draft.attachments.forEach { attachment ->
                        val url = requireNotNull(attachment.portableDraftAttachmentUrl())
                        require(url.matches(assetPattern) && zip.getEntry(url) != null) { "草稿附件缺失或路徑不合法。" }
                        used += url
                    }
                }
                require(files.all { it.name in used }) { "備份含未列入清單的附件。" }
                var remaining = maximumArchiveBytes - bytes.size()
                for (entry in files) remaining -= verifyPortableEntry(zip, entry, remaining)
                val matches = payload.conversations.map { it.id }.filter { repository.existsConversationById(it) }.toSet()
                Preview(file, payload, used.size, matches, matches.count { ChatPrivacy.chatLocked(context, it) })
            }
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        }
    }

    suspend fun apply(preview: Preview, replaceMatches: Boolean, includeChats: Boolean = true): ApplyResult = withContext(Dispatchers.IO) {
        // Copy files to an app-owned directory before the database transaction. No imported path
        // can refer to a pre-existing private file or to the contents of another chat.
        val destination = File(context.filesDir, "upload")
        check(destination.isDirectory || destination.mkdirs()) { "無法建立匯入空間。" }
        val created = mutableListOf<File>()
        val archive = if (includeChats) preview.payload else preview.payload.copy(conversations = emptyList())
        var committed = false
        try {
            val existing = settings.settingsFlow.first()
            val (incoming, incomingDrafts) = ZipFile(preview.file).use { zip ->
                var remaining = (destination.usableSpace - 32L * 1024 * 1024).coerceIn(0L, maximumArchiveBytes)
                val restoredFiles = mutableMapOf<String, String>()
                suspend fun restoreAsset(url: String): String {
                    if (url.isEmpty()) return ""
                    require(url.matches(assetPattern)) { "附件路徑不合法。" }
                    return restoredFiles.getOrPut(url) {
                        val entry = requireNotNull(zip.getEntry(url))
                        val file = File(destination, "transfer-${Uuid.random()}.${url.substringAfterLast('.', "bin")}")
                        created += file
                        remaining -= file.outputStream().use { verifyPortableEntry(zip, entry, remaining, it) }
                        file.toUri().toString()
                    }
                }
                val chats = archive.conversations.map { conversation ->
                    // Child IDs are remapped together with reply anchors inside the transaction.
                    conversation.mapAttachments { restoreAsset(it) }.copy(
                        assistantId = existing.assistantId, folderId = null, workspaceCwd = null,
                        chatSuggestions = emptyList(), customSystemPrompt = null, chatModelId = null,
                        modeInjectionIds = emptySet(), lorebookIds = emptySet(), newConversation = false,
                    )
                }
                val drafts = if (includeChats) archive.featureData?.drafts.orEmpty().mapValues { (_, draft) ->
                    draft.remap { restoreAsset(it) }.let { ComposerDraft(it.text, it.attachments) }
                } else emptyMap()
                chats to drafts
            }
            currentCoroutineContext().ensureActive()
            val displacedFiles = linkedSetOf<Uri>()
            val replaced = linkedSetOf<Uuid>()
            val importedIds = linkedMapOf<String, String>()
            withContext(NonCancellable) {
                database.withTransaction {
                    for (conversation in incoming) {
                        val old = repository.getConversationById(conversation.id)
                        if (replaceMatches) {
                            require((old != null) == (conversation.id in preview.matchingIds)) {
                                "聊天室清單已變動，請重新預覽備份。"
                            }
                        }
                        val targetId = if (old != null && !replaceMatches) Uuid.random() else conversation.id
                        val (imported, relay) = restorePortableConversation(
                            conversation = conversation,
                            targetId = targetId,
                            relay = archive.featureData?.relays?.get(conversation.id.toString()),
                        )
                        if (old != null && replaceMatches) {
                            require(!ChatPrivacy.chatLocked(context, old.id)) { "有同識別碼的聊天室已鎖定。請使用合併功能另存。" }
                            require(chatService.canReplacePortableConversation(old.id)) { "有同識別碼的聊天室正在使用或有待送出的訊息，請關閉聊天室或改用合併。" }
                            displacedFiles.addAll(old.files)
                            repository.clearCompaction(old.id)
                            repository.updateConversation(imported, updateSearchIndex = false)
                            replaced += old.id
                        } else {
                            repository.insertConversation(imported, updateSearchIndex = false)
                        }
                        relay?.let { repository.upsertCompaction(it) }
                        repository.indexTransferredConversation(imported)
                        importedIds[conversation.id.toString()] = imported.id.toString()
                    }
                }
                committed = true
                replaced.forEach(chatService::dropSession)
                val warnings = mutableListOf<String>()
                try {
                    cleanupDisplacedPortableFiles(displacedFiles)
                } catch (_: Exception) {
                    warnings += "部分舊附件仍保留在裝置上。"
                }
                if (archive.display != null) {
                    try {
                        settings.update { current -> current.copy(displaySetting = archive.display.copy(
                            userAvatar = current.displaySetting.userAvatar,
                            chatCustomFontPath = current.displaySetting.chatCustomFontPath,
                            chatCustomFontName = current.displaySetting.chatCustomFontName,
                        )) }
                    } catch (_: Exception) {
                        warnings += "外觀設定寫入失敗，請重新匯入設定。"
                    }
                }
                try {
                    val values = archive.labPreferences.filterKeys { it in settingsKeys } + archive.featureData?.restoreValues(context, replaceMatches).orEmpty()
                    LabPreferences.commitAll(context, values)
                } catch (_: Exception) {
                    warnings += "趣味功能或實驗資料無法完整套用，請重新匯入這些項目。"
                }
                val store = ProjectStore.get(context)
                val incomingProjects = archive.featureData?.projects.orEmpty().mapTo(hashSetOf()) { it.id }
                val scopeIds = store.projects.value.filter { it.id !in incomingProjects }.associate { it.id to it.id }.toMutableMap()
                archive.featureData?.projects?.let { projects ->
                    try {
                        val restored = store.restorePortableProjects(projects, importedIds, replaceMatches)
                        scopeIds.putAll(restored.scopeIds)
                        val conflicts = restored.skippedLinks
                        if (conflicts > 0) warnings += "$conflicts 個聊天已屬於其他專案，保留原來的歸屬。"
                    } catch (_: Exception) {
                        warnings += "專案工作桌寫入失敗，請重新匯入專案。"
                    }
                }
                if (incomingDrafts.isNotEmpty()) {
                    try {
                        val restored = incomingDrafts.mapNotNull { (source, draft) ->
                            importedIds[source]?.let { Uuid.parse(it) to draft }
                        }.toMap()
                        ChatDraftStore(context).restorePortableDrafts(restored, replaceMatches)
                    } catch (_: Exception) {
                        warnings += "草稿與附件寫入失敗，請重新匯入草稿。"
                    }
                }
                archive.featureData?.draftTexts?.takeIf { includeChats }?.let { drafts ->
                    try {
                        val restored = drafts.mapNotNull { (source, text) -> importedIds[source]?.let { Uuid.parse(it) to text } }.toMap()
                        ChatDraftStore(context).restorePortableText(restored, replaceMatches)
                    } catch (_: Exception) {
                        warnings += "文字草稿寫入失敗，請重新匯入草稿。"
                    }
                }
                archive.featureData?.memory?.let { memory ->
                    try {
                        val scopes = portableMemoryScopeMap(existing.assistants.mapTo(hashSetOf()) { it.id.toString() }, scopeIds, incomingProjects)
                        val skipped = database.withTransaction {
                            restorePortableMemories(database.memoryDao(), memory, scopes, replaceMatches)
                        }
                        if (skipped > 0) warnings += "$skipped 筆記憶找不到原助理或專案，未寫入其他範圍。"
                        val localBlocked = LabPreferences.get(context, LabMemoryReview.BLOCKED).split(',').filter(String::isNotBlank)
                        val paused = memory.capturePaused || (!replaceMatches && LabPreferences.enabled(context, LabMemoryReview.CAPTURE_PAUSED))
                        LabPreferences.commitAll(context, mapOf(
                            LabMemoryReview.CAPTURE_PAUSED to paused.toString(),
                            LabMemoryReview.BLOCKED to (if (replaceMatches) memory.blocked else localBlocked.toSet() + memory.blocked).joinToString(","),
                        ))
                    } catch (_: Exception) {
                        warnings += "記憶或記憶偏好無法完整寫入，請重新匯入這個項目。"
                    }
                }
                ApplyResult(incoming.size, warnings)
            }
        } finally {
            if (!committed) {
                created.forEach(File::delete)
            }
        }
    }

    private suspend fun cleanupDisplacedPortableFiles(files: Set<Uri>) {
        val upload = File(context.filesDir, "upload").canonicalFile
        for (uri in files) {
            val file = runCatching { uri.toFile().canonicalFile }.getOrNull() ?: continue
            if (file.parentFile?.canonicalFile != upload || !file.name.startsWith("transfer-") || !file.isFile) continue
            if (!repository.hasFileReference(uri.toString()) && !file.delete()) {
                throw java.io.IOException("Could not remove unreferenced imported attachment")
            }
        }
    }

    private suspend fun Conversation.mapAttachments(rewrite: suspend (String) -> String): Conversation = copy(messageNodes = messageNodes.map { node ->
        node.copy(messages = node.messages.map { message -> message.copy(annotations = emptyList(), translation = null, parts = message.parts.mapNotNull { part ->
            // Never resume imported tool calls or trust API-specific metadata from another device.
            when (part) {
                is UIMessagePart.Image -> rewrite(part.url).takeIf(String::isNotEmpty)?.let { part.copy(url = it, metadata = null) }
                is UIMessagePart.Video -> rewrite(part.url).takeIf(String::isNotEmpty)?.let { part.copy(url = it, metadata = null) }
                is UIMessagePart.Audio -> rewrite(part.url).takeIf(String::isNotEmpty)?.let { part.copy(url = it, metadata = null) }
                is UIMessagePart.Document -> rewrite(part.url).takeIf(String::isNotEmpty)?.let { part.copy(url = it, metadata = null) }
                is UIMessagePart.Text -> part.copy(metadata = null)
                is UIMessagePart.Tool -> if (part.approvalState == ToolApprovalState.Pending || !part.isExecuted) null
                    else UIMessagePart.Text("[工具紀錄：${part.toolName}]")
                else -> null
            }
        }) })
    })

    private suspend fun InputStream.copyBounded(output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var size = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = read(buffer)
            if (count < 0) return size
            size += count
            require(size <= limit) { "檔案超出大小或儲存空間限制。" }
            output.write(buffer, 0, count)
        }
    }
}

/** Stream validation happens both before preview and while staging the exact bytes to restore. */
@OptIn(ExperimentalSerializationApi::class)
internal fun writePortableManifest(output: OutputStream, manifest: ConversationTransferArchive, limit: Long): Long {
    var written = 0L
    fun checkLimit(count: Int) {
        require(count <= limit - written) { "所選聊天或附件超出單次匯出上限。" }
    }
    val bounded = object : OutputStream() {
        override fun write(value: Int) {
            checkLimit(1)
            output.write(value)
            written++
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            checkLimit(length)
            output.write(bytes, offset, length)
            written += length
        }
    }
    JsonInstant.encodeToStream(ConversationTransferArchive.serializer(), manifest, bounded)
    return written
}

internal suspend fun verifyPortableEntry(zip: ZipFile, entry: ZipEntry, limit: Long, output: OutputStream? = null): Long {
    require(entry.size in 0..limit && entry.crc >= 0) { "備份附件超出大小限制或缺少校驗資料。" }
    val checksum = CRC32()
    var count = 0L
    val buffer = ByteArray(64 * 1024)
    zip.getInputStream(entry).use { input ->
        while (true) {
            currentCoroutineContext().ensureActive()
            val bytes = input.read(buffer)
            if (bytes < 0) break
            count += bytes
            require(count <= entry.size) { "備份附件的容量與清單不符。" }
            checksum.update(buffer, 0, bytes)
            output?.write(buffer, 0, bytes)
        }
    }
    require(count == entry.size && checksum.value == entry.crc) { "備份附件已損壞。" }
    return count
}
