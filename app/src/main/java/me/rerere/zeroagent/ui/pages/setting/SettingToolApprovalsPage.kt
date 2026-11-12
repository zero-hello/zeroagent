package me.rerere.zeroagent.ui.pages.setting

import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog as AlertDialog
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import me.rerere.zeroagent.ui.components.ui.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.agentdef.LocalToolGroups
import me.rerere.zeroagent.data.ai.tools.LocalToolInventory
import me.rerere.zeroagent.data.ai.tools.LocalToolPalette
import me.rerere.zeroagent.data.ai.tools.LocalTools
import me.rerere.zeroagent.data.ai.tools.ToolApprovalDefaults
import me.rerere.zeroagent.data.ai.tools.ToolApprovalPolicy
import me.rerere.zeroagent.data.preferences.ToolApprovalPreferences
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.theme.CustomColors
import me.rerere.zeroagent.utils.plus
import org.koin.compose.koinInject

/** Per-tool extra approval rules; blanket foreground access now lives in each conversation. */
@Composable
fun SettingToolApprovalsPage() {
    val prefs: ToolApprovalPreferences = koinInject()
    val blacklist by prefs.blacklistFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()

    // The local tool directory, one group at a time - the same construction the expert editor's
    // palette uses, so a name here and a name there can never disagree. A failure hides the list
    // rather than taking the screen down with it.
    val localToolFactory = koinInject<LocalTools>()
    val toolPalette = remember {
        runCatching {
            LocalToolPalette.build(
                LocalToolGroups.all.map { group ->
                    LocalToolInventory(
                        group = group,
                        tools = localToolFactory.getTools(listOf(group)),
                    )
                }
            )
        }.getOrNull()
    }
    val adjustableNames = toolPalette?.hits.orEmpty()
        .map { it.name }
        .filterNot { it == ToolApprovalPolicy.ASK_USER_TOOL_NAME || ToolApprovalDefaults.isHardFloor(it) || it.startsWith("workspace_") }
        .toSet()
    val selectedCount = blacklist.intersect(adjustableNames).size

    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_tool_approvals)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(headlineContent = { Text(stringResource(R.string.zeroagent8_access_title)) },
                        supportingContent = { Text(stringResource(R.string.zeroagent8_access_hint)) })
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.zeroagent8_tool_rules_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = when {
                            adjustableNames.isEmpty() || selectedCount == 0 ->
                                stringResource(R.string.setting_page_tool_approvals_none_selected)
                            selectedCount == adjustableNames.size ->
                                stringResource(R.string.setting_page_tool_approvals_all_selected, adjustableNames.size)
                            else -> stringResource(
                                R.string.setting_page_tool_approvals_partial_selected,
                                selectedCount,
                                adjustableNames.size,
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (toolPalette != null) item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        enabled = selectedCount < adjustableNames.size,
                        onClick = { scope.launch { prefs.addAllToBlacklist(adjustableNames) } },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.setting_page_tool_approvals_select_all)) }
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        enabled = selectedCount > 0,
                        onClick = { scope.launch { prefs.removeAllFromBlacklist(adjustableNames) } },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.setting_page_tool_approvals_clear_all)) }
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.setting_page_tool_approvals_search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }

            if (toolPalette == null) {
                item {
                    Text(
                        text = stringResource(R.string.setting_page_tool_approvals_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                // ask_user is a request for input, not a capability: it never consults the
                // blacklist, so showing it a switch would be a lie.
                val hits = (if (query.isBlank()) toolPalette.hits else toolPalette.matchAll(query))
                    .filterNot { it.name == ToolApprovalPolicy.ASK_USER_TOOL_NAME }
                if (hits.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.setting_page_tool_approvals_no_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                } else {
                    toolPalette.groups.forEach { group ->
                        val rows = hits.filter { it.group == group }
                        if (rows.isNotEmpty()) {
                            item(key = "group:${group}") {
                                CardGroup(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    title = { Text(stringResource(localToolTitleRes(group))) },
                                ) {
                                    rows.forEach { hit ->
                                        val floor = ToolApprovalDefaults.isHardFloor(hit.name) || hit.name.startsWith("workspace_")
                                        val checked = floor || hit.name in blacklist
                                        item(
                                            headlineContent = { Text(hit.name) },
                                            supportingContent = {
                                                Text(
                                                    text = if (floor) {
                                                        stringResource(R.string.setting_page_tool_approvals_floor_badge)
                                                    } else {
                                                        stringResource(R.string.setting_page_tool_approvals_switch_desc)
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            },
                                            trailingContent = {
                                                Switch(
                                                    checked = checked,
                                                    enabled = !floor,
                                                    onCheckedChange = { next ->
                                                        scope.launch { prefs.setBlacklisted(hit.name, next) }
                                                    },
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    TextButton(
                        shapes = ButtonDefaults.shapes(), onClick = {
                            val names = toolPalette.hits
                                .map { it.name }
                                .filter { it in ToolApprovalDefaults.DANGEROUS_TOOLS }
                            scope.launch { prefs.addAllToBlacklist(names) }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(stringResource(R.string.setting_page_tool_approvals_add_recommended))
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.setting_page_tool_approvals_mcp_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
