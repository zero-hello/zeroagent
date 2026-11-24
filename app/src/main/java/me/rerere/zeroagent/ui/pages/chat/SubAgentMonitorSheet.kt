package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.R
import me.rerere.zeroagent.service.ChatService
import me.rerere.zeroagent.subagent.SubAgentRun
import me.rerere.zeroagent.subagent.SubAgentStatus
import me.rerere.zeroagent.ui.components.ui.ZeroAgentModalBottomSheet
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
internal fun SubAgentMonitorSheet(
    runs: List<SubAgentRun>,
    onDismiss: () -> Unit,
    onCancel: (String) -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    ZeroAgentModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(stringResource(R.string.chat_subagent_monitor_title), style = MaterialTheme.typography.headlineSmall)
            if (runs.isEmpty()) {
                Text(stringResource(R.string.chat_subagent_monitor_empty), modifier = Modifier.padding(vertical = 20.dp))
            }
            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(runs, key = SubAgentRun::id) { run ->
                    SubAgentMonitorRow(run, now, onCancel)
                }
            }
        }
    }
}

@Composable
private fun SubAgentMonitorRow(run: SubAgentRun, now: Long, onCancel: (String) -> Unit) {
    val service: ChatService = koinInject()
    val childId = remember(run.childConversationId) {
        run.childConversationId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
    }
    val childFlow = remember(childId, service) { childId?.let(service::getConversationFlow) }
    val child = childFlow?.collectAsStateWithLifecycle()?.value
    val tools = remember(child) {
        child?.currentMessages.orEmpty().flatMap { it.parts.filterIsInstance<UIMessagePart.Tool>() }
    }
    val elapsed = ((now - run.startedAtMs) / 1_000).coerceAtLeast(0)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(run.label, style = MaterialTheme.typography.titleMedium)
            Text(run.task, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(
                    R.string.chat_subagent_monitor_status,
                    stringResource(if (run.status == SubAgentStatus.PENDING) R.string.chat_subagent_monitor_queued else R.string.chat_subagent_monitor_running),
                    elapsed / 60,
                    elapsed % 60,
                ),
                style = MaterialTheme.typography.labelMedium,
            )
            if (tools.isNotEmpty()) {
                Text(stringResource(R.string.chat_subagent_monitor_tools, tools.count { it.isExecuted }, tools.size))
                tools.lastOrNull { !it.isExecuted && it.executionStartedAt != null }?.let { tool ->
                    Text(stringResource(R.string.chat_subagent_monitor_active_tool, tool.toolName))
                }
            }
            OutlinedButton(onClick = { onCancel(run.id) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.chat_subagent_monitor_cancel))
            }
        }
    }
}
