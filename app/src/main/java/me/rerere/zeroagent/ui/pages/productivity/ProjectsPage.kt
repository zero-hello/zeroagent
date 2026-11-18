package me.rerere.zeroagent.ui.pages.productivity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.getCurrentAssistant
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.productivity.ChatProject
import me.rerere.zeroagent.data.productivity.ProjectStore
import me.rerere.zeroagent.data.repository.ConversationRepository
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import me.rerere.zeroagent.ui.context.LocalNavController
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun ProjectsPage(currentConversationId: String? = null) {
    val context = LocalContext.current
    val loaded = remember { runCatching { ProjectStore.get(context) } }
    if (loaded.isFailure) {
        Scaffold(topBar = { TopAppBar(title = { Text("專案空間") }, navigationIcon = { BackButton() }) }) {
            Text("專案資料無法讀取，原始檔案已保留，請先備份 App 資料。", Modifier.padding(it).padding(24.dp))
        }
        return
    }
    val store = loaded.getOrThrow()
    val projects by store.projects.collectAsStateWithLifecycle()
    val settingsStore: SettingsStore = koinInject()
    val repository: ConversationRepository = koinInject()
    val conversationFlow = remember(repository) { repository.getAllConversations() }
    val conversations by conversationFlow.collectAsStateWithLifecycle(emptyList())
    val nav = LocalNavController.current
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = projects.firstOrNull { it.id == selectedId }
    var name by rememberSaveable { mutableStateOf("") }
    var instructions by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { error = failure.message ?: "操作失敗，資料未變更。" }
            finally { busy = false }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val projectId = selectedId
        if (uri != null && projectId != null) perform {
            val document = store.importDocument(uri)
            withContext(Dispatchers.IO) {
                val latest = store.projects.value.first { it.id == projectId }
                store.save(latest.copy(documents = latest.documents + document))
            }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(selected?.name ?: "專案空間") }, navigationIcon = { BackButton() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("專案保存自己的指示、對話、文件與記憶。文件在本機解析與檢索；只有相關片段會隨問題傳給你選用的供應商。專案不讀取其他專案或全域記憶。")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (selected == null) {
                OutlinedTextField(name, { name = it }, label = { Text("新專案名稱") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(instructions, { instructions = it }, label = { Text("共同指示與目標") },
                    minLines = 3, modifier = Modifier.fillMaxWidth())
                Button(enabled = name.isNotBlank() && !busy, onClick = { perform {
                    val project = ChatProject(name = name.trim(), instructions = instructions.trim())
                    settingsStore.update { settings ->
                        val profile = settings.getCurrentAssistant().copy(id = Uuid.parse(project.id), name = project.name,
                            enableMemory = true, useGlobalMemory = false, enableRecentChatsReference = false,
                            workspaceId = null, coldMemoryEnabled = false)
                        settings.copy(assistants = settings.assistants + profile)
                    }
                    withContext(Dispatchers.IO) { store.save(project) }
                    selectedId = project.id; name = project.name; instructions = project.instructions
                } }) { Text("建立專案") }
                if (projects.isEmpty()) Text("目前沒有專案。你可以為工作、課程或旅行分別建立空間。")
                projects.forEach { project ->
                    Card(onClick = { selectedId = project.id; instructions = project.instructions; name = project.name },
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(project.name, style = MaterialTheme.typography.titleLarge)
                            Text("${project.conversationIds.size} 個對話 · ${project.documents.size} 份文件")
                        }
                    }
                }
            } else {
                TextButton(onClick = { selectedId = null; name = ""; instructions = "" }) { Text("返回專案清單") }
                OutlinedTextField(name.ifBlank { selected.name }, { name = it }, label = { Text("專案名稱") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(instructions, { instructions = it }, label = { Text("共同指示與目標") }, minLines = 3,
                    modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy, onClick = { perform {
                    withContext(Dispatchers.IO) { store.save(selected.copy(name = name.trim().ifBlank { selected.name }, instructions = instructions)) }
                } }) { Text("保存指示") }
                Button(enabled = !busy, onClick = { perform {
                    val conversation = Conversation.ofId(Uuid.random(), assistantId = Uuid.parse(selected.id))
                    repository.insertConversation(conversation)
                    withContext(Dispatchers.IO) { store.save(selected.copy(conversationIds = selected.conversationIds + conversation.id.toString())) }
                    nav.navigate(Screen.Chat(conversation.id.toString()))
                } }) { Text("開啟專案新對話") }
                if (currentConversationId != null && store.forConversation(currentConversationId) == null) {
                    OutlinedButton(enabled = !busy, onClick = { perform {
                        withContext(Dispatchers.IO) { store.save(selected.copy(conversationIds = selected.conversationIds + currentConversationId)) }
                    } }) { Text("將目前對話加入此專案") }
                }
                TextButton(onClick = { nav.navigate(Screen.AssistantMemory(selected.id)) }) { Text("查看、編輯與刪除專案記憶") }
                HorizontalDivider()
                Text("專案文件與知識庫", style = MaterialTheme.typography.titleLarge)
                Text("支援 PDF、DOCX、PPTX、EPUB、TXT、Markdown、CSV 等文字檔；每份最多 12 MB／50 萬字元。檢索引用以檔名與片段編號顯示。")
                OutlinedButton(enabled = !busy, onClick = { importer.launch(arrayOf("application/pdf", "application/epub+zip",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation", "text/*", "application/json")) }) { Text("匯入文件") }
                selected.documents.forEach { doc ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(doc.name, style = MaterialTheme.typography.titleMedium)
                            Text("${doc.text.length} 字元 · 本機索引")
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Switch(checked = doc.included, enabled = !busy, onCheckedChange = { included -> perform {
                                    withContext(Dispatchers.IO) { store.save(selected.copy(documents = selected.documents.map {
                                        if (it.id == doc.id) it.copy(included = included) else it
                                    })) }
                                } })
                                Text(if (doc.included) "允許用於專案回答" else "已排除此文件")
                            }
                            TextButton(enabled = !busy, onClick = { perform {
                                withContext(Dispatchers.IO) { store.save(selected.copy(documents = selected.documents.filterNot { it.id == doc.id })) }
                            } }) { Text("刪除文件") }
                        }
                    }
                }
                Text("專案對話", style = MaterialTheme.typography.titleLarge)
                conversations.filter { it.id.toString() in selected.conversationIds }.forEach { conversation ->
                    OutlinedButton(onClick = { nav.navigate(Screen.Chat(conversation.id.toString())) }) {
                        Text(conversation.title.ifBlank { "新對話" })
                    }
                }
                TextButton(enabled = !busy, onClick = { deleteConfirm = true }) { Text("移除專案") }
            }
        }
    }
    if (deleteConfirm && selected != null) AlertDialog(onDismissRequest = { deleteConfirm = false },
        title = { Text("移除這個專案？") },
        text = { Text("專案指示與文件索引將刪除。對話與記憶仍保留；原專案對話會停用後續長期記憶。請先在記憶頁清除不需要的資料。") },
        confirmButton = { TextButton(onClick = { deleteConfirm = false; perform {
            withContext(Dispatchers.IO) { store.delete(selected.id) }; selectedId = null; name = ""; instructions = ""
        } }) { Text("移除") } }, dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("取消") } })
}
