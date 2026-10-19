package me.rerere.zeroagent.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Voice
import me.rerere.zeroagent.R

/** Each action owns its measured space; a short window wraps instead of overlapping hit targets. */
@Composable
internal fun ComposerActionRow(
    loading: Boolean,
    hasDraft: Boolean,
    onAttach: () -> Unit,
    onVoiceCall: (() -> Unit)?,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    onLongSend: (() -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().testTag("composer_actions"),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onAttach, modifier = Modifier.size(48.dp).testTag("composer_attach")) {
            Icon(HugeIcons.Add01, stringResource(R.string.quick_chat_segment_attachment))
        }
        if (onVoiceCall != null) {
            FilledTonalIconButton(onClick = onVoiceCall, modifier = Modifier.size(48.dp).testTag("composer_voice")) {
                Icon(HugeIcons.Voice, stringResource(R.string.zeroagent_voice_title))
            }
        }
        IconButton(onClick = onMore, modifier = Modifier.size(48.dp).testTag("composer_more")) {
            Icon(HugeIcons.MoreVertical, stringResource(R.string.more_options))
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                FilledTonalIconButton(
                    onClick = onStop,
                    modifier = Modifier.size(48.dp).testTag("composer_stop"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Icon(HugeIcons.Cancel01, stringResource(R.string.stop)) }
            }
            if (!loading || hasDraft) {
                Box(
                    modifier = Modifier.size(48.dp).testTag("composer_send").clip(CircleShape)
                        .combinedClickable(enabled = hasDraft, role = Role.Button, onClick = onSend, onLongClick = onLongSend),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(shape = CircleShape,
                        color = if (hasDraft) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (hasDraft) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(HugeIcons.ArrowUp02, stringResource(R.string.send), Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ComposerContextRow(
    modelLabel: String,
    reasoningLabel: String?,
    onModelClick: () -> Unit,
    onReasoningClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessControl: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth().testTag("composer_context")) {
        val availableWidth = maxWidth
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            TextButton(
                onClick = onModelClick,
                modifier = Modifier.widthIn(max = availableWidth).heightIn(min = 48.dp)
                    .testTag("composer_model").semantics { contentDescription = modelLabel },
            ) {
                Text(modelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge)
            }
            if (reasoningLabel != null) {
                TextButton(onClick = onReasoningClick,
                    modifier = Modifier.widthIn(max = availableWidth).heightIn(min = 48.dp).testTag("composer_reasoning")) {
                    Text(reasoningLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
            accessControl?.invoke()
        }
    }
}
