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

@Composable
fun ConversationAccessControl(conversationId: Uuid, openRequest: Int = 0) {
    val modes by ConversationAccess.modes.collectAsStateWithLifecycle()
    val mode = modes[conversationId] ?: ConversationAccessMode.ASK
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(openRequest) { if (openRequest > 0) menu = true }
    var confirm by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }
    fun label(value: ConversationAccessMode) = when (value) {
        ConversationAccessMode.ASK -> R.string.za8_access_ask
        ConversationAccessMode.ASSISTED -> R.string.za8_access_assisted
        ConversationAccessMode.FULL -> R.string.za8_access_full
    }
    Box {
        TextButton(onClick = { menu = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(stringResource(label(mode)), style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            ConversationAccessMode.entries.forEach { value ->
                DropdownMenuItem(text = { Text(stringResource(label(value))) }, onClick = {
                    menu = false
                    if (value == ConversationAccessMode.FULL) { accepted = false; confirm = true }
                    else ConversationAccess.set(conversationId, value)
                })
            }
            Text(stringResource(R.string.za8_access_hint), modifier = Modifier.widthIn(max = 280.dp).padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.za8_access_risk_title)) },
        text = { Column {
            Text(stringResource(R.string.za8_access_risk))
            Row { Checkbox(checked = accepted, onCheckedChange = { accepted = it }); Text(stringResource(R.string.za8_access_accept)) }
        } },
        confirmButton = { TextButton(enabled = accepted, onClick = {
            ConversationAccess.set(conversationId, ConversationAccessMode.FULL, highRiskConfirmed = accepted); confirm = false
        }) { Text(stringResource(R.string.za8_access_full)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
}
