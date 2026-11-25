package me.rerere.zeroagent.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.findModelById
import me.rerere.zeroagent.data.datastore.findProvider
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.repository.ConversationRepository
import me.rerere.zeroagent.data.repository.ScheduledJobRepository
import me.rerere.zeroagent.service.ChatService
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private data class ReplacementOption(val model: Model, val providerName: String)
private data class ModelImpact(val id: Uuid, val model: Model?, val isDefault: Boolean, val conversations: List<Conversation>, val schedules: List<String>)

private fun compatible(previous: Model?, replacement: Model): Boolean = replacement.type == ModelType.CHAT &&
    (previous == null || (replacement.inputModalities.containsAll(previous.inputModalities) &&
        replacement.outputModalities.containsAll(previous.outputModalities) &&
        replacement.abilities.containsAll(previous.abilities) &&
        (previous.contextLength == null || (replacement.contextLength ?: 0) >= previous.contextLength!!)))

@Composable
internal fun ModelReplacementControl(settings: Settings) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("接替無法使用的模型") }
    if (!open) return
    val repo = koinInject<ConversationRepository>()
    val schedules = koinInject<ScheduledJobRepository>()
    val store = koinInject<SettingsStore>()
    val service = koinInject<ChatService>()
    val scope = rememberCoroutineScope()
    var sourceId by remember { mutableStateOf<Uuid?>(null) }
    var choice by remember { mutableStateOf<ReplacementOption?>(null) }
    var applying by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val available = remember(settings.providers) {
        settings.providers.filter { it.enabled }.flatMap { it.models }.distinctBy { it.id }.mapNotNull { model ->
            val provider = model.findProvider(settings.providers)?.takeIf { it.enabled } ?: return@mapNotNull null
            model.takeIf { it.modelId.isNotBlank() && it.type == ModelType.CHAT }?.let { ReplacementOption(it, provider.name) }
        }
    }
    val impacts by produceState<List<ModelImpact>?>(null, settings, refresh) {
        try {
            val chats = repo.getAllConversations().first()
            val jobs = schedules.observeAll().first().filter { it.mode == "llm" }
            val availableIds = available.mapTo(hashSetOf()) { it.model.id }
            val ids = (chats.mapNotNull { it.chatModelId } + settings.chatModelId).distinct()
                .filter { it != Uuid.NIL && it !in availableIds }
            value = ids.map { id ->
                val isDefault = id == settings.chatModelId
                ModelImpact(id, settings.findModelById(id), isDefault,
                    chats.filter { it.chatModelId == id || (isDefault && it.chatModelId == null) },
                    jobs.map { it.name }.takeIf { isDefault }.orEmpty())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            notice = "無法讀取受影響的資料。"
            value = emptyList()
        }
    }
    val impact = impacts?.firstOrNull { it.id == sourceId }
    ZeroAgentAlertDialog(
        onDismissRequest = { if (!applying) open = false },
        title = { Text("接替無法使用的模型") },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                notice?.let { Text(it) }
                val rows = impacts
                if (rows == null) Text("正在檢查對話和排程…")
                else if (rows.isEmpty()) Text("沒有停用或已移除的模型需要接替。")
                if (impact == null) rows.orEmpty().forEach { item ->
                    TextButton(onClick = { sourceId = item.id; choice = null; notice = null }) {
                        val label = item.model?.let { it.displayName.ifBlank { it.modelId } } ?: "已移除模型 ${item.id.toString().take(8)}"
                        Text("$label · ${item.conversations.size} 個對話、${item.schedules.size} 個排程")
                    }
                } else {
                    Text("受影響的對話 ${impact.conversations.size} 個，排程 ${impact.schedules.size} 個。")
                    impact.conversations.forEach { Text("對話 · ${it.title.ifBlank { "未命名對話" }}") }
                    impact.schedules.forEach { Text("排程 · $it") }
                    val choices = available.filter { compatible(impact.model, it.model) }
                    if (choices.isEmpty()) Text("沒有符合原模型能力的選項。")
                    Text(if (impact.model == null) "原模型的能力資料已移除，請先確認替代模型能處理你用的附件和工具。" else "可選模型符合原模型已知的輸入、工具與上下文能力。")
                    choices.forEach { item -> TextButton(onClick = { choice = item }, enabled = !applying) {
                        val label = item.model.displayName.ifBlank { item.model.modelId }
                        Text("${if (choice?.model?.id == item.model.id) "已選取 · " else ""}$label · ${item.providerName}")
                    } }
                    choice?.let { Text("套用後，受影響對話的後續內容及排程提示詞會交給 ${it.providerName} 處理。") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !applying && choice != null && impact != null, onClick = {
                val selected = choice ?: return@TextButton
                val affected = impact ?: return@TextButton
                applying = true
                scope.launch {
                    try {
                        val current = store.settingsFlow.first()
                        val replacement = current.findModelById(selected.model.id)
                            ?.takeIf { compatible(affected.model, it) && it.findProvider(current.providers)?.enabled == true }
                            ?: error("替代模型已變更，請重新選擇。")
                        val explicit = affected.conversations.filter { it.chatModelId == affected.id }
                        val jobs = service.getConversationJobs().first()
                        check(affected.conversations.none { jobs[it.id]?.isActive == true }) { "有受影響的對話正在生成。完成或停止後再套用。" }
                        val updated = explicit.count { service.replaceUnavailableModelForChat(it.id, affected.id, replacement.id) }
                        if (affected.isDefault) store.update { latest ->
                            if (latest.chatModelId == affected.id) latest.copy(chatModelId = replacement.id) else latest
                        }
                        notice = if (updated < explicit.size) "已更新 $updated 個指定模型的對話。其餘對話狀態已變更，請重新檢查。" else "已套用替代模型。"
                        sourceId = null
                        choice = null
                        refresh++
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        notice = error.message ?: "套用失敗，請重新檢查受影響的項目。"
                        refresh++
                    } finally {
                        applying = false
                    }
                }
            }) { Text(if (applying) "正在套用…" else "確認接替") }
        },
        dismissButton = { TextButton(onClick = { open = false }, enabled = !applying) { Text("關閉") } },
    )
}