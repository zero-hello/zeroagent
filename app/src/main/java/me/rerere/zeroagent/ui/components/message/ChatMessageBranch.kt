package me.rerere.zeroagent.ui.components.message

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import me.rerere.zeroagent.utils.generateUnifiedDiff
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import me.rerere.zeroagent.ui.designsystem.ZeroIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.model.MessageNode

@Composable
fun ChatMessageBranchSelector(
    node: MessageNode,
    modifier: Modifier = Modifier,
    onUpdate: (MessageNode) -> Unit,
) {
    if (node.messages.size <= 1) return
    val currentIndex = node.selectIndex.takeIf { it in node.messages.indices } ?: node.messages.lastIndex
    var compare by remember(node.id, currentIndex) { mutableStateOf(false) }
    val comparisonIndex = if (currentIndex > 0) currentIndex - 1 else 1
    if (compare) {
        val previous = minOf(currentIndex, comparisonIndex)
        val next = maxOf(currentIndex, comparisonIndex)
        val old = node.messages[previous]
        val new = node.messages[next]
        val diff by produceState<List<String>?>(null, old, new) {
            value = withContext(Dispatchers.Default) {
                val before = old.toText()
                val after = new.toText()
                if (before == after) emptyList()
                else if (before.length + after.length > 48_000 ||
                    (before.count { it == '\n' } + 1L) * (after.count { it == '\n' } + 1L) > 1_000_000L
                ) listOf("內容太長，請分別查看兩個版本。")
                else generateUnifiedDiff(before, after, "reply", 2).orEmpty().lineSequence()
                    .drop(2).toList()
            }
        }
        ZeroAgentAlertDialog(
            onDismissRequest = { compare = false },
            title = { Text("版本 ${previous + 1} → ${next + 1}") },
            text = {
                when {
                    diff == null -> Text("正在比較…")
                    diff.isNullOrEmpty() -> Text("兩個版本的文字相同。")
                    else -> Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())) {
                        diff.orEmpty().forEach { line ->
                            Text(
                                text = line,
                                fontFamily = FontFamily.Monospace,
                                color = when {
                                    line.startsWith("+") -> MaterialTheme.colorScheme.primary
                                    line.startsWith("-") -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { compare = false }) { Text("關閉") } },
        )
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(
            onClick = { onUpdate(node.copy(selectIndex = currentIndex - 1)) },
            enabled = currentIndex > 0,
        ) {
            Icon(HugeIcons.ArrowLeft01, stringResource(R.string.accessibility_previous_branch), Modifier.size(18.dp))
        }
        Text(
            text = stringResource(R.string.chat_branch_position, currentIndex + 1, node.messages.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(
            onClick = { onUpdate(node.copy(selectIndex = currentIndex + 1)) },
            enabled = currentIndex < node.messages.lastIndex,
        ) {
            Icon(HugeIcons.ArrowRight01, stringResource(R.string.accessibility_next_branch), Modifier.size(18.dp))
        }
        TextButton(onClick = { compare = true }) { Text("比較") }
    }
}
