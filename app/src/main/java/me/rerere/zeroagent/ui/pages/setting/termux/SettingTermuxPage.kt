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

/** Embedded terminal settings; historical route retained for navigation compatibility. */
@Composable
fun SettingTermuxPage(
    vm: SettingTermuxViewModel = koinViewModel(),
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.embedded_terminal_title)) },
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
            me.rerere.zeroagent.ui.components.terminal.EmbeddedTerminalEnvironmentPanel()

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

