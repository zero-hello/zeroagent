package me.rerere.zeroagent.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.nio.ByteBuffer
import java.util.Base64

/** Transport contains a single, versioned, allowlisted template. */
@Serializable
internal data class PortableTemplate(
    val kind: Kind,
    val name: String,
    val format: Format? = null,
    val endpoint: String? = null,
    val requestPath: String? = null,
    val models: List<String> = emptyList(),
    val prompt: String? = null,
) {
    enum class Kind { PROVIDER, MCP, PROMPT }
    enum class Format {
        OPENAI_CHAT, OPENAI_RESPONSES, COHERE_V2, GOOGLE, ANTHROPIC, MCP_SSE, MCP_HTTP,
    }
}

internal object PortableTemplateCodec {
    private const val PREFIX = "ZA1:"
    const val MAX_QR_LENGTH = 1_900
    private val json = Json { ignoreUnknownKeys = false }
    private val safePath = Regex("(?:/[A-Za-z0-9._~\\-]+)*/?")
    private val modelId = Regex("[A-Za-z0-9._:/@+\\-]{1,160}")
    private val credentialMarker = Regex(
        "(?i)(-----BEGIN [A-Z ]*PRIVATE KEY-----|\\bBearer\\s+\\S+|" +
            "\\b(?:sk-[A-Za-z0-9_-]{8,}|AIza[A-Za-z0-9_-]{18,}|gh[pousr]_[A-Za-z0-9_]{12,}|" +
            "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)|" +
            "\\b(?:api[-_ ]?key|access[-_ ]?token|refresh[-_ ]?token|client[-_ ]?secret|password)\\s*[:=]\\s*['\"]?\\S+)",
    )
    private val longOpaqueSegment = Regex("[A-Za-z0-9_\\-]{24,}")
    private val providerFormats = setOf(
        PortableTemplate.Format.OPENAI_CHAT, PortableTemplate.Format.OPENAI_RESPONSES,
        PortableTemplate.Format.COHERE_V2, PortableTemplate.Format.GOOGLE, PortableTemplate.Format.ANTHROPIC,
    )

    fun encode(template: PortableTemplate, knownSecrets: Collection<String> = emptyList()): String {
        validate(template)
        val raw = json.encodeToString(PortableTemplate.serializer(), template)
        val fields = listOfNotNull(template.name, template.endpoint, template.requestPath, template.prompt) + template.models
        require(knownSecrets.none { secret -> secret.isNotBlank() && fields.any { it.contains(secret) } })
        val code = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))
        require(code.length <= MAX_QR_LENGTH)
        return code
    }

    fun decode(code: String): PortableTemplate {
        require(code.length <= MAX_QR_LENGTH && code.startsWith(PREFIX))
        val bytes = Base64.getUrlDecoder().decode(code.substring(PREFIX.length))
        val raw = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        val template = json.decodeFromString(PortableTemplate.serializer(), raw)
        validate(template)
        return template
    }

    private fun validate(template: PortableTemplate) = with(template) {
        require(name.isNotBlank() && name.length <= 80 && name.none { it.isISOControl() || it.isDirectionalControl() })
        val fields = listOfNotNull(name, endpoint, requestPath, prompt) + models
        require(fields.none(credentialMarker::containsMatchIn))
        when (kind) {
            PortableTemplate.Kind.PROVIDER -> {
                require(format in providerFormats && prompt == null)
                requireSafeEndpoint(requireNotNull(endpoint))
                val usesPath = format in setOf(PortableTemplate.Format.OPENAI_CHAT, PortableTemplate.Format.OPENAI_RESPONSES)
                require((requestPath != null) == usesPath)
                requestPath?.let { require(it.startsWith('/')); requireSafePath(it) }
                require(models.size <= 32 && models.all(modelId::matches))
            }
            PortableTemplate.Kind.MCP -> {
                require(format == PortableTemplate.Format.MCP_SSE || format == PortableTemplate.Format.MCP_HTTP)
                require(prompt == null && requestPath == null && models.isEmpty())
                requireSafeEndpoint(requireNotNull(endpoint))
            }
            PortableTemplate.Kind.PROMPT -> {
                require(format == null && endpoint == null && requestPath == null && models.isEmpty())
                require(!prompt.isNullOrBlank() && prompt.length <= 1_200)
                require(prompt.none { (it.isISOControl() && it !in "\r\n\t") || it.isDirectionalControl() })
            }
        }
    }

    private fun requireSafeEndpoint(value: String) {
        require(value.length <= 240)
        val uri = URI(value)
        val host = requireNotNull(uri.host)
        val loopback = host.lowercase() in setOf("localhost", "127.0.0.1", "[::1]")
        require(uri.scheme == "https" || uri.scheme == "http" && loopback)
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(uri.port == -1 || uri.port in 1..65_535)
        requireSafePath(uri.rawPath.orEmpty())
    }

    private fun requireSafePath(value: String) {
        require(value.length <= 160 && safePath.matches(value))
        require(value.split('/').none {
            it == "." || it == ".." ||
                (longOpaqueSegment.matches(it) && it.any(Char::isDigit) && it.any(Char::isLetter))
        })
    }

    private fun Char.isDirectionalControl() = this in '\u202a'..'\u202e' || this in '\u2066'..'\u2069'
}
