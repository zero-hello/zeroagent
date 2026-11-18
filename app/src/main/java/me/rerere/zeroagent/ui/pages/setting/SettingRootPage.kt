package me.rerere.zeroagent.ui.pages.setting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import me.rerere.zeroagent.data.root.RootAccessManager
import me.rerere.zeroagent.data.root.RootAccessStatus
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import me.rerere.zeroagent.ui.components.ui.Switch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingRootPage() {
    val context = LocalContext.current
    val nav = LocalNavController.current
    val manager = remember { RootAccessManager(context) }
    val scope = rememberCoroutineScope()
    val status by manager.status.collectAsStateWithLifecycle()
    val enabled = manager.enabled
    val busy = status == RootAccessStatus.CHECKING
    var confirmEnable by remember { mutableStateOf(false) }
    val statusRes = when (status) {
        RootAccessStatus.DISABLED -> R.string.zeroagent6s_root_off
        RootAccessStatus.UNVERIFIED -> R.string.root_status_unverified
        RootAccessStatus.CHECKING -> R.string.root_status_checking
        RootAccessStatus.AVAILABLE -> R.string.zeroagent6s_root_granted
        RootAccessStatus.UNAVAILABLE -> R.string.zeroagent6s_root_denied
    }
    LaunchedEffect(manager) { if (manager.enabled) manager.refresh() }
    if (confirmEnable) AlertDialog(
        onDismissRequest = { confirmEnable = false },
        title = { Text(stringResource(R.string.zeroagent6s_root_allow)) },
        text = { Text(stringResource(R.string.root_enable_warning)) },
        confirmButton = { TextButton(onClick = {
            confirmEnable = false
            scope.launch { manager.request() }
        }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = { confirmEnable = false }) { Text(stringResource(R.string.cancel)) } },
    )
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(me.rerere.zeroagent.R.string.zeroagent6s_root_title)) }, navigationIcon = {
        me.rerere.zeroagent.ui.components.nav.BackButton()
    }, colors = me.rerere.zeroagent.ui.theme.CustomColors.topBarColors) }) { inset ->
        Column(Modifier.padding(inset).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(me.rerere.zeroagent.R.string.zeroagent6s_root_heading), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(me.rerere.zeroagent.R.string.zeroagent6s_root_intro))
            me.rerere.zeroagent.ui.components.ui.CardGroup {
            item(headlineContent = { Text(stringResource(me.rerere.zeroagent.R.string.zeroagent6s_root_allow)) }, supportingContent = { Text(stringResource(statusRes)) }, trailingContent = {
                Switch(checked = enabled, enabled = !busy, onCheckedChange = { turnOn ->
                    if (!turnOn) manager.disable() else confirmEnable = true
                })
            })
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(enabled = enabled && !busy, onClick = { scope.launch { manager.refresh() } }) {
                Text(stringResource(R.string.root_recheck))
            }
            Text(stringResource(me.rerere.zeroagent.R.string.zeroagent6s_root_approval))
            Text(stringResource(me.rerere.zeroagent.R.string.zeroagent6s_root_revoke))
            Text(stringResource(me.rerere.zeroagent.R.string.zeroagent6s_root_not_undo))
        }
    }
}
