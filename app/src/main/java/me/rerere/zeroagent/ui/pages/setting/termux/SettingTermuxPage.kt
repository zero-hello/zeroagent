package me.rerere.zeroagent.ui.pages.setting.termux

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import me.rerere.zeroagent.ui.components.ui.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

/**
 * Settings -> Termux. Four sections:
 *
 *  1. Status — integration indicators with tap actions (Termux installed, RUN_COMMAND
 *     permission request, Open Termux, verify smoke test).
 *  2. Timeouts — command timeout, per-turn budget (all tools), verify smoke-test timeout.
 *  3. Defaults & limits — working directory, stdout/stderr caps, apt-wrap toggle.
 *  4. Help — expandable setup instructions for allow-external-apps=true.
 */
@Composable
fun SettingTermuxPage(
    vm: SettingTermuxViewModel = koinViewModel(),
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_termux_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            me.rerere.zeroagent.ui.components.terminal.TermuxEnvironmentPanel(config.verifyTimeoutMs)

            // Section 2: Timeouts
            CardGroup(
                title = { Text(stringResource(R.string.setting_termux_section_timeouts)) },
            ) {
                item(
                    headlineContent = { Text(stringResource(R.string.setting_termux_command_timeout)) },
                    supportingContent = { Text(stringResource(R.string.setting_termux_command_timeout_desc)) },
                    trailingContent = {
                        TimeoutInput(
                            currentValue = config.commandTimeoutMs / 1_000L,
                            unitLabel = stringResource(R.string.setting_termux_unit_seconds),
                            onCommit = vm::setCommandTimeoutSeconds,
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.setting_termux_turn_budget)) },
                    supportingContent = { Text(stringResource(R.string.setting_termux_turn_budget_desc)) },
                    trailingContent = {
                        TimeoutInput(
                            currentValue = config.turnBudgetMs / 60_000L,
                            unitLabel = stringResource(R.string.setting_termux_unit_minutes),
                            onCommit = vm::setTurnBudgetMinutes,
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.setting_termux_max_tool_steps)) },
                    supportingContent = { Text(stringResource(R.string.setting_termux_max_tool_steps_desc)) },
                    trailingContent = {
                        TimeoutInput(
                            currentValue = config.maxToolSteps.toLong(),
                            unitLabel = stringResource(R.string.setting_termux_unit_steps),
                            onCommit = vm::setMaxToolSteps,
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.setting_termux_verify_timeout)) },
                    supportingContent = { Text(stringResource(R.string.setting_termux_verify_timeout_desc)) },
                    trailingContent = {
                        TimeoutInput(
                            currentValue = config.verifyTimeoutMs / 1_000L,
                            unitLabel = stringResource(R.string.setting_termux_unit_seconds),
                            onCommit = vm::setVerifyTimeoutSeconds,
                        )
                    },
                )
            }

            // Section 3: Defaults & limits
            CardGroup(
                title = { Text(stringResource(R.string.setting_termux_section_defaults)) },
            ) {
                item(
                    headlineContent = { Text(stringResource(R.string.setting_termux_working_dir)) },
                    supportingContent = {
                        WorkingDirInput(
                            currentValue = config.defaultWorkingDir,
                            onCommit = vm::setDefaultWorkingDir,
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.setting_termux_max_stdout)) },
                    supportingContent = { Text(stringResource(R.string.setting_termux_max_stdout_desc)) },
                    trailingContent = {
                        TimeoutInput(
                            currentValue = config.maxStdoutBytes.toLong(),
                            unitLabel = stringResource(R.string.setting_termux_unit_bytes),
                            onCommit = { vm.setMaxStdoutBytes(it.toInt()) },
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.setting_termux_max_stderr)) },
                    supportingContent = { Text(stringResource(R.string.setting_termux_max_stderr_desc)) },
                    trailingContent = {
                        TimeoutInput(
                            currentValue = config.maxStderrBytes.toLong(),
                            unitLabel = stringResource(R.string.setting_termux_unit_bytes),
                            onCommit = { vm.setMaxStderrBytes(it.toInt()) },
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.setting_termux_apt_wrap)) },
                    supportingContent = { Text(stringResource(R.string.setting_termux_apt_wrap_desc)) },
                    trailingContent = {
                        Switch(
                            checked = config.aptWrapEnabled,
                            onCheckedChange = vm::setAptWrapEnabled,
                        )
                    },
                )
            }

            // Section 4: Help
            CardGroup(
                title = { Text(stringResource(R.string.setting_termux_section_help)) },
            ) {
                item(
                    headlineContent = { Text(stringResource(R.string.setting_termux_help_allow_external_apps_title)) },
                    supportingContent = { Text(stringResource(R.string.setting_termux_help_allow_external_apps_body)) },
                )
            }
        }
    }
}

/**
 * Compact numeric input for a timeout/cap row's trailing slot. Mirrors [TimeoutInput] in
 * [me.rerere.zeroagent.ui.pages.setting.browser.SettingBrowserPage] in shape, but uses
 * .take(6) instead of .take(4) to accommodate the 5-digit stdout/stderr byte caps.
 */
@Composable
private fun TimeoutInput(
    currentValue: Long,
    unitLabel: String,
    onCommit: (Long) -> Unit,
) {
    var text by remember(currentValue) { mutableStateOf(currentValue.toString()) }

    OutlinedTextField(
        value = text,
        onValueChange = { new -> text = new.filter { it.isDigit() }.take(6) },
        singleLine = true,
        suffix = { Text(unitLabel, style = MaterialTheme.typography.bodySmall) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .width(148.dp)
            .onFocusChanged { focus ->
                if (!focus.isFocused) {
                    val parsed = text.toLongOrNull()
                    if (parsed != null && parsed != currentValue) {
                        onCommit(parsed)
                    } else {
                        text = currentValue.toString()
                    }
                }
            },
    )
}

/**
 * Single-line text field for the working directory setting. Commits on focus loss, same
 * pattern as [TimeoutInput]. An empty submit restores the current value.
 */
@Composable
private fun WorkingDirInput(
    currentValue: String,
    onCommit: (String) -> Unit,
) {
    var text by remember(currentValue) { mutableStateOf(currentValue) }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .onFocusChanged { focus ->
                if (!focus.isFocused) {
                    if (text != currentValue && text.isNotBlank()) {
                        onCommit(text)
                    } else {
                        text = currentValue
                    }
                }
            },
    )
}
