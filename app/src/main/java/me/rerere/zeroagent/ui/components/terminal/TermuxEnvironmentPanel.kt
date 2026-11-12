package me.rerere.zeroagent.ui.components.terminal

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.getCurrentAssistant
import me.rerere.zeroagent.data.repository.WorkspaceRepository
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.workspace.WorkspaceShellStatus
import org.koin.compose.koinInject

/** Embedded environment shared by the PTY and Agent workspace tools. */
@Composable
fun EmbeddedTerminalEnvironmentPanel() {
    val store: SettingsStore = koinInject()
    val repository: WorkspaceRepository = koinInject()
    val settings by store.settingsFlow.collectAsStateWithLifecycle()
    val workspaces by repository.listFlow().collectAsStateWithLifecycle(emptyList())
    val workspaceId = if (settings.init) null else settings.getCurrentAssistant().workspaceId?.toString()
    val workspace = workspaces.firstOrNull { it.id == workspaceId }
    val ready = workspace?.shellStatus == WorkspaceShellStatus.READY.name
    val nav = LocalNavController.current
    CardGroup(title = { Text(stringResource(R.string.embedded_terminal_title)) }) {
        item(
            headlineContent = { Text(workspace?.name ?: stringResource(R.string.embedded_terminal_workspaces)) },
            supportingContent = { Text(stringResource(when {
                workspace == null -> R.string.embedded_terminal_unbound
                ready -> R.string.embedded_terminal_ready
                else -> R.string.embedded_terminal_not_ready
            })) },
            onClick = {
                nav.navigate(when {
                    workspace == null -> Screen.Workspaces
                    ready -> Screen.WorkspaceTerminal(workspace.id)
                    else -> Screen.WorkspaceDetail(workspace.id)
                })
            },
        )
        item(headlineContent = { Text(stringResource(R.string.embedded_terminal_privilege)) })
    }
}
