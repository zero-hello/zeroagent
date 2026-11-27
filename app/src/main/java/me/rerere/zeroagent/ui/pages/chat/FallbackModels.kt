package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.util.HttpException
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.findProvider
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import java.io.IOException

internal data class FallbackModel(val model: Model, val provider: ProviderSetting)

internal fun fallbackCandidates(current: Model, settings: Settings): List<FallbackModel> = settings.providers
    .filter { it.enabled }
    .flatMap { provider ->
        provider.models.filter { it.id != current.id && it.type == ModelType.CHAT && it.modelId.isNotBlank() }
            .mapNotNull { model -> model.findProvider(settings.providers)?.takeIf { it.enabled }?.let { FallbackModel(model, it) } }
    }.distinctBy { it.model.id }

internal fun canSuggestFallback(error: Throwable): Boolean = generateSequence(error) { it.cause }.take(8).any {
    when (it) {
        is HttpException -> it.statusCode == 429 || it.statusCode in 500..599
        is IOException -> true
        else -> false
    }
}

@Composable
internal fun FallbackSettings(model: Model, settings: Settings, onUpdate: (Set<String>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("選擇備援模型（${model.generation.fallbackModelIds.size}）") }
    if (!open) return
    val candidates = remember(model, settings.providers) { fallbackCandidates(model, settings) }
    var selected by remember(model.id) { mutableStateOf(model.generation.fallbackModelIds) }
    ZeroAgentAlertDialog(
        onDismissRequest = { open = false },
        title = { Text("指定備援模型") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text("模型暫時無法使用時，ZeroAgent 會顯示你選的選項。")
                if (candidates.isEmpty()) Text("沒有其他已啟用的聊天模型。")
                candidates.forEach { item ->
                    val id = item.model.id.toString()
                    ListItem(
                        headlineContent = { Text(item.model.displayName.ifBlank { item.model.modelId }) },
                        supportingContent = { Text(item.provider.name) },
                        trailingContent = { Checkbox(checked = id in selected, onCheckedChange = {
                            selected = if (it) selected + id else selected - id
                        }) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onUpdate(selected.intersect(candidates.map { it.model.id.toString() }.toSet())); open = false }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } },
    )
}

@Composable
internal fun FallbackModelButton(current: Model, settings: Settings, hasAttachments: Boolean, onSwitch: (Model, Boolean) -> Unit) {
    val candidates = remember(current, settings.providers) {
                fallbackCandidates(current, settings).sortedByDescending { it.model.id.toString() in current.generation.fallbackModelIds }
    }
    if (candidates.isEmpty()) return
    var open by remember(current.id) { mutableStateOf(false) }
    var selection by remember(current.id) { mutableStateOf<FallbackModel?>(null) }
    TextButton(onClick = { open = true }) { Text("選擇備援模型") }
    if (open) ZeroAgentAlertDialog(
        onDismissRequest = { open = false },
        title = { Text("切換備援模型") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                candidates.forEach { item ->
                    TextButton(onClick = { open = false; selection = item }) {
                        Text("${item.model.displayName.ifBlank { item.model.modelId }} · ${item.provider.name}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("取消") } },
    )
    selection?.let { item ->
        ZeroAgentAlertDialog(
            onDismissRequest = { selection = null },
            title = { Text("改用 ${item.provider.name}？") },
            text = {
                val data = if (hasAttachments) "對話、適用的背景資料和附件" else "對話和適用的背景資料"
                Text("接續時，$data 會送給 ${item.provider.name}。已收到的回覆會留在聊天室。")
            },
            confirmButton = { TextButton(onClick = { selection = null; onSwitch(item.model, true) }) { Text("切換並接續") } },
            dismissButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = { selection = null }) { Text("取消") }
                    TextButton(onClick = { selection = null; onSwitch(item.model, false) }) { Text("只切換") }
                }
            },
        )
    }
}