package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.ai.ContextBudgetPlanner
import me.rerere.zeroagent.data.ai.ContextCompactionView
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.repository.ConversationRepository
import org.koin.compose.koinInject

@Composable
internal fun ChatContextMeter(
    conversation: Conversation,
    contextLength: Int?,
    canCompact: Boolean,
    onCompact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (contextLength == null || contextLength <= 0 || conversation.messageNodes.isEmpty()) return
    val repository: ConversationRepository = koinInject()
    val latestConversation by rememberUpdatedState(conversation)
    val tokens by produceState<Int?>(null, conversation.id, contextLength, repository) {
        value = null
        combine(snapshotFlow { latestConversation }, repository.observeCompaction(conversation.id)) { current, compaction ->
            current to compaction
        }.conflate().catch { value = null }.collect { (current, compaction) ->
            value = withContext(Dispatchers.Default) {
                ContextBudgetPlanner.estimateContextTokens(ContextCompactionView.build(current, compaction).messages)
            }
            // Keep processing the newest snapshot while limiting repeated work during a stream.
            delay(250)
        }
    }
    val used = tokens ?: return
    val usage = estimatedContextUsage(used, contextLength)
    val color = if (usage >= 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(R.string.chat_context_usage_estimate, used, contextLength),
                style = MaterialTheme.typography.labelSmall,
                color = if (usage >= 0.85f) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(progress = { usage }, modifier = Modifier.fillMaxWidth(), color = color)
        }
        TextButton(onClick = onCompact, enabled = canCompact, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.chat_context_usage_compact))
        }
    }
}

internal fun estimatedContextUsage(tokens: Int, contextLength: Int): Float =
    if (contextLength <= 0) 0f else (tokens.toFloat() / contextLength).coerceIn(0f, 1f)
