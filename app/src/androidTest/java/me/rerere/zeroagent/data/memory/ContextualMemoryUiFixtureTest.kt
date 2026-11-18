package me.rerere.zeroagent.data.memory

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.getCurrentChatModel
import me.rerere.zeroagent.data.datastore.getCurrentAssistant
import me.rerere.zeroagent.data.db.AppDatabase
import me.rerere.zeroagent.data.db.entity.ConversationEntity
import me.rerere.zeroagent.utils.ZeroAgentLocale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

/** Test-only seed data for real graph/drawer UI inspection; no API key or model request. */
@RunWith(AndroidJUnit4::class)
class ContextualMemoryUiFixtureTest : KoinComponent {
    private val store: SettingsStore by inject()
    private val database: AppDatabase by inject()

    @Test fun seedIsolatedDebugUi() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "app.zeroagent.debug")
        assertTrue(ZeroAgentLocale.select(context, "zh-TW"))
        store.settingsFlow.first { !it.init }
        if (store.settingsFlow.value.getCurrentChatModel() == null) {
            val model = Model(modelId = "test-only-offline", displayName = "介面測試（未連線）")
            val provider = ProviderSetting.OpenAI(name = "介面測試供應商（未連線）", apiKey = "", models = listOf(model))
            store.update { it.copy(providers = it.providers + provider, chatModelId = model.id) }
        }
        val state = store.settingsFlow.first { it.getCurrentChatModel() != null }
        database.conversationDao().insert(ConversationEntity(
            id = Uuid.random().toString(), assistantId = state.getCurrentAssistant().id.toString(),
            title = "側欄回歸測試", nodes = "[]", chatSuggestions = "[]", isPinned = false,
            createAt = System.currentTimeMillis(), updateAt = System.currentTimeMillis(), folderId = "",
        ))
        val manager = MemoryManager.get(context)
        manager.validateModel(); manager.setEnabled(true)
        try {
            manager.remember(listOf(
                UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("介面測試：我最喜歡喝無糖烏龍茶。"))),
                UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("介面測試：你偏好無糖烏龍茶。"))),
            ), "ui-fixture")
            manager.refresh()
            assertTrue(manager.state.value.records.size >= 2)
        } finally {
            manager.setEnabled(false)
        }
    }
}
