package me.rerere.zeroagent.ui.pages.chat

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.llamacpp.LlamaCppCatalog
import me.rerere.locallm.LocalRuntime
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.ui.DownloadProgress
import me.rerere.zeroagent.ui.components.ui.DownloadProgressIndicator
import me.rerere.zeroagent.ui.components.ui.ZeroAgentCard
import me.rerere.zeroagent.ui.pages.setting.locallm.LocalDownloadControls
import me.rerere.zeroagent.ui.pages.setting.locallm.SettingLocalLlmViewModel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

internal fun ProviderSetting.acceptsOnboardingApiKey(): Boolean =
    this is ProviderSetting.OpenAI || this is ProviderSetting.Google || this is ProviderSetting.Claude

internal fun ProviderSetting.isReadyForOnboarding(): Boolean {
    if (!enabled || models.none { it.type == ModelType.CHAT }) return false
    return when (this) {
        is ProviderSetting.OpenAI -> apiKey.isNotBlank() ||
            (!builtIn && (customHeaders.isNotEmpty() || baseUrl.toHttpUrlOrNull()?.host in setOf("localhost", "127.0.0.1", "::1")))
        is ProviderSetting.Google -> apiKey.isNotBlank()
        is ProviderSetting.Claude -> apiKey.isNotBlank()
        is ProviderSetting.AICore, is ProviderSetting.LiteRtLocal, is ProviderSetting.LlamaCppLocal,
        is ProviderSetting.Codex, is ProviderSetting.Grok, is ProviderSetting.GeminiOAuth -> true
    }
}

@Composable
internal fun ChatModelSetupCard(
    providers: List<ProviderSetting>,
    onProviderClick: (ProviderSetting) -> Unit,
    onAllProvidersClick: () -> Unit,
    canOfferLocal: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        ZeroAgentCard(modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.chat_setup_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.chat_setup_body), style = MaterialTheme.typography.bodyMedium)
                var choosingProvider by remember { mutableStateOf(false) }
                Box {
                    Button(
                        onClick = { if (providers.isEmpty()) onAllProvidersClick() else choosingProvider = true },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.chat_setup_provider)) }
                    DropdownMenu(expanded = choosingProvider, onDismissRequest = { choosingProvider = false }) {
                        providers.forEach { provider ->
                            DropdownMenuItem(text = { Text(provider.name) }, onClick = {
                                choosingProvider = false
                                onProviderClick(provider)
                            })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.chat_setup_all_providers)) }, onClick = {
                            choosingProvider = false
                            onAllProvidersClick()
                        })
                    }
                }
                if (canOfferLocal) StarterGgufDownload()
            }
        }
    }
}

@Composable
private fun StarterGgufDownload() {
    val starter = LlamaCppCatalog.ENTRIES.firstOrNull() ?: return
    val runtime = LocalRuntime.LlamaCpp
    val vm = koinViewModel<SettingLocalLlmViewModel>(
        key = "configure-${runtime.displayName}",
        parameters = { parametersOf(runtime) },
    )
    val context = LocalContext.current
    val progress by vm.downloadProgress.collectAsStateWithLifecycle()
    val installedModels by vm.installedModelFiles.collectAsStateWithLifecycle()
    val error by vm.errorMessage.collectAsStateWithLifecycle()
    val paused by vm.downloadPaused.collectAsStateWithLifecycle()
    val capabilities by vm.capabilities.collectAsStateWithLifecycle()
    if (capabilities?.cpuAvailable == false) {
        Text(stringResource(R.string.chat_setup_local_unavailable), style = MaterialTheme.typography.bodySmall)
        return
    }
    if (starter.file !in installedModels) {
        OutlinedButton(
            onClick = { vm.startManualDownload(starter.resolveUrl()) },
            enabled = progress == null && !paused && capabilities?.cpuAvailable == true,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.chat_setup_local, starter.displayName, Formatter.formatFileSize(context, starter.sizeBytes)))
        }
        progress?.let { DownloadProgressIndicator(DownloadProgress(it.bytesRead, it.totalBytes)) }
        LocalDownloadControls(vm)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}