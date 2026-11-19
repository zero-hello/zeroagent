package me.rerere.zeroagent.ui.pages.productivity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.productivity.*
import me.rerere.zeroagent.data.repository.ConversationRepository
import me.rerere.zeroagent.data.sync.s3.S3Client
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.context.LocalSettings
import org.koin.compose.koinInject
import java.time.Instant
import kotlin.uuid.Uuid

@Composable
fun ConversationLinksPage(conversationId: String? = null) {
    val context = LocalContext.current
    val nav = LocalNavController.current
    val settings = LocalSettings.current
    val repository: ConversationRepository = koinInject()
    val http: HttpClient = koinInject()
    val store = remember { ProjectStore.get(context) }
    val config = remember(settings.s3Config) { runCatching { ConversationLinks.checkedConfig(settings.s3Config) } }
    var messages by remember { mutableStateOf<List<UIMessage>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var title by remember { mutableStateOf("分享對話") }
    var preview by remember { mutableStateOf("") }
    var prepared by remember { mutableStateOf(false) }
    var privateBucket by remember { mutableStateOf(false) }
    var expires by remember { mutableIntStateOf(3600) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var records by remember { mutableStateOf(store.shareLinks()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(conversationId) {
        try {
            val conversation = conversationId?.let { repository.getConversationById(Uuid.parse(it)) }
            title = conversation?.title?.ifBlank { "分享對話" } ?: "分享對話"
            messages = conversation?.currentMessages.orEmpty().filter {
                it.role in listOf(MessageRole.USER, MessageRole.ASSISTANT) && it.toText().isNotBlank()
            }
            selected = messages.map { it.id.toString() }.toSet()
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { notice = "對話讀取失敗，未上傳內容。" }
    }
    fun copyUrl(link: ConversationLink) {
        try {
            val url = ConversationLinks.url(config.getOrThrow(), link)
            val clip = ClipData.newPlainText("ZeroAgent 分享連結", url)
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
            notice = "連結已複製；持有連結的人可在到期前閱讀。"
        } catch (_: Exception) { notice = "無法複製：請確認連結未到期，且目前 S3 設定對應原儲存位置。" }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("對話分享連結") }, navigationIcon = { BackButton() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("先選訊息並預覽，確認後才上傳至你設定的 S3 儲存桶。僅包含文字，不上傳附件、工具輸出或 App 金鑰。")
            Text("需要私人儲存桶。期限限制連結存取，檔案仍保留直到撤回；公開桶不適用期限保護。撤回無法刪除他人已下載的副本。")
            if (config.isFailure) Text("請先至備份頁設定 HTTPS 的 S3 服務、區域、私人儲存桶及具備讀寫刪除權限的金鑰。", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { nav.navigate(Screen.Backup) }) { Text("設定 S3 儲存服務") }
            if (messages.isEmpty()) Text("從對話附件選單的「效率工具」進入，即可選取該對話的訊息。下方仍可管理已建立的連結。")
            messages.forEach { message ->
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(checked = message.id.toString() in selected, enabled = !busy, onCheckedChange = { checked ->
                        selected = if (checked) selected + message.id.toString() else selected - message.id.toString()
                        prepared = false
                    })
                    Text((if (message.role == MessageRole.USER) "你：" else "AI：") + message.toText().take(160), Modifier.weight(1f).padding(top = 12.dp))
                }
            }
            OutlinedButton(enabled = !busy && selected.isNotEmpty(), onClick = {
                preview = messages.filter { it.id.toString() in selected }.joinToString("\n\n") {
                    (if (it.role == MessageRole.USER) "你：\n" else "AI：\n") + it.toText()
                }
                prepared = true
            }) { Text("預覽選取內容") }
            if (prepared) {
                OutlinedTextField(title, { title = it }, enabled = !busy, label = { Text("分享標題") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(preview, { preview = it }, enabled = !busy, label = { Text("發布內容，可先刪除私人文字") }, minLines = 6, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(900 to "15 分鐘", 3600 to "1 小時", 86400 to "1 天", 604800 to "7 天").forEach { (seconds, label) ->
                        FilterChip(expires == seconds, enabled = !busy, onClick = { expires = seconds }, label = { Text(label) })
                    }
                }
                Row {
                    Checkbox(privateBucket, enabled = !busy, onCheckedChange = { privateBucket = it })
                    Text("已確認儲存桶為私人，且同意上傳以上內容", Modifier.weight(1f).padding(top = 12.dp))
                }
                Button(enabled = !busy && config.isSuccess && privateBucket && preview.isNotBlank(), onClick = {
                    busy = true; notice = null
                    val selectedConfig = config.getOrThrow()
                    val now = Instant.now().epochSecond
                    val record = ConversationLink(title = title, endpoint = selectedConfig.endpoint, bucket = selectedConfig.bucket,
                        region = selectedConfig.region, pathStyle = selectedConfig.pathStyle, createdAt = now, expiresAt = now + expires)
                    val payload = ConversationLinks.html(title, preview)
                    scope.launch {
                        val client = S3Client(selectedConfig, http)
                        var recorded = false
                        try {
                            client.putObject(record.objectKey, payload, "text/html; charset=utf-8").getOrThrow()
                            withContext(NonCancellable + Dispatchers.IO) { store.saveLink(record); recorded = true }
                            records = store.shareLinks(); copyUrl(record)
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { notice = "上傳或紀錄保存失敗，請檢查 S3 連線和權限；請到儲存桶檢查 zeroagent-shares 下是否留下檔案。" }
                        finally {
                            if (!recorded) withContext(NonCancellable) { client.deleteObject(record.objectKey) }
                            busy = false
                        }
                    }
                }) { Text(if (busy) "處理中…" else "確認上傳並建立連結") }
            }
            notice?.let { Text(it) }
            HorizontalDivider()
            Text("已建立的分享", style = MaterialTheme.typography.titleLarge)
            records.sortedByDescending { it.createdAt }.forEach { link ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(link.title, style = MaterialTheme.typography.titleMedium)
                        Text("到期：${Instant.ofEpochSecond(link.expiresAt)}")
                        Text("${link.endpoint} / ${link.bucket}", style = MaterialTheme.typography.bodySmall)
                        val sameLocation = config.getOrNull()?.let { ConversationLinks.matches(it, link) } == true
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !busy && sameLocation && link.expiresAt > Instant.now().epochSecond, onClick = { copyUrl(link) }) { Text("複製連結") }
                            TextButton(enabled = !busy && sameLocation, onClick = {
                                busy = true
                                val current = config.getOrThrow()
                                scope.launch {
                                    try {
                                        S3Client(current, http).deleteObject(link.objectKey).getOrThrow()
                                        withContext(NonCancellable + Dispatchers.IO) { store.removeLink(link.id) }
                                        records = store.shareLinks(); notice = "已撤回並刪除遠端分享檔案。"
                                    } catch (cancel: CancellationException) { throw cancel }
                                    catch (_: Exception) { notice = "撤回失敗，連結紀錄已保留，請檢查原儲存桶與刪除權限後重試。" }
                                    finally { busy = false }
                                }
                            }) { Text("撤回連結並刪除檔案") }
                        }
                        if (!sameLocation) Text("請恢復此連結原有的 S3 設定後再管理。")
                    }
                }
            }
        }
    }
}
