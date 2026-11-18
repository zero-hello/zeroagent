package me.rerere.zeroagent.data.productivity

import kotlinx.serialization.Serializable
import me.rerere.zeroagent.data.sync.s3.AwsSignatureV4
import me.rerere.zeroagent.data.sync.s3.S3Config
import java.net.URI
import java.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class ConversationLink(
    val id: String = Uuid.random().toString(),
    val title: String,
    val endpoint: String,
    val bucket: String,
    val region: String,
    val pathStyle: Boolean,
    val createdAt: Long,
    val expiresAt: Long,
) {
    val objectKey: String get() { Uuid.parse(id); return "zeroagent-shares/$id.html" }
}

object ConversationLinks {
    fun checkedConfig(config: S3Config): S3Config {
        val uri = URI(config.endpoint)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && uri.path.orEmpty() in listOf("", "/")) {
            "請在備份設定填入 HTTPS 的 S3 基底網址，不含路徑、查詢或帳密。"
        }
        require(Regex("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]").matches(config.bucket) &&
            config.accessKeyId.isNotBlank() && config.secretAccessKey.isNotBlank() &&
            Regex("[a-z0-9-]+").matches(config.region)) { "請先完成 S3 儲存桶、區域和金鑰設定。" }
        return config.copy(endpoint = "https://${uri.host.lowercase()}${if (uri.port < 0) "" else ":${uri.port}"}")
    }

    fun matches(config: S3Config, link: ConversationLink): Boolean =
        config.endpoint == link.endpoint && config.bucket == link.bucket &&
            config.region == link.region && config.pathStyle == link.pathStyle

    fun url(config: S3Config, link: ConversationLink, now: Instant = Instant.now()): String {
        require(matches(config, link)) { "目前 S3 設定與此連結的儲存位置不同。" }
        val remaining = link.expiresAt - now.epochSecond
        require(remaining in 1..604800) { "這個連結已到期。" }
        return AwsSignatureV4.presignGet(config, link.objectKey, remaining.toInt(), now)
    }

    fun html(title: String, text: String): ByteArray {
        fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
        return ("<!doctype html><html lang=\"zh-Hant\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<meta name=\"referrer\" content=\"no-referrer\"><meta name=\"robots\" content=\"noindex,nofollow\">" +
            "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\">" +
            "<title>${escape(title)}</title><style>body{font-family:system-ui;max-width:56rem;margin:auto;padding:24px;line-height:1.7}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit}</style>" +
            "</head><body><h1>${escape(title)}</h1><pre>${escape(text)}</pre><footer>由 ZeroAgent 匯出</footer></body></html>")
            .toByteArray(Charsets.UTF_8)
    }
}
