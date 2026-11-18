package me.rerere.zeroagent.ui.components.terminal

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.terminal.AndroidShellEnvironment
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.context.LocalNavController
import java.io.File

/** Android shell needs neither a downloadable Linux environment nor a workspace binding. */
@Composable
fun EmbeddedTerminalEnvironmentPanel() {
    val ready = remember { File(AndroidShellEnvironment.SHELL).canExecute() }
    val nav = LocalNavController.current
    CardGroup(title = { Text(stringResource(R.string.embedded_terminal_title)) }) {
        item(
            headlineContent = { Text(stringResource(R.string.embedded_terminal_title)) },
            supportingContent = { Text(stringResource(if (ready) R.string.embedded_terminal_ready else R.string.embedded_terminal_not_ready)) },
            onClick = { nav.navigate(Screen.WorkspaceTerminal(AndroidShellEnvironment.SESSION_KEY)) },
        )
        item(headlineContent = { Text(stringResource(R.string.embedded_terminal_privilege)) })
    }
}
