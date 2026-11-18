package me.rerere.zeroagent.ui.pages.productivity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.ai.provider.*
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.repository.ConversationRepository
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.context.LocalSettings
import me.rerere.zeroagent.utils.base64Encode
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun AutoModelPage() {
    val settings = LocalSettings.current
    val repository: ConversationRepository = koinInject()
    val nav = LocalNavController.current
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var vision by remember { mutableStateOf(false) }
    var localOnly by remember { mutableStateOf(false) }
    var longContext by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val candidates = settings.providers.filter { provider ->
        provider.enabled && (!localOnly || provider is ProviderSetting.LlamaCppLocal || provider is ProviderSetting.LiteRtLocal)
    }.flatMap { provider -> provider.models.filter {
        it.type == ModelType.CHAT && Modality.TEXT in it.outputModalities &&
            (!vision || Modality.IMAGE in it.inputModalities) &&
            (!longContext || (it.contextLength ?: 0) >= 32_000)
    }.map { provider to it } }
    // Unknown prices are never treated as free. Ties retain the user's provider/model order.
    val recommended = candidates.minByOrNull { (_, model) ->
        if (localOnly) 0.0 else (model.pricePromptPerToken?.plus(model.priceCompletionPerToken ?: Double.POSITIVE_INFINITY)
            ?: Double.POSITIVE_INFINITY)
    }
    Scaffold(topBar = { TopAppBar(title = { Text("依任務選模") }, navigationIcon = { BackButton() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("根據你指定的能力和已設定模型挑選。具有價格資料時優先選擇較低的標準文字費率；未提供價格的模型依你的清單順序選擇，並不代表免費或較快。")
            Row { Checkbox(vision, { vision = it }); Text("需要圖片理解", Modifier.padding(top = 12.dp)) }
            Row { Checkbox(localOnly, { localOnly = it }); Text("只使用本機模型", Modifier.padding(top = 12.dp)) }
            Row { Checkbox(longContext, { longContext = it }); Text("已確認至少 32K 上下文", Modifier.padding(top = 12.dp)) }
            OutlinedTextField(prompt, { prompt = it }, label = { Text("任務內容") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            if (recommended == null) Text("目前沒有符合全部條件的已啟用模型，請調整條件或加入模型。")
            else {
                Text("建議：${recommended.first.name} · ${recommended.second.displayName.ifBlank { recommended.second.modelId }}",
                    style = MaterialTheme.typography.titleLarge)
                Text("開啟後仍可在對話輸入區手動換模型；不會自動送出或扣除 API 額度。")
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val model = recommended.second
                            val conversation = Conversation.ofId(Uuid.random(), settings.assistantId).copy(chatModelId = model.id)
                            repository.insertConversation(conversation)
                            nav.navigate(Screen.Chat(conversation.id.toString(), text = prompt.base64Encode()))
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { error = "無法建立對話，請重試。" }
                        finally { busy = false }
                    }
                }) { Text("用此模型開啟對話") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
