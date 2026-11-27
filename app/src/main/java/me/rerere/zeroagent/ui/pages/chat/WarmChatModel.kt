package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.llamacpp.LlamaCppProvider
import me.rerere.locallm.litert.LiteRtProvider
import me.rerere.zeroagent.data.datastore.findProvider
import me.rerere.zeroagent.data.local.GenerationResourceLimits
import me.rerere.zeroagent.data.usage.UsagePurpose
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
internal fun WarmChatModel(chatId: Uuid, model: Model?, providers: List<ProviderSetting>, isGenerating: Boolean): Boolean {
    if (model?.generation?.warmOnChatOpen != true) return false
    val provider = model.findProvider(providers)?.takeIf { it.enabled } ?: return false
    if (provider !is ProviderSetting.LlamaCppLocal && provider !is ProviderSetting.LiteRtLocal) return false
    val llama = koinInject<LlamaCppProvider>()
    val liteRt = koinInject<LiteRtProvider>()
    val limits = koinInject<GenerationResourceLimits>()
    var warming by remember(chatId, provider.id, model.id) { mutableStateOf(false) }
    val generating by rememberUpdatedState(isGenerating)
    LaunchedEffect(chatId, provider.id, model.id) {
        if (generating) return@LaunchedEffect
        delay(350)
        if (generating) return@LaunchedEffect
        warming = true
        try {
            withContext(Dispatchers.IO) {
                limits.ensureAllowed(provider, UsagePurpose.MAIN)
                when (provider) {
                    is ProviderSetting.LlamaCppLocal -> llama.warmModel(model)
                    is ProviderSetting.LiteRtLocal -> liteRt.warmModel(model)
                    else -> Unit
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Normal generation reports load failures when the user sends a message.
        } finally {
            warming = false
        }
    }
    return warming
}