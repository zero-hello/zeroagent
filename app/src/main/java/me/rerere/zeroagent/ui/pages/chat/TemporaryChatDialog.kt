package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.findProvider
import me.rerere.zeroagent.data.usage.UsageCallContext
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import me.rerere.zeroagent.utils.localizedErrorMessage
import org.koin.compose.koinInject

@Composable
internal fun TemporaryChatDialog(settings: Settings, model: Model?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager: ProviderManager = koinInject()
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<UIMessage>() }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val requestExit = {
        if (history.isNotEmpty() || input.isNotBlank() || sending) confirmExit = true else onDismiss()
    }
    if (confirmExit) ZeroAgentAlertDialog(
        onDismissRequest = { confirmExit = false },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("離開臨時會話？") },
        text = { Text("這裡的對話和未送出文字會離開畫面。使用過的模型服務商可能仍保存收到的內容。") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("離開會話") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("繼續聊天") } },
    )
    val provider = model?.findProvider(settings.providers)?.takeIf { it.enabled }
    ZeroAgentAlertDialog(
        onDismissRequest = requestExit,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.temporary_chat)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.temporary_chat_description))
                if (history.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history, key = { it.id.toString() }) { message -> Text(message.toText()) }
                }
                if (sending) CircularProgressIndicator()
                error?.let { Text(it) }
                OutlinedTextField(value = input, onValueChange = { input = it }, enabled = !sending,
                    label = { Text(stringResource(R.string.chat_input_hint_1)) }, maxLines = 4)
            }
        },
        dismissButton = { TextButton(onClick = requestExit) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            TextButton(enabled = !sending && input.isNotBlank() && model != null && provider != null, onClick = {
                if (model == null || provider == null) return@TextButton
                val user = UIMessage.user(input.trim())
                val messages = history.takeLast(20) + user
                sending = true
                error = null
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO + UsageCallContext(privateSession = true)) {
                            manager.getProviderByType(provider).generateText(provider, messages,
                                TextGenerationParams(model = model.copy(tools = emptySet()),
                                    tools = emptyList(), textToolCallParsing = false))
                        }
                        history.add(user)
                        history.add(UIMessage.assistant(result.message.toText()))
                        input = ""
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = context.localizedErrorMessage(e)
                    } finally {
                        sending = false
                    }
                }
            }) { Text(stringResource(R.string.temporary_chat_send)) }
        },
    )
}
