package me.rerere.zeroagent.ui.hooks

import android.content.Context
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.providers.*
import okhttp3.OkHttpClient

fun createAsrController(context: Context, httpClient: OkHttpClient, provider: ASRProviderSetting): ASRController? {
        return when (provider) {
            is ASRProviderSetting.Cloud -> {
                if (provider.apiKey.isBlank()) return null
                me.rerere.asr.providers.CloudSpeechController(context, httpClient, provider)
            }
            is ASRProviderSetting.OpenAIRealtime -> {
                if (provider.apiKey.isBlank()) return null
                OpenAIRealtimeASRController(context, httpClient, provider)
            }

            is ASRProviderSetting.DashScope -> {
                if (provider.apiKey.isBlank()) return null
                DashScopeASRController(context, httpClient, provider)
            }

            is ASRProviderSetting.Volcengine -> {
                if (provider.apiKey.isBlank()) return null
                VolcengineASRController(context, httpClient, provider)
            }

            is ASRProviderSetting.MiMo -> {
                if (provider.apiKey.isBlank()) return null
                MiMoASRController(context, httpClient, provider)
            }

            is ASRProviderSetting.Step -> {
                if (provider.apiKey.isBlank()) return null
                StepASRController(context, httpClient, provider)
            }

            is ASRProviderSetting.DashScopeStreaming -> {
                if (provider.apiKey.isBlank()) return null
                DashScopeStreamingASRController(context, httpClient, provider)
            }
        }
}
