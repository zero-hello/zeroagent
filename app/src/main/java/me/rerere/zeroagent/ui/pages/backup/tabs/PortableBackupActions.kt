package me.rerere.zeroagent.ui.pages.backup.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.db.AppDatabase
import me.rerere.zeroagent.data.repository.ConversationRepository
import me.rerere.zeroagent.data.sync.ConversationTransfer
import me.rerere.zeroagent.data.sync.PortableLabSection
import me.rerere.zeroagent.service.ChatService
import me.rerere.zeroagent.ui.components.ui.ChatPrivacy
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
internal fun PortableBackupActions() {
    val context = LocalContext.current.applicationContext
    val repository = koinInject<ConversationRepository>()
    val settings = koinInject<SettingsStore>()
    val database = koinInject<AppDatabase>()
    val chatService = koinInject<ChatService>()
    val transfer = remember(context, repository, settings, database, chatService) {
        ConversationTransfer(context, repository, settings, database, chatService)
    }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(emptySet<Uuid>()) }
    var includeDisplay by remember { mutableStateOf(false) }
    var includePreferences by remember { mutableStateOf(false) }
    var selectedFeatures by remember { mutableStateOf(emptySet<PortableLabSection>()) }
    var choosingExport by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ConversationTransfer.Preview?>(null) }
    var restoreChats by remember { mutableStateOf(true) }
    DisposableEffect(preview) {
        val file = preview?.file
        onDispose { file?.delete() }
    }

    fun perform(task: suspend () -> String) {
        scope.launch {
            busy = true
            status = null
            try {
                status = task().ifBlank { null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                status = e.message?.takeIf { it.length < 160 && it.any { c -> c in '\u4e00'..'\u9fff' } }
                    ?: "無法處理此備份。請確認檔案格式與可用的儲存空間。"
            } catch (_: Exception) {
                status = "無法處理此備份。請確認檔案格式與可用的儲存空間。"
            } finally {
                busy = false
            }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let {
            perform {
                transfer.export(it, selected, includeDisplay, includePreferences, selectedFeatures)
                "已儲存備份。"
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            perform {
                restoreChats = true
                preview = transfer.preview(it)
                ""
            }
        }
    }
    fun applyImport(replace: Boolean) {
        val choice = preview ?: return
        perform {
            val result = try {
                transfer.apply(choice, replace, includeChats = restoreChats)
            } finally {
                withContext(Dispatchers.IO) { choice.file.delete() }
                preview = null
            }
            val summary = when {
                result.conversationCount > 0 -> "已匯入 ${result.conversationCount} 個聊天室。"
                result.warnings.isEmpty() -> "已匯入所選資料。"
                else -> "匯入未能完整套用。"
            }
            (listOf(summary) + result.warnings).joinToString(" ")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("選擇要備份的聊天室和偏好。備份不含模型金鑰、登入憑證和鎖定聊天室。請妥善保管匯出的檔案。")
        TextButton(enabled = !busy, onClick = { choosingExport = true }) { Text("選擇資料並匯出") }
        TextButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
            Text("開啟備份並預覽")
        }
        if (busy) CircularProgressIndicator()
        status?.let { Text(it) }
    }

    if (choosingExport) {
        val conversations = remember(repository) { repository.getAllConversations() }
            .collectAsStateWithLifecycle(initialValue = emptyList()).value
        val available = conversations.filterNot { ChatPrivacy.chatLocked(context, it.id) }
        ZeroAgentAlertDialog(
            onDismissRequest = { choosingExport = false },
            title = { Text("選擇要匯出的資料") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    item { BackupChoice("外觀與閱讀設定", includeDisplay) { includeDisplay = it } }
                    item { BackupChoice("回覆與趣味功能偏好", includePreferences) { includePreferences = it } }
                    items(PortableLabSection.entries.filter { it.available(context) }, key = { it.name }) { section ->
                        BackupChoice(section.label, section in selectedFeatures) { checked ->
                            selectedFeatures = if (checked) selectedFeatures + section else selectedFeatures - section
                        }
                    }
                    if (PortableLabSection.DRAFTS in selectedFeatures) item { Text("會匯出所選聊天室的草稿文字和已存入裝置的附件。") }
                    if (PortableLabSection.RELAY in selectedFeatures) item { Text("接力摘要會綁定原本的聊天室和來源訊息。") }
                    if (PortableLabSection.PROJECTS in selectedFeatures) item { Text("專案會一併匯出參考資料，只保留已選聊天的連結。") }
                    if (PortableLabSection.MEMORY in selectedFeatures) item {
                        Text("一般備份不加密。記憶可能涉及私人資料。匯入只會寫回可辨識的助理或專案，取代只影響備份包含的範圍。")
                    }
                    if (available.isEmpty()) item { Text("目前沒有可匯出的聊天室。") }
                    items(available, key = { it.id.toString() }) { chat ->
                        BackupChoice(chat.title.ifBlank { "未命名聊天" }, chat.id in selected) { checked ->
                            selected = if (!checked) selected - chat.id
                            else if (selected.size < 200) selected + chat.id
                            else selected
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = available.any { it.id in selected } || includeDisplay || includePreferences || selectedFeatures.any { it.available(context) }, onClick = {
                    selected = selected.intersect(available.map { it.id }.toSet())
                    choosingExport = false
                    export.launch("ZeroAgent-聊天備份.zip")
                }) { Text("匯出") }
            },
            dismissButton = { TextButton(onClick = { choosingExport = false }) { Text("取消") } },
        )
    }
    preview?.let { backup ->
        ZeroAgentAlertDialog(
            onDismissRequest = { if (!busy) preview = null },
            title = { Text("確認匯入內容") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${backup.payload.conversations.size} 個聊天室、${backup.attachmentCount} 個附件、${backup.matches} 個相同識別碼。")
                    if (backup.payload.display != null) Text("外觀與閱讀設定會取代本機設定。")
                    if (backup.payload.labPreferences.isNotEmpty()) Text("備份中的回覆與趣味功能偏好會套用到本機。")
                    backup.payload.featureData?.let { Text("功能資料包括 ${it.labels.joinToString("、")}。") }
                    if (backup.payload.featureData != null) Text("合併時保留本機成品、心情卡及批次資料，配方和驗收項目會加入現有清單。取代時套用備份的版本。")
                    if (backup.payload.conversations.isNotEmpty() && (backup.payload.display != null || backup.payload.labPreferences.isNotEmpty() || backup.payload.featureData?.let { it.labels.size > listOfNotNull(it.draftTexts, it.drafts, it.relays).size } == true)) {
                        BackupChoice("一併匯入聊天室及其草稿與接力摘要", restoreChats) { if (!busy) restoreChats = it }
                    }
                    if (restoreChats && backup.matches > 0) Text("合併會另存重複的聊天。取代會覆寫相同的聊天，無法復原。")
                    if (restoreChats && backup.lockedMatches > 0) Text("有 ${backup.lockedMatches} 個相同的聊天室已鎖定。你可以用合併另存。")
                    if (busy) CircularProgressIndicator()
                    if (!busy) TextButton(onClick = { preview = null }) { Text("取消") }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { applyImport(false) }) { Text(if (restoreChats && backup.matches > 0) "合併" else "匯入") }
            },
            dismissButton = if ((!restoreChats || backup.lockedMatches == 0) && ((restoreChats && backup.matches > 0) || backup.payload.featureData != null)) {
                { TextButton(enabled = !busy, onClick = { applyImport(true) }) { Text("取代相同資料") } }
            } else null,
        )
    }
}

@Composable
private fun BackupChoice(title: String, checked: Boolean, setChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = setChecked)
        Text(title)
    }
}
