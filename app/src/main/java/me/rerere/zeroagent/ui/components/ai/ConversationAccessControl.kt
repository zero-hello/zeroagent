package me.rerere.zeroagent.ui.components.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.AiSecurity01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.ShieldAlert
import me.rerere.hugeicons.stroke.ShieldUser
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.ai.tools.ConversationAccess
import me.rerere.zeroagent.data.ai.tools.ConversationAccessMode
import kotlin.uuid.Uuid
import me.rerere.zeroagent.ui.components.ui.ZeroAgentModalBottomSheet as ModalBottomSheet

private fun ConversationAccessMode.label() = when (this) {
    ConversationAccessMode.ASK -> R.string.zeroagent8_access_ask
    ConversationAccessMode.ASSISTED -> R.string.zeroagent8_access_assisted
    ConversationAccessMode.FULL -> R.string.zeroagent8_access_full
}

private fun ConversationAccessMode.icon(): ImageVector = when (this) {
    ConversationAccessMode.ASK -> HugeIcons.ShieldUser
    ConversationAccessMode.ASSISTED -> HugeIcons.AiSecurity01
    ConversationAccessMode.FULL -> HugeIcons.ShieldAlert
}

@Composable
fun ConversationAccessControl(conversationId: Uuid, openRequest: Int = 0, modelName: String = "") {
    val modes by ConversationAccess.modes.collectAsStateWithLifecycle()
    val mode = modes[conversationId] ?: ConversationAccessMode.ASK
    var menu by remember(conversationId) { mutableStateOf(false) }
    LaunchedEffect(conversationId, openRequest) { if (openRequest > 0) menu = true }
    FilledTonalButton(
        onClick = { menu = true }, shape = CircleShape,
        colors = if (mode == ConversationAccessMode.FULL) ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) else ButtonDefaults.filledTonalButtonColors(),
        modifier = Modifier.heightIn(min = 48.dp).testTag("access_control"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(mode.icon(), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(mode.label()), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(4.dp))
        Icon(HugeIcons.ArrowDown01, contentDescription = null, modifier = Modifier.size(16.dp))
    }
    if (menu) ConversationAccessPicker(
        mode = mode, modelName = modelName,
        onModeSelected = { selected ->
            ConversationAccess.set(conversationId, selected, highRiskConfirmed = selected == ConversationAccessMode.FULL)
            menu = false
        }, onDismiss = { menu = false },
    )
}

/** FULL is delivered to the caller only after the risk acknowledgement. */
@Composable
internal fun ConversationAccessPicker(
    mode: ConversationAccessMode, modelName: String,
    onModeSelected: (ConversationAccessMode) -> Unit, onDismiss: () -> Unit,
) {
    var confirmFull by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        sheetMaxWidth = 560.dp, containerColor = MaterialTheme.colorScheme.surface,
    ) {
        if (confirmFull) FullAccessConfirmation(
            onDismiss = { confirmFull = false },
            onConfirm = { confirmFull = false; onModeSelected(ConversationAccessMode.FULL) },
        ) else Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 20.dp).padding(bottom = 24.dp).testTag("access_options"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.access_choose_title), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.access_choose_subtitle), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(HugeIcons.Cancel01, stringResource(R.string.access_close), Modifier.size(20.dp))
                }
            }
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConversationAccessMode.entries.forEach { value ->
                    AccessOption(value = value, selected = mode == value, onClick = {
                        if (value == mode) onDismiss()
                        else if (value == ConversationAccessMode.FULL) confirmFull = true
                        else onModeSelected(value)
                    })
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (modelName.isNotBlank()) Text(stringResource(R.string.access_review_model, modelName),
                        style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.access_review_detail), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.access_session_detail), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccessOption(value: ConversationAccessMode, selected: Boolean, onClick: () -> Unit) {
    val color = if (value == ConversationAccessMode.FULL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        color = if (selected) color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, if (selected) color.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("access_option_${value.name}")
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
        ) {
            Icon(value.icon(), null, Modifier.size(24.dp), tint = color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(value.label()), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = if (value == ConversationAccessMode.FULL) color else MaterialTheme.colorScheme.onSurface)
                val description = when (value) {
                    ConversationAccessMode.ASK -> R.string.access_ask_description
                    ConversationAccessMode.ASSISTED -> R.string.access_assisted_description
                    ConversationAccessMode.FULL -> R.string.access_full_description
                }
                Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null, modifier = Modifier.size(24.dp),
                colors = RadioButtonDefaults.colors(selectedColor = color))
        }
    }
}

@Composable
private fun FullAccessConfirmation(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var accepted by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(HugeIcons.ShieldAlert, null, tint = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.zeroagent8_access_risk_title),
            style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.zeroagent8_access_risk), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("access_accept_risk")
            .toggleable(value = accepted, role = Role.Checkbox, onValueChange = { accepted = it }).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = accepted, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.zeroagent8_access_accept))
        }
        Button(enabled = accepted, onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("access_confirm_full"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError),
        ) { Text(stringResource(R.string.zeroagent8_access_full)) }
        TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.cancel))
        }
    }
}
