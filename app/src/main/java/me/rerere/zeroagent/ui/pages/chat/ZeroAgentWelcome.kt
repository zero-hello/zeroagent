package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ZeroAgentWelcome(modifier: Modifier = Modifier, onDraft: (String) -> Unit) {
    WorkspaceWelcome(modifier = modifier, onDraft = onDraft) {
        ZeroAgentTaskOverview()
    }
}
