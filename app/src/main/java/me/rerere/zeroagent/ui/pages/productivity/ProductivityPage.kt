package me.rerere.zeroagent.ui.pages.productivity

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.widget.QuickActionsWidget
import me.rerere.zeroagent.data.productivity.ProjectStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProductivityPage(conversationId: String? = null) {
    val nav = LocalNavController.current
    val context = LocalContext.current
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val projectStore = remember { runCatching { ProjectStore.get(context) }.getOrNull() }
    var noMemory by remember(conversationId) { mutableStateOf(conversationId?.let { projectStore?.memoryDisabled(it) } ?: false) }
    var labels by remember(conversationId) { mutableStateOf(conversationId?.let { projectStore?.tags(it)?.joinToString(", ") }.orEmpty()) }
    Scaffold(topBar = { TopAppBar(title = { Text("效率工具") }, navigationIcon = { BackButton() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (conversationId != null && projectStore != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("此對話不使用長期記憶", style = MaterialTheme.typography.titleMedium)
                        Text("從下一次送出起，不讀取或新增長期記憶與近期對話參考；原有歷史和已保存記憶仍保留。")
                        Switch(checked = noMemory, onCheckedChange = { disabled -> scope.launch {
                            try {
                                withContext(Dispatchers.IO) { projectStore.setMemoryDisabled(conversationId, disabled) }
                                noMemory = disabled
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) { notice = "記憶偏好保存失敗，請重試。" }
                        } })
                        OutlinedTextField(labels, { labels = it }, label = { Text("對話標籤，以逗號分隔") }, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { scope.launch {
                            try {
                                withContext(Dispatchers.IO) { projectStore.setTags(conversationId,
                                    labels.split(',', '，', '\n').map { it.trim() }.filter { it.isNotBlank() }.toSet()) }
                                notice = "標籤已保存，可在全域搜尋使用 tag:標籤。"
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) { notice = "標籤保存失敗。" }
                        } }) { Text("保存對話標籤") }
                    }
                }
            }
            ToolEntry("專案空間與文件知識庫", "集中管理指示、對話、文件與專案記憶，回答引用實際文件片段。") {
                nav.navigate(Screen.Projects(conversationId))
            }
            ToolEntry("多模型比較", "選擇 2 至 4 個已啟用模型，同時回答相同問題，查看結果、耗時與回報用量。") {
                nav.navigate(Screen.ModelCompare)
            }
            ToolEntry("依任務選模", "依圖片理解、本機執行、上下文與可用價格資料挑選模型，送出前可再確認。") {
                nav.navigate(Screen.AutoModel)
            }
            ToolEntry("文件與表格工作台", "生成、編輯及匯出 Markdown 文件與 CSV 表格。") {
                nav.navigate(Screen.DocumentWorkbench)
            }
            ToolEntry("相機與螢幕協助", "即時相機或螢幕預覽，按下詢問後才傳送一張畫面給支援視覺的模型。") {
                nav.navigate(Screen.VisualAssist)
            }
            ToolEntry("對話分享連結", "預覽文字，再透過已設定的私人 S3 儲存桶建立有期限、可撤回的分享。") {
                nav.navigate(Screen.ConversationLinks(conversationId))
            }
            ToolEntry("提示範本", "建立與匯入可重複使用的提示詞。") { nav.navigate(Screen.Prompts) }
            ToolEntry("搜尋舊對話", "搜尋已保存的訊息，回到原對話查看內容。") { nav.navigate(Screen.MessageSearch) }
            ToolEntry("任務與提醒", "查看已建立的任務、排程與執行狀態。") { nav.navigate(Screen.TaskCenter) }
            val widgets = AppWidgetManager.getInstance(context)
            if (widgets.isRequestPinAppWidgetSupported) {
                OutlinedButton(onClick = {
                    val accepted = widgets.requestPinAppWidget(ComponentName(context, QuickActionsWidget::class.java), null, null)
                    notice = if (accepted) "已請啟動器顯示新增小工具的確認畫面。" else "啟動器未接受要求，請從桌面的小工具清單新增。"
                }) { Text("新增主畫面小工具") }
            } else Text("可從桌面長按空白處，在小工具清單加入 ZeroAgent；是否支援由啟動器決定。")
            notice?.let { Text(it) }
        }
    }
}

@Composable
private fun ToolEntry(title: String, description: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
