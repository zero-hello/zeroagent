package me.rerere.zeroagent.data.datastore

import java.net.URI

/** Retired stock catalog entries; opaque IDs preserve import/data compatibility. */
val RETIRED_PROVIDER_IDS: Set<String> = setOf(
    "1b1395ed-b702-4aeb-8bc1-b681c4456953",
    "2a05506f-3a59-450a-a493-33a82bc85a81",
    "386e0f29-8228-4512-affe-8fd8add82d88",
    "3bc40dc1-b11a-46fa-863b-6306971223be",
    "3dfd6f9b-f9d9-417f-80c1-ff8d77184191",
    "4da09554-8844-4cc8-a4a9-fe1b2515e91b",
    "53027b08-1b58-43d5-90ed-29173203e3d8",
    "56a94d29-c88b-41c5-8e09-38a7612d6cf8",
    "a2bafe83-eaf8-47bf-a8c7-3dd82d89f637",
    "a8d2d463-e8c0-41f2-b89e-f5eb8e716cce",
    "aecf04fd-cb5c-4582-aed2-e8bf393923fd",
    "afbc54ad-807e-4455-9594-7d7a546356ad",
    "b4deabea-20fb-4101-a74c-65679c7e4754",
    "b55716c7-e465-4666-a788-4243aab13fb3",
    "d6c4d8c6-3f62-4ca9-a6f3-7ade6b15ecc3",
    "da020a90-f7b3-4c29-b90e-c511a0630630",
    "da93779f-3956-48cc-82ef-67bb482eaaf7",
    "ef5d149b-8e34-404b-818c-6ec242e5c3c5",
    "f099ad5b-ef03-446d-8e78-7e36787f780b",
    "f4f8870e-82d3-495b-9b64-d58e508b3b2c",
    "f76cae46-069a-4334-ab8e-224e4979e58c",
)

private val retiredDomains = setOf(
    "rikka-ai.com", "aihubmix.com", "apimart.ai", "siliconflow.cn", "siliconflow.com",
    "moonshot.cn", "moonshot.ai", "tokenpony.cn", "aliyuncs.com",
    "volces.com", "bigmodel.cn", "stepfun.com", "302.ai", "tencent.com",
    "sui-xiang.com", "muteki.site", "minimaxi.com", "minimax.io", "xiaomimimo.com",
    "ackai.fun", "unifyllm.top", "unifyllm.com",
)

fun isRetiredProvider(id: String?, baseUrl: String?): Boolean {
    if (id in RETIRED_PROVIDER_IDS) return true
    val host = runCatching { URI(baseUrl.orEmpty()).host?.lowercase() }.getOrNull() ?: return false
    return retiredDomains.any { host == it || host.endsWith(".$it") }
}

/** Reorders a visible category without dropping providers hidden by tabs or search. */
fun reorderVisibleProviders(
    all: List<me.rerere.ai.provider.ProviderSetting>,
    visible: List<me.rerere.ai.provider.ProviderSetting>,
    from: Int,
    to: Int,
): List<me.rerere.ai.provider.ProviderSetting> {
    if (from !in visible.indices || to !in visible.indices) return all
    val ordered = visible.toMutableList().apply { add(to, removeAt(from)) }.iterator()
    val ids = visible.map { it.id }.toSet()
    return all.map { if (it.id in ids) ordered.next() else it }
}

val RETIRED_SPEECH_TYPES = setOf("minimax", "qwen", "mimo", "step", "fish-audio", "volcengine", "dashscope", "dashscope_streaming")

fun me.rerere.tts.provider.TTSProviderSetting.isRetiredSpeechProvider(): Boolean = when (this) {
    is me.rerere.tts.provider.TTSProviderSetting.MiniMax,
    is me.rerere.tts.provider.TTSProviderSetting.Qwen,
    is me.rerere.tts.provider.TTSProviderSetting.MiMo,
    is me.rerere.tts.provider.TTSProviderSetting.Step,
    is me.rerere.tts.provider.TTSProviderSetting.FishAudio,
    is me.rerere.tts.provider.TTSProviderSetting.Volcengine -> true
    is me.rerere.tts.provider.TTSProviderSetting.OpenAI -> isRetiredProvider(null, baseUrl)
    else -> false
}
