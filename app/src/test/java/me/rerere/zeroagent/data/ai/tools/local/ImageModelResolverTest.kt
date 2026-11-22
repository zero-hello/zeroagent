package me.rerere.zeroagent.data.ai.tools.local

import me.rerere.ai.provider.*
import me.rerere.zeroagent.data.datastore.Settings
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class ImageModelResolverTest {
    private val chat = Model(modelId = "chat", outputModalities = listOf(Modality.TEXT))
    private val imageA = Model(modelId = "image-a", type = ModelType.IMAGE, outputModalities = listOf(Modality.IMAGE))
    private val imageB = Model(modelId = "image-b", type = ModelType.IMAGE, outputModalities = listOf(Modality.IMAGE))
    private fun settings(enabled: Boolean = true, extra: Boolean = false) = Settings(
        chatModelId = chat.id, imageGenerationModelId = imageB.id,
        providers = listOf(ProviderSetting.OpenAI(name = "Current", enabled = enabled,
            models = listOf(chat, imageA) + if (extra) listOf(imageA.copy(id = Uuid.random(), modelId = "image-extra")) else emptyList()),
            ProviderSetting.OpenAI(name = "Other", models = listOf(imageB))))

    @Test fun oldGlobalSelectionCannotSpendQuotaOnAnotherProvider() {
        assertEquals(imageA.id, settings().resolveImageModel(chat.id.toString())?.id)
        assertNull(settings().resolveImageModel(chat.id.toString(), requested = imageB.modelId))
    }
    @Test fun removedOrDisabledConversationProviderDoesNotFallBackToAnotherAccount() {
        assertNull(settings(false).resolveImageModel(chat.id.toString()))
        assertNull(settings().resolveImageModel(Uuid.random().toString()))
    }
    @Test fun ambiguousCurrentProviderRequiresAnExplicitChoice() {
        assertNull(settings(extra = true).resolveImageModel(chat.id.toString()))
        assertEquals(imageA.id, settings(extra = true).resolveImageModel(chat.id.toString(), requested = "image-a")?.id)
    }
    @Test fun editingIsNotAdvertisedForTextOnlyImageEndpoints() {
        assertNull(settings().resolveImageModel(chat.id.toString(), editing = true))
    }
}
