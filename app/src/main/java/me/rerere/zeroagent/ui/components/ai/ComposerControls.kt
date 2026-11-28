package me.rerere.zeroagent.ui.components.ai

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column

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
import me.rerere.zeroagent.ui.designsystem.ZeroIconButton as IconButton
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.hugeicons.stroke.Voice
import me.rerere.zeroagent.R

/** One row owns the attachment button, editable text and current primary action. */
@Composable
internal fun ComposerActionRow(
    loading: Boolean,
    hasDraft: Boolean,
    onAttach: () -> Unit,
    onVoiceCall: (() -> Unit)?,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onModelOptions: (() -> Unit)?,
    onReasoning: (() -> Unit)? = null,
    reasoningSelected: Boolean = false,
    reasoningLabel: String? = null,
    modifier: Modifier = Modifier,
    onLongSend: (() -> Unit)? = null,
    input: @Composable (Modifier) -> Unit = { Spacer(it) },
) {
    Column(modifier.fillMaxWidth().testTag("composer_actions")) {
        if (onModelOptions != null || onReasoning != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onModelOptions?.let { action ->
                    IconButton(onClick = action, modifier = Modifier.testTag("composer_model")) {
                        Icon(HugeIcons.Brain02, stringResource(R.string.model_list_select_model))
                    }
                }
                onReasoning?.let { action ->
                    IconButton(onClick = action, modifier = Modifier.testTag("composer_reasoning").semantics {
                        selected = reasoningSelected
                        reasoningLabel?.let { stateDescription = it }
                    }) { Icon(HugeIcons.Idea01, stringResource(R.string.reasoning_picker_title)) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAttach, modifier = Modifier.testTag("composer_attach")) {
                Icon(HugeIcons.Add01, stringResource(R.string.quick_chat_segment_attachment))
            }
            input(Modifier.weight(1f))
            if (loading && hasDraft) {
                IconButton(onClick = onStop, modifier = Modifier.testTag("composer_stop")) {
                    Icon(HugeIcons.Cancel01, stringResource(R.string.stop))
                }
            }
            val mode = when {
                hasDraft -> ComposerPrimary.Send
                loading -> ComposerPrimary.Stop
                onVoiceCall != null -> ComposerPrimary.Voice
                else -> ComposerPrimary.Send
            }
            androidx.compose.animation.AnimatedContent(targetState = mode, label = "Composer action",
                transitionSpec = {
                    androidx.compose.animation.fadeIn(me.rerere.zeroagent.ui.designsystem.ZeroMotion.snappy()) togetherWith
                        androidx.compose.animation.fadeOut(me.rerere.zeroagent.ui.designsystem.ZeroMotion.fluid())
                }) { current ->
                when (current) {
                    ComposerPrimary.Voice -> IconButton(onClick = { onVoiceCall?.invoke() }, modifier = Modifier.testTag("composer_voice")) {
                        Icon(HugeIcons.Voice, stringResource(R.string.zeroagent_voice_title))
                    }
                    ComposerPrimary.Stop -> IconButton(onClick = onStop, modifier = Modifier.testTag("composer_stop")) {
                        Icon(HugeIcons.Cancel01, stringResource(R.string.stop))
                    }
                    ComposerPrimary.Send -> {
                        val colors = me.rerere.zeroagent.ui.designsystem.ZeroTheme.colors
                        Box(Modifier.size(48.dp).testTag("composer_send").clip(CircleShape)
                            .combinedClickable(enabled = hasDraft, role = Role.Button, onClick = onSend, onLongClick = onLongSend),
                            contentAlignment = Alignment.Center) {
                            Surface(shape = CircleShape, modifier = Modifier.size(38.dp),
                                color = if (hasDraft) colors.textPrimary else colors.surfaceElevated,
                                contentColor = if (hasDraft) colors.background else colors.textMuted) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(HugeIcons.ArrowUp02, stringResource(R.string.send), Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class ComposerPrimary { Voice, Send, Stop }
/** A transparent layout slot, without an enclosing toolbar surface or external model selector. */
@Composable
internal fun ComposerPermissionRow(control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("composer_permissions"), verticalAlignment = Alignment.CenterVertically) {
        control()
    }
}
