package me.rerere.zeroagent.ui.components.ai

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import me.rerere.zeroagent.ui.components.ui.ZeroAgentModalBottomSheet as ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.data.datastore.getCurrentChatModel
import me.rerere.zeroagent.data.datastore.findProvider
import me.rerere.ai.core.ReasoningLevel
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Idea
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.ui.ToggleSurface
import me.rerere.zeroagent.ui.components.ui.SelectableOptionRow
import me.rerere.zeroagent.ui.components.ui.icons.ReasoningHigh
import me.rerere.zeroagent.ui.components.ui.icons.ReasoningLow
import me.rerere.zeroagent.ui.components.ui.icons.ReasoningMedium


@Composable
fun ReasoningButton(
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    model: me.rerere.ai.provider.Model? = me.rerere.zeroagent.ui.context.LocalSettings.current.getCurrentChatModel(),
    reasoningLevel: ReasoningLevel,
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
) {
    val capabilityRevision by me.rerere.ai.core.ReasoningCapabilities.revision.collectAsState()
    val settings = me.rerere.zeroagent.ui.context.LocalSettings.current
    val selectedModel = model
    val provider = selectedModel?.findProvider(settings.providers)
    val options = remember(selectedModel, provider, capabilityRevision) {
        me.rerere.ai.core.ReasoningCapabilities.forModel(selectedModel, provider)
    }
    val actualLevel = me.rerere.ai.core.ReasoningCapabilities.effective(selectedModel, provider, reasoningLevel)
    var showPicker by remember(selectedModel?.id, provider?.id) { mutableStateOf(false) }

    if (options.levels.size < 2) return

    if (showPicker) {
        ReasoningPicker(
            reasoningLevel = actualLevel,
            supportedLevels = options.levels,
            capabilitiesDetected = options.detected,
            onDismissRequest = { showPicker = false },
            onUpdateReasoningLevel = onUpdateReasoningLevel
        )
    }

    ToggleSurface(
        checked = actualLevel.isEnabled,
        onClick = { showPicker = true },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                ReasoningIcon(actualLevel)
            }
            if (!onlyIcon) Text(actualLevel.label())
        }
    }
}

@Composable
fun ReasoningPicker(
    reasoningLevel: ReasoningLevel,
    supportedLevels: List<ReasoningLevel> = listOf(ReasoningLevel.AUTO),
    capabilitiesDetected: Boolean = false,
    modelLabel: String? = null,
    onDismissRequest: () -> Unit = {},
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
) {
    val levels = supportedLevels.distinct().ifEmpty { listOf(ReasoningLevel.AUTO) }
    val levelCount = levels.size
    val currentIndex = levels.indexOf(reasoningLevel).coerceAtLeast(0)
    val previewLevel = levels[currentIndex]

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 标题
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = modelLabel ?: stringResource(R.string.reasoning_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(if (capabilitiesDetected) R.string.zeroagent7_reasoning_detected else R.string.zeroagent7_reasoning_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // 当前等级展示
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val iconColor by animateColorAsState(
                    if (previewLevel.isEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = when (previewLevel) {
                        ReasoningLevel.OFF -> HugeIcons.Idea
                        ReasoningLevel.AUTO -> HugeIcons.Idea01
                        ReasoningLevel.MINIMAL, ReasoningLevel.LOW -> ReasoningLow
                        ReasoningLevel.MEDIUM -> ReasoningMedium
                        ReasoningLevel.HIGH -> ReasoningHigh
                        ReasoningLevel.XHIGH -> ReasoningHigh
                        ReasoningLevel.MAX -> ReasoningHigh
                    },
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = iconColor,
                )
                Text(
                    text = previewLevel.label(),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                levels.forEach { level ->
                    SelectableOptionRow(
                        label = level.label(),
                        selected = level == previewLevel,
                        onClick = { onUpdateReasoningLevel(level) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasoningIcon(level: ReasoningLevel) {
    when (level) {
        ReasoningLevel.OFF -> Icon(HugeIcons.Idea, null)
        ReasoningLevel.AUTO -> Icon(HugeIcons.Idea01, null)
        ReasoningLevel.MINIMAL, ReasoningLevel.LOW -> Icon(ReasoningLow, null)
        ReasoningLevel.MEDIUM -> Icon(ReasoningMedium, null)
        ReasoningLevel.HIGH -> Icon(ReasoningHigh, null)
        ReasoningLevel.XHIGH -> Icon(ReasoningHigh, null)
        ReasoningLevel.MAX -> Icon(ReasoningHigh, null)
    }
}

@Composable
internal fun ReasoningLevel.label(): String = when (this) {
    ReasoningLevel.OFF -> stringResource(R.string.reasoning_off)
    ReasoningLevel.AUTO -> stringResource(R.string.reasoning_auto)
    ReasoningLevel.MINIMAL -> stringResource(R.string.zeroagent7_reasoning_minimal)
    ReasoningLevel.LOW -> stringResource(R.string.reasoning_light)
    ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium)
    ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy)
    ReasoningLevel.XHIGH -> stringResource(R.string.reasoning_xhigh)
    ReasoningLevel.MAX -> stringResource(R.string.reasoning_max)
}

@Composable
@Preview(showBackground = true)
private fun ReasoningPickerPreview() {
    MaterialTheme {
        var level by remember { mutableStateOf(ReasoningLevel.AUTO) }
        ReasoningPicker(
            reasoningLevel = level,
            onUpdateReasoningLevel = { level = it }
        )
    }
}
