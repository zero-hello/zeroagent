package me.rerere.zeroagent.data.chat

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ChatDraftStoreTest {
    @Test
    fun unsentMessageCanBeRestoredWithoutOverwritingTheComposerDraft() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val conversation = Uuid.random()
        val draft = ComposerDraft("另寫一則訊息", emptyList())
        val unsent = UnsentChatMessage(
            id = Uuid.random().toString(),
            parts = listOf(UIMessagePart.Text("原文"), UIMessagePart.Document("file:/example.txt", "example.txt", "text/plain")),
            answer = true,
        )
        val first = ChatDraftStore(context)
        try {
            first.write(conversation, draft)
            assertTrue(first.writeUnsent(conversation, unsent, durable = true))
            val reopened = ChatDraftStore(context)
            assertEquals(draft, reopened.read(conversation))
            assertEquals(listOf(unsent), reopened.readUnsent(conversation))
            assertTrue(reopened.removeUnsent(conversation, Uuid.parse(unsent.id), durable = true))
            assertTrue(reopened.readUnsent(conversation).isEmpty())
            assertEquals(draft, reopened.read(conversation))
        } finally {
            first.remove(conversation)
            first.clearUnsent(conversation)
        }
    }

    @Test
    fun legacyEntryRemainsFirstAndRemovingOneEntryPreservesTheOthers() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val conversation = Uuid.random()
        val firstId = Uuid.random()
        val secondId = Uuid.random()
        val first = UnsentChatMessage(firstId.toString(), listOf(UIMessagePart.Text("第一則")), true)
        val second = UnsentChatMessage(secondId.toString(), listOf(UIMessagePart.Text("第二則")), true)
        val edited = first.copy(parts = listOf(UIMessagePart.Text("第一則已編輯")))
        val preferences = context.getSharedPreferences("conversation_drafts", android.content.Context.MODE_PRIVATE)
        val store = ChatDraftStore(context)
        try {
            assertTrue(preferences.edit().putString("unsent:$conversation", Json.encodeToString(first)).commit())
            assertEquals(listOf(first), store.readUnsent(conversation))
            assertTrue(store.writeUnsent(conversation, second, durable = true))
            assertTrue(store.writeUnsent(conversation, edited, durable = true))
            assertEquals(listOf(edited, second), ChatDraftStore(context).readUnsent(conversation))
            assertTrue(store.removeUnsent(conversation, firstId, durable = true))
            assertEquals(listOf(second), ChatDraftStore(context).readUnsent(conversation))
        } finally {
            store.clearUnsent(conversation)
        }
    }

    @Test
    fun onlyNonEmptyOutgoingQueuesAreDiscoverableAfterReopening() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val blankChat = Uuid.random()
        val pendingChat = Uuid.random()
        val unsent = UnsentChatMessage(Uuid.random().toString(), listOf(UIMessagePart.Text("尚未送出")), true)
        val store = ChatDraftStore(context)
        try {
            store.write(blankChat, ComposerDraft("只寫了草稿", emptyList()))
            assertTrue(store.writeUnsent(pendingChat, unsent, durable = true))
            val reopened = ChatDraftStore(context)
            val discoverable = reopened.observeUnsentConversationIds().first()
            assertTrue(pendingChat in discoverable)
            assertTrue(blankChat !in discoverable)
            reopened.clearUnsent(pendingChat)
            assertTrue(pendingChat !in reopened.observeUnsentConversationIds().first())
        } finally {
            store.remove(blankChat)
            store.clearUnsent(pendingChat)
        }
    }
}
