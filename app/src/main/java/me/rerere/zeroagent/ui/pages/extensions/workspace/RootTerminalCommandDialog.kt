package me.rerere.zeroagent.ui.pages.extensions.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.root.RootAccessManager
import me.rerere.zeroagent.data.root.RootAccessStatus
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import me.rerere.zeroagent.ui.context.LocalNavController

/** A real Android su command, using exactly the same grant and revocation state as Agent tools. */
@Composable
internal fun RootTerminalCommandDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val root = remember(context) { RootAccessManager(context) }
    val status by root.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val nav = LocalNavController.current
    var command by rememberSaveable { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val outputGate = remember { TerminalOutputGate() }
    val stoppedMessage = stringResource(R.string.embedded_terminal_stopped)
    LaunchedEffect(root) { if (root.enabled) root.refresh() }

    ZeroAgentAlertDialog(
        onDismissRequest = { job?.cancel(); onDismiss() },
        title = { Text(stringResource(R.string.embedded_terminal_root_command)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.embedded_terminal_root_help), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(when (status) {
                    RootAccessStatus.DISABLED -> R.string.embedded_terminal_root_disabled
                    RootAccessStatus.UNVERIFIED -> R.string.embedded_terminal_root_unverified
                    RootAccessStatus.CHECKING -> R.string.embedded_terminal_root_checking
                    RootAccessStatus.AVAILABLE -> R.string.embedded_terminal_root_available
                    RootAccessStatus.UNAVAILABLE -> R.string.embedded_terminal_root_unavailable
                }))
                if (running || status == RootAccessStatus.CHECKING) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it.take(8192) },
                    label = { Text(stringResource(R.string.embedded_terminal_command)) },
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                )
                if (output.isNotEmpty()) SelectionContainer {
                    Text(
                        text = output,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                if (!running) TextButton(onClick = {
                    if (root.enabled) scope.launch { root.refresh() }
                    else { onDismiss(); nav.navigate(Screen.SettingRoot) }
                }, shapes = ButtonDefaults.shapes(), enabled = status != RootAccessStatus.CHECKING) {
                    Text(stringResource(if (root.enabled) R.string.embedded_terminal_recheck_root else R.string.embedded_terminal_root_settings))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (running) {
                        job?.cancel()
                    } else {
                        running = true
                        val thisExecution = outputGate.start()
                        output = ""
                        job = scope.launch {
                            try {
                                val result = root.run(command, onOutput = { snapshot ->
                                    scope.launch {
                                        if (outputGate.accepts(thisExecution)) output = snapshot
                                    }
                                })
                                output = context.getString(R.string.embedded_terminal_root_result, result.exitCode, result.output)
                            } catch (cancel: CancellationException) {
                                output = stoppedMessage
                                throw cancel
                            } catch (failure: Exception) {
                                output = failure.message ?: context.getString(R.string.embedded_terminal_start_failed)
                            } finally {
                                outputGate.finish(thisExecution)
                                running = false
                            }
                        }
                    }
                },
                shapes = ButtonDefaults.shapes(),
                enabled = running || (status == RootAccessStatus.AVAILABLE && command.isNotBlank()),
            ) { Text(stringResource(if (running) R.string.embedded_terminal_stop else R.string.embedded_terminal_execute)) }
        },
        dismissButton = {
            TextButton(onClick = { job?.cancel(); onDismiss() }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.workspace_terminal_close))
            }
        },
    )
}
