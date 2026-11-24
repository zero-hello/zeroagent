package me.rerere.zeroagent.ui.pages.lab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import me.rerere.zeroagent.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.zeroagent.data.db.entity.MemoryEntity
import me.rerere.zeroagent.data.lab.LabMemoryReview
import me.rerere.zeroagent.data.lab.LabPreferences
import me.rerere.zeroagent.data.lab.MemoryChange
import me.rerere.zeroagent.data.productivity.ProjectStore
import me.rerere.zeroagent.data.repository.MemoryRepository
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemoryDrawerWorkspace(modifier: Modifier) {
    val context = LocalContext.current
    val repository: MemoryRepository = koinInject()
    val scope = rememberCoroutineScope()
    val records by remember(repository) { repository.allMemoriesFlow() }.collectAsStateWithLifecycle(emptyList())
    val projects by remember(context) { ProjectStore.get(context).projects }.collectAsStateWithLifecycle()
    val pendingJson by remember(context) { LabPreferences.flow(context, LabMemoryReview.PENDING) }
        .collectAsStateWithLifecycle(LabPreferences.get(context, LabMemoryReview.PENDING))
    val paused by remember(context) { LabPreferences.flow(context, LabMemoryReview.CAPTURE_PAUSED, "false") }
        .collectAsStateWithLifecycle(LabPreferences.get(context, LabMemoryReview.CAPTURE_PAUSED, "false"))
    val pending = remember(pendingJson) { LabMemoryReview.pending(pendingJson) }
    fun scopeName(key: String) = when (key) {
        MemoryRepository.GLOBAL_MEMORY_ID -> "全域記憶"
        else -> projects.firstOrNull { it.id == key }?.name ?: "模型記憶"
    }
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var removing by remember { mutableStateOf<MemoryEntity?>(null) }
    var text by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun write(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
                notice = "變更已保存。"
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                notice = error.message ?: "無法保存變更。"
            } finally {
                busy = false
            }
        }
    }
    val controls: @Composable () -> Unit = {
        Text("這裡顯示本機記憶庫與待確認的變更。關閉 Lab 入口後，現有記憶與待確認建議仍會保留。")
        ListItem(
            headlineContent = { Text("暫停 AI 新增或修改記憶") },
            supportingContent = { Text("暫停捕捉後仍可查看和編輯現有記憶。需要排除某一筆時，可選擇刪除並阻擋相同內容。") },
            trailingContent = { Switch(paused == "true", { LabPreferences.set(context, LabMemoryReview.CAPTURE_PAUSED, it.toString()) }) },
        )
        if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary)
    }
    val pendingCard: @Composable (MemoryChange) -> Unit = { change ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(scopeName(change.scope), style = MaterialTheme.typography.labelLarge)
                change.original?.let { Text("目前：$it") }
                Text(change.proposed?.let { "建議：$it" } ?: "建議移除這筆記憶")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(enabled = !busy, onClick = { write { LabMemoryReview.apply(context, change, repository) } }) {
                        Text(if (change.original == null) "保存" else if (change.proposed == null) "移除" else "取代")
                    }
                    TextButton(enabled = !busy, onClick = { LabMemoryReview.dismiss(context, change) }) { Text("保留原狀") }
                    if (change.proposed != null && change.conversationId != null) TextButton(enabled = !busy, onClick = {
                        LabMemoryReview.chooseForNextReply(context, change)
                        notice = "下次在原聊天室提問時會參考這個版本一次，長期記憶不會變更。"
                    }) { Text("下次僅用一次") }
                }
            }
        }
    }
    val recordCard: @Composable (MemoryEntity) -> Unit = { record ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(scopeName(record.assistantId), style = MaterialTheme.typography.labelLarge)
                Text(record.content)
                Row {
                    TextButton(enabled = !busy, onClick = { editing = record; text = record.content }) { Text("編輯") }
                    TextButton(enabled = !busy, onClick = { removing = record }) { Text("移除") }
                }
            }
        }
    }
    val sortedRecords = remember(records) { records.sortedBy { it.assistantId } }
    var selection by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSuggestion = pending.firstOrNull { "suggestion:${it.key}" == selection }
    val selectedRecord = records.firstOrNull { "memory:${it.id}" == selection }
    val listScroll = rememberScrollState()
    val windowDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    BoxWithConstraints(modifier) {
        val useTwoPanes = maxWidth >= 840.dp && windowDirective.maxHorizontalPartitions >= 2
        if (useTwoPanes) {
            ListDetailPaneScaffold(
                directive = windowDirective,
                value = ThreePaneScaffoldValue(
                    primary = PaneAdaptedValue.Expanded,
                    secondary = PaneAdaptedValue.Expanded,
                    tertiary = PaneAdaptedValue.Hidden,
                ),
                modifier = Modifier.fillMaxSize(),
                listPane = {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(listScroll).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        controls()
                        if (pending.isNotEmpty()) {
                            Text("待確認的新增與衝突", style = MaterialTheme.typography.titleMedium)
                        }
                        pending.forEach { change ->
                            val key = "suggestion:${change.key}"
                            MemoryListCard(
                                scopeName(change.scope),
                                change.proposed ?: "建議移除這筆記憶",
                                selected = key == selection,
                                onClick = { selection = key },
                            )
                        }
                        Text("已保存記憶", style = MaterialTheme.typography.titleMedium)
                        if (sortedRecords.isEmpty()) Text("目前沒有保存的記憶。")
                        sortedRecords.forEach { record ->
                            val key = "memory:${record.id}"
                            MemoryListCard(
                                scopeName(record.assistantId),
                                record.content,
                                selected = key == selection,
                                onClick = { selection = key },
                            )
                        }
                    }
                },
                detailPane = {
                    androidx.compose.runtime.key(selection) {
                        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.TopCenter) {
                            when {
                                selectedSuggestion != null -> Column(Modifier.verticalScroll(rememberScrollState())) {
                                    pendingCard(selectedSuggestion)
                                }
                                selectedRecord != null -> Column(Modifier.verticalScroll(rememberScrollState())) {
                                    recordCard(selectedRecord)
                                }
                                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        stringResource(R.string.adaptive_memory_choose_entry),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(listScroll).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    controls()
                    if (pending.isNotEmpty()) Text("待確認的新增與衝突", style = MaterialTheme.typography.titleMedium)
                    pending.forEach { pendingCard(it) }
                    Text("已保存記憶", style = MaterialTheme.typography.titleMedium)
                    if (sortedRecords.isEmpty()) Text("目前沒有保存的記憶。")
                    sortedRecords.forEach { recordCard(it) }
                }
            }
        }
    }
    editing?.let { record ->
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("編輯記憶") }, text = {
            WorkspaceField(text, { text = it }, "記憶內容", minLines = 3, cap = 1200)
        }, confirmButton = {
            TextButton(enabled = text.isNotBlank() && !busy, onClick = {
                val content = text.trim()
                editing = null
                write { repository.updateContent(record.id, content) }
            }) { Text("保存") }
        }, dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } })
    }
    removing?.let { record ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("移除這筆記憶？") },
            text = { Text("此操作無法復原。若選擇阻擋，裝置會保存內容的雜湊，用來擋下完全相同的記憶，改寫後的內容仍可能被提出為建議。") },
            confirmButton = { TextButton(onClick = {
                removing = null
                write { repository.deleteMemory(record.id); LabMemoryReview.block(context, record.content) }
            }) { Text("刪除並阻擋") } }, dismissButton = {
                Row {
                    TextButton(onClick = { removing = null }) { Text("取消") }
                    TextButton(onClick = { removing = null; write { repository.deleteMemory(record.id) } }) { Text("只刪除") }
                }
            })
    }
}


@Composable
private fun MemoryListCard(title: String, preview: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(preview, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
