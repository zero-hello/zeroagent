package me.rerere.zeroagent.data.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalMemoryCaptureTest {
    @Test fun capturesIndependentDurablePreferences() {
        assertEquals(
            listOf("我偏好繁體中文回覆", "我平常使用 Kotlin 開發 Android", "I prefer short answers"),
            PersonalMemoryCapture.extract("我偏好繁體中文回覆。我平常使用 Kotlin 開發 Android。\nI prefer short answers."),
        )
    }

    @Test fun capturesWorkflowsProfessionalBackgroundAndExplicitLongTermFacts() {
        val input = "我的固定工作流程是先列出驗收條件。我長期從事安卓開發。請記住：正式版要保留兩種架構。"
        val facts = PersonalMemoryCapture.extract(input)
        assertEquals(
            listOf("我的固定工作流程是先列出驗收條件", "我長期從事安卓開發", "請記住：正式版要保留兩種架構"),
            facts,
        )
        assertEquals(
            listOf(PersonalMemoryCapture.Category.WORKFLOW, PersonalMemoryCapture.Category.PROFILE, PersonalMemoryCapture.Category.IMPORTANT),
            facts.map(PersonalMemoryCapture::category),
        )
    }

    @Test fun filtersSensitiveOrTemporaryFactsEvenWhenExplicitlyRequested() {
        assertEquals(
            emptyList<String>(),
            PersonalMemoryCapture.extract("請記住：我的 API key 是假的。\n請記住：今天先不做。\nRemember that my password is not-real."),
        )
    }

    @Test fun ignoresTransientQuotedAndSensitiveStatements() {
        assertEquals(
            emptyList<String>(),
            PersonalMemoryCapture.extract("我今天想喝可樂。\n我偏好今天先用短句。\n「我偏好英文回覆」\n我習慣用密碼 test-only\n我喜歡這個嗎？"),
        )
    }
}
