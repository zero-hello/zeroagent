package me.rerere.zeroagent.ui.pages.setting.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.RealtimeVoiceProtocol
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.ai.realtime.*
import me.rerere.zeroagent.ui.components.ui.SelectTextField
import me.rerere.zeroagent.utils.localizedErrorMessage
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

@Composable
internal fun RealtimeModelPicker(provider: ProviderSetting, protocol: RealtimeVoiceProtocol, selected: String, onSelect: (String) -> Unit) {
    val client: OkHttpClient = koinInject()
    val context = LocalContext.current
    val identity = when (provider) {
        is ProviderSetting.OpenAI -> provider.baseUrl to provider.apiKey
        is ProviderSetting.Google -> provider.baseUrl to provider.apiKey
        else -> "" to ""
    }
    var revision by remember { mutableIntStateOf(0) }
    var options by remember(provider.id, identity, protocol) { mutableStateOf(emptyList<RealtimeModelOption>()) }
    var loading by remember(provider.id, identity, protocol) { mutableStateOf(false) }
    var error by remember(provider.id, identity, protocol) { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(provider.id, identity, protocol, revision) {
        loading = true; error = null
        try { options = RealtimeModelCatalog.load(client, provider, protocol) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e }
        finally { loading = false }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectTextField(value = selected, options = options, onValueChange = onSelect,
            onOptionSelected = { onSelect(it.id) }, optionToString = { if (it.name == it.id) it.id else "${it.name} (${it.id})" },
            label = { Text(stringResource(R.string.za8_voice_model)) }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.za8_voice_models_hint), style = MaterialTheme.typography.bodySmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(context.localizedErrorMessage(it), color = MaterialTheme.colorScheme.error) }
        if (!loading && error == null && options.isEmpty()) Text(stringResource(R.string.za8_voice_models_empty), style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = !loading, onClick = { revision++ }) { Text(stringResource(R.string.za8_voice_refresh)) }
    }
}
