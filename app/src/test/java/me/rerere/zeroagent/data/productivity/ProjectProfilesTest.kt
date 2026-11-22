package me.rerere.zeroagent.data.productivity

import kotlinx.serialization.json.Json
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.data.model.Conversation
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class ProjectProfilesTest {
    private val fallback = Assistant(systemPrompt = "model instructions", enableMemory = true,
        useGlobalMemory = true, enableRecentChatsReference = true, coldMemoryEnabled = true,
        workspaceId = Uuid.random(), allowConversationSystemPrompt = true)
    private val conversation = Conversation(assistantId = fallback.id, messageNodes = emptyList(),
        customSystemPrompt = "conversation instructions")

    @Test fun `project scope is independent from the selected model and global memory`() {
        val project = ChatProject(name = "工作", instructions = "project instructions")
        val profile = fallback.copy(id = Uuid.parse(project.id), enableMemory = true)
        val resolved = ProjectProfiles.resolve(project, conversation, fallback, profile, false)
        assertEquals(profile.id, resolved.id)
        assertTrue(resolved.enableMemory)
        assertFalse(resolved.useGlobalMemory)
        assertFalse(resolved.enableRecentChatsReference)
        assertFalse(resolved.coldMemoryEnabled)
        assertNull(resolved.workspaceId)
        assertFalse(resolved.allowConversationSystemPrompt)
        assertTrue(resolved.systemPrompt.contains("conversation instructions"))
        assertTrue(resolved.systemPrompt.contains("project instructions"))
        assertEquals(profile.id, ProjectProfiles.resolve(project, conversation, fallback.copy(id = Uuid.random()), profile, false).id)
    }

    @Test fun `conversation privacy disables every automatic long term memory source`() {
        for (project in listOf(null, ChatProject(name = "私人"))) {
            val resolved = ProjectProfiles.resolve(project, conversation, fallback, fallback, true)
            assertFalse(resolved.enableMemory)
            assertFalse(resolved.useGlobalMemory)
            assertFalse(resolved.enableRecentChatsReference)
            assertFalse(resolved.coldMemoryEnabled)
        }
    }

    @Test fun `revoked project memory is not reenabled by another model profile`() {
        val project = ChatProject(name = "工作")
        assertFalse(ProjectProfiles.resolve(project, conversation, fallback, fallback.copy(enableMemory = false), false).enableMemory)
        assertFalse(ProjectProfiles.resolve(project, conversation, fallback, null, false).enableMemory)
    }

    @Test fun `backup keeps project documents tags exclusions and privacy together`() {
        val original = ProjectState(projects = listOf(ChatProject(name = "工作", documents = listOf(
            KnowledgeDocument(name = "file.txt", text = "actual contents", included = false)))),
            disabledMemory = setOf("conversation-a"), tags = mapOf("conversation-a" to setOf("工作", "待辦")))
        assertEquals(original, Json.decodeFromString<ProjectState>(Json.encodeToString(original)))
    }
}
