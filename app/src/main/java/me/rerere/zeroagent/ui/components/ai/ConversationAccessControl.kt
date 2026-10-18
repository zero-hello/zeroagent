package me.rerere.zeroagent.ui.components.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.ai.tools.ConversationAccess
import me.rerere.zeroagent.data.ai.tools.ConversationAccessMode
import kotlin.uuid.Uuid
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import me.rerere.zeroagent.ui.components.ui.zeroAgentGlass
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role

@Composable
fun ConversationAccessControl(conversationId: Uuid, openRequest: Int = 0) {
    val modes by ConversationAccess.modes.collectAsStateWithLifecycle()
    val mode = modes[conversationId] ?: ConversationAccessMode.ASK
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(openRequest) { if (openRequest > 0) menu = true }
    var confirm by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }
    fun label(value: ConversationAccessMode) = when (value) {
        ConversationAccessMode.ASK -> R.string.zeroagent8_access_ask
        ConversationAccessMode.ASSISTED -> R.string.zeroagent8_access_assisted
        ConversationAccessMode.FULL -> R.string.zeroagent8_access_full
    }
    Box {
        FilledTonalButton(
            onClick = { menu = true }, shape = CircleShape,
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(stringResource(label(mode)), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            Icon(HugeIcons.ArrowDown01, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
            modifier = Modifier.widthIn(max = 320.dp).zeroAgentGlass(radius = 24)) {
            ConversationAccessMode.entries.forEach { value ->
                DropdownMenuItem(text = { Text(stringResource(label(value))) }, onClick = {
                    menu = false
                    if (value == ConversationAccessMode.FULL) { accepted = false; confirm = true }
                    else ConversationAccess.set(conversationId, value)
                })
            }
            Text(stringResource(R.string.zeroagent8_access_hint), modifier = Modifier.widthIn(max = 280.dp).padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirm) ZeroAgentAlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.zeroagent8_access_risk_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.zeroagent8_access_risk))
            Row(Modifier.fillMaxWidth().toggleable(value = accepted, role = Role.Checkbox, onValueChange = { accepted = it }).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = accepted, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.zeroagent8_access_accept))
            }
        } },
        confirmButton = { TextButton(enabled = accepted, onClick = {
            ConversationAccess.set(conversationId, ConversationAccessMode.FULL, highRiskConfirmed = accepted); confirm = false
        }) { Text(stringResource(R.string.zeroagent8_access_full)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
}
