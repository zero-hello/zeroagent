package me.rerere.zeroagent.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcceptanceRowTest {
    @Test
    fun artifactEditRequiresNewVerificationAndPreservesEvidence() {
        val original = artifactEvidenceDigest("第一版")
        val row = AcceptanceRow(requirement = "Check export", status = "實測通過",
            evidence = "Exported the first version", linksArtifact = true, artifactDigest = original)
        assertEquals("實測通過", row.effectiveStatus(original))
        assertEquals("尚未驗證", row.effectiveStatus(artifactEvidenceDigest("第二版")))
        assertTrue(row.needsArtifactReview(null))
        assertEquals("Exported the first version", row.evidence)
    }

    @Test
    fun legacyArtifactEvidenceNeedsReviewButUnrelatedEvidenceRemainsValid() {
        val current = artifactEvidenceDigest("成品")
        val row = AcceptanceRow(requirement = "Check notification", status = "內容已核對")
        assertFalse(row.needsArtifactReview(current))
        assertEquals("尚未驗證", row.copy(linksArtifact = true).effectiveStatus(current))
    }
}
