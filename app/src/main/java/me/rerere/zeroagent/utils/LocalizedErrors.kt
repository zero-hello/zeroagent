package me.rerere.zeroagent.utils

import android.content.Context
import me.rerere.ai.util.HttpException
import me.rerere.zeroagent.R
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException

internal enum class UserErrorKind { UNKNOWN, NETWORK, TIMEOUT, AUTH, RATE, QUOTA, SERVICE, MODEL, CONTEXT, PERMISSION, CANCELLED, STORAGE, MEMORY, AUDIO, CORRUPT, UNSUPPORTED, REQUEST, REASONING, FORMAT, CONFIG }

/** The original exception remains available for diagnostics; UI copy never needs a paid translator. */
internal fun classifyUserError(error: Throwable? = null, raw: String? = null): UserErrorKind {
    val chain = generateSequence(error) { it.cause }.take(8).toList()
    val text = (raw.orEmpty() + " " + chain.joinToString(" ") { it.javaClass.simpleName + " " + it.message.orEmpty() + " " + ((it as? HttpException)?.errorCode.orEmpty()) }).take(8000).lowercase()
    val code = chain.filterIsInstance<HttpException>().firstOrNull()?.statusCode
        ?: Regex("http\\s+(\\d{3})").find(text)?.groupValues?.get(1)?.toIntOrNull()
    fun has(vararg values: String) = values.any { it in text }
    return when {
        chain.any { it is CancellationException } || has("cancellationexception", "cancelled", "canceled") -> UserErrorKind.CANCELLED
        chain.any { it is SecurityException } -> UserErrorKind.PERMISSION
        has("insufficient_quota", "quota exceeded", "quota_exceeded", "billing", "credit balance", "餘額不足", "余额不足") -> UserErrorKind.QUOTA
        code == 401 || code == 403 || has("unauthorized", "invalid_api_key", "api_key_invalid", "api_key_expired", "authentication", "invalid api key", "api key not valid", "api key expired", "http 401", "http 403") -> UserErrorKind.AUTH
        has("permission denied", "permissiondenied", "permission required", "not granted", "approval required") -> UserErrorKind.PERMISSION
        code == 429 || has("rate limit", "rate_limit", "too many requests", "http 429") -> UserErrorKind.RATE
        chain.any { it is SocketTimeoutException } || has("timed out", "timeout", "逾時", "超时") -> UserErrorKind.TIMEOUT
        has("no space left", "disk full", "insufficient storage", "空間不足", "空间不足") -> UserErrorKind.STORAGE
        chain.any { it is OutOfMemoryError } || has("outofmemory", "out of memory", "not enough memory") -> UserErrorKind.MEMORY
        has("context_length", "context length", "token ids are too long", "maximum context", "too many tokens") -> UserErrorKind.CONTEXT
        has("corrupt", "bad magic", "invalid model file", "truncated model") -> UserErrorKind.CORRUPT
        has("audio focus", "audiorecord", "microphone", "audio_error", "record_audio") -> UserErrorKind.AUDIO
        has("reasoning", "thinking") && has("unsupported", "not support", "invalid", "budget") -> UserErrorKind.REASONING
        code == 404 || has("model_not_found", "model not found", "no model loaded", "model is not loaded") -> UserErrorKind.MODEL
        code != null && code >= 500 || has("service unavailable", "http 502", "http 503") -> UserErrorKind.SERVICE
        chain.any { it is IOException } || has("connection", "network", "unreachable", "unknownhost", "sslhandshake") -> UserErrorKind.NETWORK
        has("not configured", "notconfigured", "no provider", "missing_key", "missing_model", "invalid_endpoint", "secure_endpoint_required") -> UserErrorKind.CONFIG
        chain.any { it is kotlinx.serialization.SerializationException || it is NumberFormatException || it is java.util.regex.PatternSyntaxException } || has("invalid json", "unexpected json", "malformed json", "invalid_response", "model_catalog_page_limit", "catalog_protocol_mismatch", "invalid_termux_result") -> UserErrorKind.FORMAT
        has("unsupported", "not supported", "unimplemented") -> UserErrorKind.UNSUPPORTED
        code == 400 || has("invalid request", "invalid_argument", "bad request") -> UserErrorKind.REQUEST
        else -> UserErrorKind.UNKNOWN
    }
}

fun Context.localizedErrorMessage(error: Throwable? = null, raw: String? = null): String = zeroAgentLocalizedContext().getString(when (classifyUserError(error, raw)) {
    UserErrorKind.CONFIG -> R.string.zeroagent7_error_config
    UserErrorKind.FORMAT -> R.string.zeroagent7_error_format
    UserErrorKind.UNKNOWN -> R.string.zeroagent7_error_unknown
    UserErrorKind.NETWORK -> R.string.zeroagent7_error_network
    UserErrorKind.TIMEOUT -> R.string.zeroagent7_error_timeout
    UserErrorKind.AUTH -> R.string.zeroagent7_error_auth
    UserErrorKind.RATE -> R.string.zeroagent7_error_rate
    UserErrorKind.QUOTA -> R.string.zeroagent7_error_quota
    UserErrorKind.SERVICE -> R.string.zeroagent7_error_service
    UserErrorKind.MODEL -> R.string.zeroagent7_error_model
    UserErrorKind.CONTEXT -> R.string.zeroagent7_error_context
    UserErrorKind.PERMISSION -> R.string.zeroagent7_error_permission
    UserErrorKind.CANCELLED -> R.string.zeroagent7_error_cancelled
    UserErrorKind.STORAGE -> R.string.zeroagent7_error_storage
    UserErrorKind.MEMORY -> R.string.zeroagent7_error_memory
    UserErrorKind.AUDIO -> R.string.zeroagent7_error_audio
    UserErrorKind.CORRUPT -> R.string.zeroagent7_error_corrupt
    UserErrorKind.UNSUPPORTED -> R.string.zeroagent7_error_unsupported
    UserErrorKind.REQUEST -> R.string.zeroagent7_error_request
    UserErrorKind.REASONING -> R.string.zeroagent7_error_reasoning
})
