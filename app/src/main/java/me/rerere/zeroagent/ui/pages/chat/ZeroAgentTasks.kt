package me.rerere.zeroagent.ui.pages.chat

import me.rerere.zeroagent.utils.localizedErrorMessage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.rerere.zeroagent.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.agentrun.AgentRun
import me.rerere.zeroagent.data.agentrun.AgentRunRepository
import me.rerere.zeroagent.ui.context.LocalNavController
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import me.rerere.zeroagent.ui.components.ui.ZeroAgentCard as Card
import me.rerere.zeroagent.ui.components.ui.ZeroAgentOutlinedCard as OutlinedCard

internal data class TaskSnapshot(val runs: List<AgentRun> = emptyList(), val loading: Boolean = false, val unavailable: Boolean = false)
internal val activeRunStates = setOf("running", "queued")
internal fun runStatusLabel(status: String, context: Context): String = context.getString(when (status) {
    "queued" -> R.string.zeroagent6_status_queued
    "running" -> R.string.zeroagent6_status_running
    "awaiting_approval" -> R.string.zeroagent6_status_awaiting_approval
    "succeeded" -> R.string.zeroagent6_status_completed
    "failed" -> R.string.zeroagent6_status_failed
    "cancelled" -> R.string.zeroagent6_status_cancelled
    "process_lost" -> R.string.zeroagent6_status_interrupted
    else -> R.string.zeroagent6_status_unknown
})
internal fun runKindLabel(kind: String, context: Context): String = context.getString(when (kind) {
    "cron" -> R.string.zeroagent6_kind_scheduled
    "workflow" -> R.string.zeroagent6_kind_workflow
    "subagent" -> R.string.zeroagent6_kind_subagent
    "telegram" -> R.string.zeroagent6_kind_telegram
    "external_automation" -> R.string.zeroagent6_kind_automation
    "agent_def_write" -> R.string.zeroagent6_kind_assistant_update
    "price_table" -> R.string.zeroagent6_kind_pricing_update
    "model_write" -> R.string.zeroagent6_kind_model_update
    else -> R.string.zeroagent6_kind_task
})
internal fun runTitle(run: AgentRun, context: Context): String {
    val metadata = runCatching { Json.parseToJsonElement(run.metadataJson ?: "{}") as? JsonObject }.getOrNull()
    val label = listOf("label", "name", "title").firstNotNullOfOrNull { (metadata?.get(it) as? JsonPrimitive)?.takeIf { value -> value.isString }?.content?.takeIf { value -> value.isNotBlank() } }
    return label ?: "${runKindLabel(run.kind, context)} · ${run.domainId}"
}
private fun taskDate(ms: Long, context: Context): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, context.resources.configuration.locales[0]).format(Date(ms))
@Composable
internal fun rememberTaskSnapshot(): TaskSnapshot {
    val repo = koinInject<AgentRunRepository>()
    val flow = remember(repo) { repo.observeRecent(200).map { TaskSnapshot(it) }.catch { emit(TaskSnapshot(unavailable = true)) } }
    return flow.collectAsStateWithLifecycle(initialValue = TaskSnapshot(loading = true)).value
}
@Composable
internal fun ZeroAgentTaskOverview() {
    val context = LocalContext.current
    val snapshot = rememberTaskSnapshot()
    val nav = LocalNavController.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.zeroagent6_workspace), Modifier.padding(end = 12.dp), style = MaterialTheme.typography.titleSmall)
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { nav.navigate(Screen.TaskCenter) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(stringResource(R.string.zeroagent6_tasks)) }
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { nav.navigate(Screen.Workspaces) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(stringResource(R.string.zeroagent_settings_projects)) }
            }
            Text(when {
                snapshot.loading -> stringResource(R.string.zeroagent6_recent_loading)
                snapshot.unavailable -> stringResource(R.string.zeroagent6_recent_unavailable)
                snapshot.runs.isEmpty() -> stringResource(R.string.zeroagent6_recent_empty)
                else -> stringResource(R.string.zeroagent6_recent_count, snapshot.runs.count { it.status in activeRunStates }, snapshot.runs.count { it.status == "awaiting_approval" })
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroAgentTaskCenterPage() {
    val context = LocalContext.current
    val nav = LocalNavController.current
    val snapshot = rememberTaskSnapshot()
    val repository = koinInject<AgentRunRepository>()
    var children by remember { mutableStateOf<List<AgentRun>?>(null) }
    var childrenUnavailable by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var expanded by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(expanded, snapshot.runs.firstOrNull { it.id == expanded }?.updatedAtMs) {
        children = null
        childrenUnavailable = false
        expanded?.let { id ->
            try { children = repository.getChildren(id) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { childrenUnavailable = true }
        }
    }
    val runs = snapshot.runs.filter { run ->
        val matches = when (filter) {
            "Active" -> run.status in activeRunStates
            "Waiting" -> run.status == "awaiting_approval"
            "Finished" -> run.status in setOf("succeeded", "failed", "cancelled", "process_lost")
            else -> true
        }
        matches && (query.isBlank() || listOf(runTitle(run, context), runKindLabel(run.kind, context), runStatusLabel(run.status, context), run.id, run.domainId).any { it.contains(query, ignoreCase = true) })
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.zeroagent6_tasks)) }, navigationIcon = { me.rerere.zeroagent.ui.components.nav.BackButton() }, colors = me.rerere.zeroagent.ui.theme.CustomColors.topBarColors) }) { inset ->
        LazyColumn(Modifier.padding(inset).fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.zeroagent6_recent_activity), style = MaterialTheme.typography.headlineSmall) }
            item { Text(stringResource(R.string.zeroagent6_task_history_hint), style = MaterialTheme.typography.bodyMedium) }
            item { OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.zeroagent6_task_search)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Active", "Waiting", "Finished").forEach { name -> FilterChip(selected = filter == name, onClick = { filter = name }, label = { Text(stringResource(when (name) {
                    "Active" -> R.string.zeroagent6_filter_active
                    "Waiting" -> R.string.zeroagent6_filter_waiting
                    "Finished" -> R.string.zeroagent6_filter_finished
                    else -> R.string.zeroagent6_filter_all
                })) }) }
            } }
            when {
                snapshot.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                snapshot.unavailable -> item { Text(stringResource(R.string.zeroagent6_task_unavailable)) }
                runs.isEmpty() -> item { Text(if (snapshot.runs.isEmpty()) stringResource(R.string.zeroagent6_task_empty) else stringResource(R.string.zeroagent6_task_no_match)) }
            }
            items(runs, key = { it.id }) { run ->
                Card(onClick = { expanded = if (expanded == run.id) null else run.id }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(runTitle(run, context), style = MaterialTheme.typography.titleMedium)
                        Text(runStatusLabel(run.status, context), color = if (run.status in setOf("failed", "process_lost")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.zeroagent6_task_updated, taskDate(run.updatedAtMs, context)), style = MaterialTheme.typography.bodySmall)
                        if (expanded == run.id) {
                            SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.zeroagent6_task_id, run.id), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.zeroagent6_task_source_id, run.domainId), style = MaterialTheme.typography.bodySmall)
                                run.parentRunId?.let { Text(stringResource(R.string.zeroagent6_task_parent, it), style = MaterialTheme.typography.bodySmall) }
                                run.lastError?.let { Text(context.localizedErrorMessage(raw = it), color = MaterialTheme.colorScheme.error) }
                                Text(stringResource(R.string.zeroagent6_recorded_activity), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.zeroagent6_task_created, taskDate(run.createdAtMs, context)))
                                run.startedAtMs?.let { Text(stringResource(R.string.zeroagent6_task_started, taskDate(it, context))) }
                                run.finishedAtMs?.let { Text(stringResource(R.string.zeroagent6_task_finished, taskDate(it, context))) }
                                Text(stringResource(R.string.zeroagent6_task_latest, runStatusLabel(run.status, context), taskDate(run.updatedAtMs, context)))
                                run.metadataJson?.takeIf { it.isNotBlank() && it != "{}" }?.let { Text(stringResource(R.string.zeroagent6_task_details, it), style = MaterialTheme.typography.bodySmall) }
                                when {
                                    childrenUnavailable -> Text(stringResource(R.string.zeroagent6_children_unavailable))
                                    children == null -> Text(stringResource(R.string.zeroagent6_children_loading))
                                    children!!.isEmpty() -> Text(stringResource(R.string.zeroagent6_children_empty))
                                    else -> children!!.forEach { Text(stringResource(R.string.zeroagent6_child_summary, runTitle(it, context), runStatusLabel(it.status, context)), style = MaterialTheme.typography.bodySmall) }
                                }
                                Text(stringResource(R.string.zeroagent6_ledger_hint), style = MaterialTheme.typography.bodySmall)
                            } }
                            if (run.kind != "subagent") {
                                TextButton(shapes = ButtonDefaults.shapes(), onClick = { nav.navigate(when (run.kind) {
                                    "workflow" -> Screen.WorkflowDetail(run.domainId)
                                    "cron" -> Screen.SettingScheduledJobs
                                    else -> Screen.SettingDoctor
                                }) }) { Text(stringResource(R.string.zeroagent6_task_controls)) }
                            }
                        } else Text(stringResource(R.string.zeroagent6_task_expand), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
