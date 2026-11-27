package me.rerere.zeroagent.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import me.rerere.zeroagent.ui.designsystem.ZeroCapsuleButton as Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.lab.LabPreferences
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.components.ui.Switch
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold

@Composable
internal fun SettingFunPage() {
    val context = LocalContext.current
    val pawsFlow = remember(context) { LabPreferences.flow(context, "cat_paws", "false") }
    val paws by pawsFlow.collectAsStateWithLifecycle(initialValue = LabPreferences.get(context, "cat_paws", "false"))
    ZeroAgentScaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.fun_features)) }, navigationIcon = { BackButton() }) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardGroup {
                item(
                    headlineContent = { Text("貓咪掌印打字特效") },
                    supportingContent = { Text("AI 的新文字浮現時，偶爾會有一枚小掌印輕輕飄過。系統移除動畫時會停用。") },
                    trailingContent = { Switch(checked = paws == "true", onCheckedChange = { LabPreferences.set(context, "cat_paws", it.toString()) }) },
                )
            }
        }
    }
}
