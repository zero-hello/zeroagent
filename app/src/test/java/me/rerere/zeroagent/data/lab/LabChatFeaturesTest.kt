package me.rerere.zeroagent.data.lab

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.ui.pages.lab.RequirementQuestion
import me.rerere.zeroagent.ui.pages.lab.decodeRequirementQuestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LabChatFeaturesTest {
    @Test
    fun replyReviewLimitsItselfToComplexTextRequests() {
        assertFalse(shouldReviewLabReply(UIMessage.user("晚安，明天聊。")))
        assertTrue(shouldReviewLabReply(UIMessage.user("請計算購買三件商品打八折之後的總價。")))
        assertTrue(shouldReviewLabReply(UIMessage.user("請整理文件定義與交付方式。".repeat(20))))
        val withImage = UIMessage.user("請比較這張圖的兩種排版方式並說明優缺點。")
        assertFalse(shouldReviewLabReply(withImage.copy(parts = withImage.parts + UIMessagePart.Image("content://local/image"))))
    }

    @Test
    fun replyContextRetainsOnlyRecentSnapshotsAndHidesAttachmentPathFromKey() {
        ReplyContextStore.clear()
        try {
            val chat = Uuid.random()
            val firstReply = Uuid.random()
            ReplyContextStore.record(chat, firstReply, listOf(ReplyContextEntry("memory:1", "記憶", "保留資訊")))
            assertEquals("保留資訊", ReplyContextStore.get(firstReply)?.entries?.single()?.text)
            repeat(26) { ReplyContextStore.record(chat, Uuid.random(), emptyList()) }
            assertNull(ReplyContextStore.get(firstReply))
            val sensitive = "content://local/path/secret-name"
            val image = UIMessagePart.Image(sensitive)
            val first = UIMessage.user("圖一")
            val second = UIMessage.user("圖二")
            assertFalse(replyAttachmentKey(first, image).orEmpty().contains("secret-name"))
            assertNotEquals(replyAttachmentKey(first, image), replyAttachmentKey(second, image))
        } finally {
            ReplyContextStore.clear()
        }
    }

    @Test
    fun requirementsQuestionsCanBeEmptyAndRejectExcessOrMissingAnswers() {
        assertTrue(decodeRequirementQuestions("[]").isEmpty())
        assertEquals(listOf(RequirementQuestion("給誰讀？", "一般讀者")), decodeRequirementQuestions(
            "```json\n[{\"question\":\"給誰讀？\",\"suggestedAssumption\":\"一般讀者\"}]\n```",
        ))
        assertThrows(IllegalArgumentException::class.java) {
            decodeRequirementQuestions("""[{"question":"給誰讀？","suggestedAssumption":""}]""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            val question = """{"question":"用途？","suggestedAssumption":"內部討論"}"""
            decodeRequirementQuestions(List(4) { question }.joinToString(prefix = "[", postfix = "]"))
        }
    }
}
