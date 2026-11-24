package me.rerere.zeroagent.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    }
}
