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
import androidx.compose.ui.graphics.Color
import me.rerere.zeroagent.ui.components.ui.liquidGlass
import me.rerere.zeroagent.ui.components.ui.LiquidGlassIconButton
import me.rerere.zeroagent.ui.context.LocalSettings
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
        val displaySetting = LocalSettings.current.displaySetting
        if (onVoiceCall != null) {
            IconButton(onClick = onVoiceCall, modifier = Modifier.size(48.dp).testTag("composer_voice")) {
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
                    shapes = IconButtonDefaults.shapes(), onClick = onStop,
                    modifier = Modifier.size(48.dp).testTag("composer_stop"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Icon(HugeIcons.Cancel01, stringResource(R.string.stop)) }
            }
            if (!loading || hasDraft) {
                val sendButtonModifier = if (displaySetting.advancedMaterialEnabled && hasDraft) {
                    Modifier
                        .size(40.dp)
                        .liquidGlass(
                            shape = CircleShape,
                            style = displaySetting.advancedMaterialStyle,
                            tint = MaterialTheme.colorScheme.primary,
                            borderAlpha = 0.35f,
                            glareAlpha = 0.20f,
                            enabled = true,
                        )
                } else {
                    Modifier.size(40.dp)
                }
                Box(
                    modifier = Modifier.size(48.dp).testTag("composer_send").clip(CircleShape)
                        .combinedClickable(enabled = hasDraft, role = Role.Button, onClick = onSend, onLongClick = onLongSend),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(shape = CircleShape,
                        color = if (hasDraft) (if (displaySetting.advancedMaterialEnabled) Color.Transparent else MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (hasDraft) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = sendButtonModifier,
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

/** A transparent layout slot, without an enclosing toolbar surface or external model selector. */
@Composable
internal fun ComposerPermissionRow(control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("composer_permissions"), verticalAlignment = Alignment.CenterVertically) {
        control()
    }
}
