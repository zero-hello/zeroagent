package me.rerere.zeroagent.data.lab

import me.rerere.zeroagent.data.ai.net.GuardedDns
import me.rerere.zeroagent.data.ai.net.isBlockedTarget
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal const val PREVIEW_ASSET_LIMIT = 384 * 1024
private val PUBLIC_HOST = Regex("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z][a-z0-9-]{0,62}")
private val ASSET_PLACEHOLDER = Regex("\\{\\{asset([1-3])}}")

internal fun previewAssetUrl(value: String): HttpUrl? {
    if (value.any { it.isWhitespace() || it.isISOControl() || it == '\\' }) return null
    return value.toHttpUrlOrNull()?.takeIf {
        it.isHttps && it.port == 443 && it.username.isEmpty() && it.password.isEmpty() &&
            it.query == null && it.fragment == null && PUBLIC_HOST.matches(it.host) &&
            it.host.substringAfterLast('.') !in setOf("localhost", "local", "internal", "onion", "invalid", "test", "example")
    }
}

// GuardedDns rejects private/local answers. Also reject special-purpose and tunnel prefixes here.
internal fun isPublicPreviewAddress(address: InetAddress): Boolean {
    if (address.isBlockedTarget()) return false
    val bytes = address.address.map { it.toInt() and 255 }
    val (first, second, third) = bytes
    return when (address) {
        is Inet4Address -> first in 1..223 && !(first == 192 && second == 0) &&
            !(first == 192 && second == 88 && third == 99) &&
            !(first == 198 && (second in 18..19 || second == 51 && third == 100)) &&
            !(first == 203 && second == 0 && third == 113)
        is Inet6Address -> first in 0x20..0x3f && !(first == 0x20 && second == 0x02) &&
            !(first == 0x20 && second == 0x01 && (third <= 1 || third == 0x0d && bytes[3] == 0xb8)) &&
            !(first == 0x3f && second == 0xff && third < 0x10)
        else -> false
    }
}

private val previewClient by lazy {
    val guard = GuardedDns()
    OkHttpClient.Builder()
        .dns(Dns { host ->
            guard.lookup(host).also { addresses ->
                if (addresses.isEmpty() || addresses.any { !isPublicPreviewAddress(it) }) {
                    throw UnknownHostException("預覽圖片只能來自公開網路。")
                }
            }
        })
        .proxy(Proxy.NO_PROXY)
        .protocols(listOf(Protocol.HTTP_1_1))
        .cookieJar(CookieJar.NO_COOKIES)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
}

internal data class PreviewAsset(val mime: String, val bytes: ByteArray) {
    fun dataUrl(): String = "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}"
}

internal fun previewImage(bytes: ByteArray, mime: String?): PreviewAsset? {
    if (bytes.size !in 12..PREVIEW_ASSET_LIMIT) return null
    fun startsWith(vararg prefix: Int) = prefix.indices.all { bytes[it].toInt() and 255 == prefix[it] }
    val valid = when (mime) {
        "image/png" -> startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        "image/jpeg" -> startsWith(0xff, 0xd8, 0xff)
        "image/webp" -> startsWith(0x52, 0x49, 0x46, 0x46) &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        else -> false
    }
    return if (valid && mime != null) PreviewAsset(mime, bytes) else null
}

internal fun applyPreviewAssets(value: String, assetUrls: List<String>): String =
    ASSET_PLACEHOLDER.replace(value) { match ->
        assetUrls.getOrNull(match.groupValues[1].toInt() - 1) ?: match.value
    }

/** Only call for URLs explicitly accepted by the user in the preview confirmation. */
internal suspend fun fetchApprovedPreviewAsset(target: HttpUrl): PreviewAsset = suspendCancellableCoroutine { continuation ->
    require(previewAssetUrl(target.toString()) == target)
    val request = Request.Builder().url(target).header("Accept", "image/png,image/jpeg,image/webp").get().build()
    val call = previewClient.newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(IOException("無法連線、網址不公開或下載逾時。"))
        }

        override fun onResponse(call: Call, response: Response) {
            val asset = try {
                response.use {
                    if (it.code != 200) throw IOException("下載失敗（HTTP ${it.code}）。不會跟隨轉址。")
                    val body = it.body
                    val mime = body.contentType()?.let { type -> "${type.type}/${type.subtype}" }
                    if (mime !in setOf("image/png", "image/jpeg", "image/webp")) {
                        throw IOException("只支援 PNG、JPEG 或 WebP 圖片。")
                    }
                    if (body.contentLength() > PREVIEW_ASSET_LIMIT) throw IOException("單張圖片超過 384 KiB。")
                    val source = body.source()
                    if (source.request(PREVIEW_ASSET_LIMIT + 1L)) throw IOException("單張圖片超過 384 KiB。")
                    previewImage(source.readByteArray(), mime) ?: throw IOException("圖片內容與檔案格式不符。")
                }
            } catch (error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
                return
            }
            if (continuation.isActive) continuation.resume(asset)
        }
    })
}
