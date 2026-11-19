package me.rerere.zeroagent.ui.pages.productivity

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.*
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.productivity.ComparisonRunner
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import me.rerere.zeroagent.ui.context.LocalSettings
import org.koin.compose.koinInject

private data class ComparisonAnswer(val text: String, val elapsed: Long, val tokens: Int?, val cost: Double?)

@Composable
fun ModelComparePage() {
    val settings = LocalSettings.current
    val manager: ProviderManager = koinInject()
    val choices = settings.providers.filter { it.enabled }.flatMap { provider ->
        provider.models.filter { it.type == ModelType.CHAT && Modality.TEXT in it.outputModalities }
            .map { provider to it }
    }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    val availableSelected = choices.filter { it.second.id.toString() in selected }
    var prompt by rememberSaveable { mutableStateOf("") }
    val answers = remember { mutableStateMapOf<String, ComparisonAnswer>() }
    val errors = remember { mutableStateMapOf<String, String>() }
    var job by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    Scaffold(topBar = { TopAppBar(title = { Text("多模型比較") }, navigationIcon = { BackButton() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("同一個問題交給 2 至 4 個已啟用模型。送出會各自使用供應商額度；此流程不執行裝置工具，也不讀取私人記憶。")
            if (choices.isEmpty()) Text("請先到供應商設定新增可對話的模型。")
            choices.forEach { (provider, model) ->
                val id = model.id.toString()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Checkbox(checked = id in selected, enabled = !running && (id in selected || selected.size < 4),
                        onCheckedChange = { checked -> selected = if (checked) selected + id else selected - id })
                    Column(Modifier.weight(1f).padding(top = 8.dp)) {
                        Text(model.displayName.ifBlank { model.modelId }, style = MaterialTheme.typography.titleMedium)
                        Text(provider.name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("共同問題") },
                enabled = !running, minLines = 3, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !running && availableSelected.map { it.second.id }.distinct().size in 2..4 && prompt.isNotBlank(), onClick = {
                    val snapshot = availableSelected.associateBy { it.second.id.toString() }
                    val question = prompt.trim()
                    answers.clear(); errors.clear(); running = true
                    job = scope.launch {
                        try {
                            ComparisonRunner.run(snapshot.keys.toList(), request = { id ->
                                val (provider, model) = snapshot.getValue(id)
                                val start = android.os.SystemClock.elapsedRealtime()
                                val result = manager.getProviderByType(provider).generateText(provider,
                                    listOf(UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text(question)))),
                                    TextGenerationParams(model = model, customHeaders = model.customHeaders,
                                        customBody = model.customBodies))
                                ComparisonAnswer(result.message.toText(), android.os.SystemClock.elapsedRealtime() - start,
                                    result.usage?.totalTokens, result.usage?.cost)
                            }, update = { id, result ->
                                result.onSuccess { answers[id] = it }.onFailure {
                                    // Provider exceptions can contain request headers. Never display raw exception text.
                                    errors[id] = "此模型請求失敗，請檢查連線、金鑰、額度及模型支援狀態。"
                                }
                            })
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) {
                            snapshot.keys.filter { it !in answers && it !in errors }.forEach {
                                errors[it] = "目前選取的模型已變更，請重新選擇。"
                            }
                        } finally { running = false }
                    }
                }) { Text(if (running) "比較中…" else "開始比較") }
                if (running) TextButton(onClick = { job?.cancel() }) { Text("取消全部") }
            }
            // Horizontal scrolling preserves readable columns on narrow devices and large fonts.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                choices.filter { it.second.id.toString() in selected }.forEach { (provider, model) ->
                    val id = model.id.toString()
                    Card(Modifier.width(320.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(model.displayName.ifBlank { model.modelId }, style = MaterialTheme.typography.titleLarge)
                            Text(provider.name, style = MaterialTheme.typography.labelMedium)
                            val answer = answers[id]
                            when {
                                answer != null -> {
                                    Text("${answer.elapsed / 1000.0} 秒 · ${answer.tokens?.let { "$it tokens" } ?: "未回報用量"}")
                                    Text(answer.cost?.let { "供應商回報費用：US$ $it" } ?: "供應商未回報實際費用，請查看帳單。")
                                    SelectionContainer { Text(answer.text.ifBlank { "模型沒有傳回文字內容。" }) }
                                }
                                errors[id] != null -> Text(errors.getValue(id), color = MaterialTheme.colorScheme.error)
                                running -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("等待此模型回覆…") }
                                else -> Text("選好模型與問題後，按「開始比較」。")
                            }
                        }
                    }
                }
            }
        }
    }
}
