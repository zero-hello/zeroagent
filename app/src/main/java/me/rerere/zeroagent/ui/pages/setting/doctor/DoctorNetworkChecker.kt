package me.rerere.zeroagent.ui.pages.setting.doctor

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.data.datastore.SettingsStore
import java.net.InetAddress
import me.rerere.zeroagent.R

internal class DoctorNetworkChecker(
    context: Context,
    private val settingsStore: SettingsStore,
) : DoctorCheckResources(context) {
    suspend fun providerChecks(): List<DoctorCheck> = buildList {
        runCatching {
            val settings = settingsStore.settingsFlow.first { !it.init }
            val provs = settings.providers
            val configured = provs.count { p ->
                when (p) {
                    is me.rerere.ai.provider.ProviderSetting.OpenAI -> p.apiKey.isNotBlank()
                    is me.rerere.ai.provider.ProviderSetting.Google -> p.apiKey.isNotBlank()
                    is me.rerere.ai.provider.ProviderSetting.Claude -> p.apiKey.isNotBlank()
                    is me.rerere.ai.provider.ProviderSetting.AICore -> p.enabled  // on-device, no API key
                    // Local provider (LiteRT): usable when enabled AND at least one model has
                    // been loaded/downloaded. A disabled provider with no models is the factory
                    // default — don't count it.
                    is me.rerere.ai.provider.ProviderSetting.LiteRtLocal -> p.enabled && p.models.isNotEmpty()
                    // Local provider (llama.cpp): usable when enabled AND at least one model
                    // has been loaded, same criterion as LiteRT above.
                    is me.rerere.ai.provider.ProviderSetting.LlamaCppLocal -> p.enabled && p.models.isNotEmpty()
                    is me.rerere.ai.provider.ProviderSetting.Codex -> p.enabled  // OAuth, no API key
                    is me.rerere.ai.provider.ProviderSetting.Grok -> p.enabled  // OAuth, no API key
                    is me.rerere.ai.provider.ProviderSetting.GeminiOAuth -> p.enabled  // OAuth, no API key
                }
            }
            add(
                DoctorCheck(
                    id = "net.providers",
                    category = DoctorCategory.Network,
                    label = text(R.string.doctor_check_providers),
                    detail = text(R.string.doctor_detail_providers_count, configured, provs.size),
                    severity = if (configured > 0) Severity.OK else Severity.WARN,
                    fix = FixAction.OpenAppRoute(text(R.string.doctor_fix_open_providers), AppRouteKey.SettingProvider),
                )
            )
        }
    }

    suspend fun dnsChecks(): List<DoctorCheck> = buildList {
        // DNS sanity — confirms the OkHttp clients aren't stuck on a stale resolver.
        val dnsOk = withTimeoutOrNull(2_500L) {
            runCatching { InetAddress.getByName("dns.google") != null }.getOrDefault(false)
        } == true
        add(
            DoctorCheck(
                id = "net.dns",
                category = DoctorCategory.Network,
                label = text(R.string.doctor_check_dns),
                detail = if (dnsOk) text(R.string.doctor_detail_dns_ok)
                else text(R.string.doctor_detail_dns_fail),
                severity = if (dnsOk) Severity.OK else Severity.WARN,
            )
        )
    }
}
