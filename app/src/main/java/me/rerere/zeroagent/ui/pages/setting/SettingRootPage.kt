package me.rerere.zeroagent.ui.pages.setting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.zeroagent.data.root.RootAccessManager
import me.rerere.zeroagent.ui.context.LocalNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingRootPage() {
    val context = LocalContext.current
    val nav = LocalNavController.current
    val manager = remember { RootAccessManager(context) }
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(manager.enabled) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (enabled) context.getString(me.rerere.zeroagent.R.string.zeroagent6s_root_enabled) else context.getString(me.rerere.zeroagent.R.string.zeroagent6s_root_off)) }
    Scaffold(topBar = { TopAppBar(title = { Text(context.getString(me.rerere.zeroagent.R.string.zeroagent6s_root_title)) }, navigationIcon = {
        me.rerere.zeroagent.ui.components.nav.BackButton()
    }, colors = me.rerere.zeroagent.ui.theme.CustomColors.topBarColors) }) { inset ->
        Column(Modifier.padding(inset).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(context.getString(me.rerere.zeroagent.R.string.zeroagent6s_root_heading), style = MaterialTheme.typography.headlineSmall)
            Text(context.getString(me.rerere.zeroagent.R.string.zeroagent6s_root_intro))
            me.rerere.zeroagent.ui.components.ui.CardGroup {
            item(headlineContent = { Text(context.getString(me.rerere.zeroagent.R.string.zeroagent6s_root_allow)) }, supportingContent = { Text(status) }, trailingContent = {
                Switch(checked = enabled, enabled = !busy, onCheckedChange = { turnOn ->
                    if (!turnOn) { manager.disable(); enabled = false; status = context.getString(me.rerere.zeroagent.R.string.zeroagent6s_root_disabled) }
                    else scope.launch {
                        busy = true
                        try {
                            val result = manager.request()
                            enabled = manager.enabled
                            status = if (enabled) context.getString(me.rerere.zeroagent.R.string.zeroagent6s_root_granted) else context.getString(me.rerere.zeroagent.R.string.zeroagent6s_root_denied)
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) { status = context.getString(me.rerere.zeroagent.R.string.zeroagent6s_root_error) }
                        finally { busy = false }
                    }
                })
            })
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(context.getString(me.rerere.zeroagent.R.string.zeroagent6s_root_approval))
            Text(context.getString(me.rerere.zeroagent.R.string.zeroagent6s_root_revoke))
            Text(context.getString(me.rerere.zeroagent.R.string.zeroagent6s_root_not_undo))
        }
    }
}
