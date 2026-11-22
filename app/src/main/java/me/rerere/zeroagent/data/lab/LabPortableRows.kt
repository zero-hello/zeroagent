package me.rerere.zeroagent.data.lab

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
internal data class AcceptanceRow(
    val id: String = Uuid.random().toString(),
    val requirement: String,
    val status: String = "尚未驗證",
    val evidence: String = "",
    val sourceUrl: String = "",
    val linksArtifact: Boolean = false,
    val artifactDigest: String? = null,
) {
    fun needsArtifactReview(currentDigest: String?): Boolean = linksArtifact && status != "尚未驗證" &&
        (artifactDigest == null || artifactDigest != currentDigest)

    fun effectiveStatus(currentDigest: String?): String =
        if (needsArtifactReview(currentDigest)) "尚未驗證" else status
}

internal fun artifactEvidenceDigest(content: String): String = MessageDigest.getInstance("SHA-256")
    .digest(content.encodeToByteArray()).joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

@Serializable
internal data class BatchRow(
    val input: String,
    val answer: String? = null,
    val failed: Boolean = false,
    val id: String = Uuid.random().toString(),
    val attempts: Int = 0,
)
