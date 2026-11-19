package me.rerere.zeroagent.ui.pages.productivity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.*
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.datastore.findProvider
import me.rerere.zeroagent.data.datastore.getCurrentChatModel
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import me.rerere.zeroagent.ui.context.LocalSettings
import org.koin.compose.koinInject
import java.time.Instant

@Composable
fun DocumentWorkbenchPage() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val manager: ProviderManager = koinInject()
    val model = settings.getCurrentChatModel()
    val provider = model?.findProvider(settings.providers)?.takeIf { it.enabled }
    var task by rememberSaveable { mutableStateOf("") }
    var document by remember { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf("Markdown") }
    var generatedAt by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val mime = if (format == "CSV") "text/csv" else "text/markdown"
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        if (uri != null) scope.launch {
            try {
                val snapshot = document
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(snapshot) }
                        ?: error("無法開啟檔案")
                }
                notice = "已匯出至選擇的位置。"
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notice = "匯出失敗，請檢查目的地權限與空間。" }
        }
    }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    Scaffold(topBar = { TopAppBar(title = { Text("文件與表格工作台") }, navigationIcon = { BackButton() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("用目前模型生成內容，修改後匯出 Markdown 文件或 CSV 表格。CSV 可用試算表軟體開啟；此頁不產生 DOCX 或 PPTX。")
            Text("模型：${provider?.name ?: "未設定"} · ${model?.displayName.orEmpty()}")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Markdown", "CSV").forEach { choice ->
                    FilterChip(selected = format == choice, enabled = !running, onClick = { format = choice }, label = { Text(choice) })
                }
            }
            OutlinedTextField(task, { task = it }, label = { Text("文件需求、原始資料及來源") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(enabled = !running && provider != null && model != null && task.isNotBlank(), onClick = {
                val selectedModel = model ?: return@Button
                val selectedProvider = provider ?: return@Button
                running = true; notice = null
                val selectedFormat = format
                job = scope.launch {
                    try {
                        val result = manager.getProviderByType(selectedProvider).generateText(selectedProvider,
                            listOf(UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text(
                                "請依資料生成 $selectedFormat 內容，只輸出內容本身，不加程式碼圍欄。保留使用者提供的來源；未知資訊請標記未知，勿編造引用。CSV 請正確處理逗號與引號。\n$task")))),
                            TextGenerationParams(model = selectedModel, customHeaders = selectedModel.customHeaders, customBody = selectedModel.customBodies))
                        document = result.message.toText()
                        generatedAt = Instant.now().toString()
                        source = selectedProvider.name + " / " + selectedModel.displayName
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { notice = "生成失敗，請檢查供應商、模型與帳戶額度。" }
                    finally { running = false }
                }
            }) { Text(if (running) "生成中…" else "生成草稿") }
            if (running) TextButton(onClick = { job?.cancel() }) { Text("取消生成") }
            if (generatedAt.isNotBlank()) Text("生成時間：$generatedAt\n模型：$source", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(document, { document = it }, label = { Text("編輯內容") }, minLines = 8, modifier = Modifier.fillMaxWidth())
            Text("內容預覽", style = MaterialTheme.typography.titleLarge)
            SelectionContainer { Text(document.ifBlank { "生成或輸入內容後，可在這裡預覽並選取。" }) }
            Button(enabled = document.isNotBlank(), onClick = {
                save.launch("ZeroAgent-${System.currentTimeMillis()}.${if (format == "CSV") "csv" else "md"}")
            }) { Text("匯出檔案") }
            notice?.let { Text(it) }
        }
    }
}
