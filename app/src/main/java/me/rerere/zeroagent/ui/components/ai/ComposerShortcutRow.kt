package me.rerere.zeroagent.ui.components.ai

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.chat.ChatSlashCommand
import me.rerere.zeroagent.ui.hooks.ChatInputState

@Composable
internal fun ComposerShortcutRow(state: ChatInputState, hasWorkspace: Boolean, onFocus: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val text = state.textContent.text
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = expanded,
            onClick = { expanded = !expanded },
            label = { Text(stringResource(R.string.chat_composer_shortcuts)) },
        )
        if (expanded) {
            for (command in listOf(ChatSlashCommand.PLAN, ChatSlashCommand.GOAL, ChatSlashCommand.COMPACT, ChatSlashCommand.HELP)) {
                AssistChip(
                    enabled = text.isBlank(),
                    onClick = {
                        state.setMessageText("/${command.command} ")
                        onFocus()
                    },
                    label = { Text("/${command.command}") },
                )
            }
            if (hasWorkspace) {
                AssistChip(
                    onClick = {
                        val draft = state.textContent.text
                        if (draft.lastOrNull() != '@') {
                            state.appendText(if (draft.isEmpty() || draft.last().isWhitespace()) "@" else " @")
                        }
                        onFocus()
                    },
                    label = { Text(stringResource(R.string.chat_composer_workspace)) },
                )
            }
        }
    }
}