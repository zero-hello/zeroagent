package me.rerere.zeroagent.data.ai

import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatModelPreflightTest {
    @Test fun unsupportedImageDependsOnModelAndUsableOcr() {
        val localImage = UIMessagePart.Image("file:///fixture.png")
        val parts = listOf(localImage, UIMessagePart.Image("https://example.invalid/photo.png"))
        assertEquals(listOf(Modality.IMAGE), ChatModelPreflight.unsupportedMedia(Model(), parts, false))
        assertEquals(listOf(Modality.IMAGE), ChatModelPreflight.unsupportedMedia(Model(), parts, true))
        assertEquals(emptyList<Modality>(), ChatModelPreflight.unsupportedMedia(Model(), listOf(localImage), true))
        val vision = Model(inputModalities = listOf(Modality.TEXT, Modality.IMAGE))
        assertEquals(emptyList<Modality>(), ChatModelPreflight.unsupportedMedia(vision, parts, false))
    }

    @Test fun onlyKnownContextLengthCanRejectAnOversizeDraft() {
        val parts = listOf(UIMessagePart.Text("a ".repeat(256)))
        assertNull(ChatModelPreflight.fitsDraft(Model(), parts))
        assertEquals(false, ChatModelPreflight.fitsDraft(Model(contextLength = 16), parts))
        assertEquals(true, ChatModelPreflight.fitsDraft(Model(contextLength = 4096), parts))
    }
}