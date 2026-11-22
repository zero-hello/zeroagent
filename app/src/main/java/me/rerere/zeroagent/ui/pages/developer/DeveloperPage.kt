package me.rerere.zeroagent.ui.pages.developer

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.zeroagent.BuildConfig
import me.rerere.zeroagent.data.ai.AILogging
import me.rerere.zeroagent.data.ai.AILoggingManager
import me.rerere.zeroagent.data.datastore.AiLogLevel
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.components.ui.Switch
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.utils.plus
import org.koin.compose.koinInject

@Composable
fun DeveloperPage() {
    val store: SettingsStore = koinInject()
    val diagnostics: AILoggingManager = koinInject()
    val settings by store.settingsFlow.collectAsStateWithLifecycle()
    val logs by diagnostics.getLogs().collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val nav = LocalNavController.current
    val context = LocalContext.current
    var showRequests by rememberSaveable { mutableStateOf(false) }
    var onlyStreaming by rememberSaveable { mutableStateOf(false) }
    val requests = logs.filterIsInstance<AILogging.Generation>()
    ZeroAgentScaffold(topBar = { TopAppBar(title = { Text("開發者選項") }, navigationIcon = { BackButton() }) }) { insets ->
        if (settings.developerMode) LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = insets + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup {
                    item(headlineContent = { Text("使用開發者選項") }, supportingContent = { Text("關閉後會隱藏設定入口，並清除暫存的診斷紀錄。") },
                        trailingContent = { Switch(checked = true, onCheckedChange = { enabled ->
                            if (!enabled) scope.launch {
                                store.update { it.copy(developerMode = false, aiLogLevel = AiLogLevel.OFF) }
                                diagnostics.clearLogs()
                                nav.popBackStack()
                            }
                        }) })
                }
            }
            item {
                CardGroup(title = { Text("AI 連線診斷") }) {
                    item(headlineContent = { Text("記錄請求摘要") }, supportingContent = { Text("只在記憶體保留請求類型與數量。") },
                        trailingContent = { Switch(checked = settings.aiLogLevel != AiLogLevel.OFF, onCheckedChange = { enabled ->
                            scope.launch { diagnostics.setLogLevel(if (enabled) AiLogLevel.INFO else AiLogLevel.OFF) }
                        }) })
                    item(headlineContent = { Text("展開最近的請求") }, supportingContent = { Text("共 ${requests.size} 筆，${requests.count { it.stream }} 筆串流。") },
                        trailingContent = { Switch(checked = showRequests, onCheckedChange = { showRequests = it }) })
                    item(headlineContent = { Text("只顯示串流請求") }, trailingContent = { Switch(checked = onlyStreaming, onCheckedChange = { onlyStreaming = it }) })
                    if (requests.isNotEmpty()) item(onClick = { diagnostics.clearLogs() }, headlineContent = { Text("清除診斷紀錄") })
                }
            }
            if (showRequests) items(requests.asReversed().filter { !onlyStreaming || it.stream }) { request ->
                CardGroup {
                    item(headlineContent = { Text(if (request.stream) "串流請求" else "一般請求") }, supportingContent = {
                        Text("輸入訊息 ${request.inputMessageCount} 則 · 可用工具 ${request.availableToolCount} 個")
                    })
                }
            }
            item {
                CardGroup(title = { Text("裝置資訊", style = MaterialTheme.typography.titleSmall) }) {
                    item(headlineContent = { Text("複製相容性資訊") }, supportingContent = { Text("包含應用程式版本、Android 版本與裝置架構。") }, onClick = {
                        val summary = "ZeroAgent ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n${Build.SUPPORTED_ABIS.joinToString()}"
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("ZeroAgent", summary))
                        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "已複製", Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
    }
}