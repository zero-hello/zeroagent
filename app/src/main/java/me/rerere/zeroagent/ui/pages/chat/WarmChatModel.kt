package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
internal fun WarmChatModel(chatId: Uuid, model: Model?, providers: List<ProviderSetting>, isGenerating: Boolean) {
    if (model?.generation?.warmOnChatOpen != true) return
    val provider = model.findProvider(providers)?.takeIf { it.enabled } ?: return
    if (provider !is ProviderSetting.LlamaCppLocal && provider !is ProviderSetting.LiteRtLocal) return
    val llama = koinInject<LlamaCppProvider>()
    val liteRt = koinInject<LiteRtProvider>()
    val limits = koinInject<GenerationResourceLimits>()
    LaunchedEffect(chatId, provider.id, model.id) {
        if (isGenerating) return@LaunchedEffect
        delay(350)
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
        }
    }
}