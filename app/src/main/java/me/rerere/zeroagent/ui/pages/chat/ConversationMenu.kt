package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.core.MessageRole
import me.rerere.zeroagent.data.lab.LabPreferences
import me.rerere.zeroagent.ui.pages.lab.ConversationNotesWorkspace
import androidx.compose.ui.res.stringResource
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.ui.ChatPrivacy
import me.rerere.zeroagent.ui.components.ui.rememberPrivacyLock
import me.rerere.zeroagent.data.model.Conversation

@Composable
internal fun ConversationMenu(
    conversation: Conversation,
    preview: Boolean,
    onPreview: () -> Unit,
    onTemporary: (() -> Unit)?,
    onClearDraft: () -> Unit,
) {
    val context = LocalContext.current
    val locked = rememberPrivacyLock(conversation.id)
    var open by remember(conversation.id) { mutableStateOf(false) }
    var confirmClearDraft by remember(conversation.id) { mutableStateOf(false) }
    val handoff by remember(context) { LabPreferences.flow(context, "handoff", "false") }
        .collectAsStateWithLifecycle(LabPreferences.get(context, "handoff", "false"))
    val todo by remember(context) { LabPreferences.flow(context, "todo_capture", "false") }
        .collectAsStateWithLifecycle(LabPreferences.get(context, "todo_capture", "false"))
    var notes by remember(conversation.id) { mutableStateOf<String?>(null) }
    val notesMode = notes
    if (!locked && notesMode != null && (if (notesMode == "handoff") handoff else todo) == "true") {
        val captureTodo = notesMode == "todo"
        ZeroAgentAlertDialog(
            onDismissRequest = { notes = null },
            title = { Text(if (captureTodo) "整理這個對話的待辦" else "接續這個對話") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConversationNotesWorkspace(captureTodo, conversation.id, onHandoff = { notes = null })
                }
            },
            confirmButton = { TextButton(onClick = { notes = null }) { Text("關閉") } },
        )
    }
    if (confirmClearDraft) {
        ZeroAgentAlertDialog(
            onDismissRequest = { confirmClearDraft = false },
            title = { Text("清除這個聊天室的草稿？") },
            text = { Text("未送出的文字與附件將從草稿移除。已送出的訊息會保留。") },
            confirmButton = {
                TextButton(onClick = { confirmClearDraft = false; onClearDraft() }) { Text("清除草稿") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDraft = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(HugeIcons.LeftToRightListBullet, stringResource(R.string.accessibility_chat_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(if (preview) "返回對話" else "瀏覽訊息") }, onClick = { open = false; onPreview() })
            if (onTemporary != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.temporary_chat)) }, onClick = { open = false; onTemporary() })
            }
            if (!locked && handoff == "true" && conversation.currentMessages.size >= 8) {
                DropdownMenuItem(text = { Text("整理長對話接力") }, onClick = { open = false; notes = "handoff" })
            }
            if (!locked && todo == "true" && conversation.currentMessages.any { it.role == MessageRole.USER }) {
                val last = conversation.currentMessages.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty()
                val likelyTask = listOf("待辦", "提醒我", "約在", "截止", "記得", "明天要").any(last::contains)
                DropdownMenuItem(text = { Text(if (likelyTask) "看看可能的待辦" else "整理聊天待辦") }, onClick = { open = false; notes = "todo" })
            }
            DropdownMenuItem(text = { Text("清除這個聊天室的草稿") }, onClick = { open = false; confirmClearDraft = true })
            DropdownMenuItem(text = { Text(if (locked) "取消對話鎖定" else "鎖定這個對話") },
                enabled = locked || ChatPrivacy.canLock(context), onClick = {
                    open = false
                    ChatPrivacy.setChatLock(context, conversation.id, !locked)
                })
        }
    }
}
