package me.rerere.zeroagent.data.lab

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.core.MessageRole

private val topics = listOf(
    "程式與除錯" to listOf("修復", "程式", "錯誤", "bug", "kotlin", "compose", "python", "javascript"),
    "版本與需求" to listOf("更新", "版本", "需求", "發布", "release"),
    "文件與整理" to listOf("文件", "簡報", "資料整理", "摘要", "pdf", "csv"),
    "寫作與改稿" to listOf("寫作", "改寫", "文案", "故事", "開頭", "結尾"),
    "學習與練習" to listOf("學習", "教我", "練習", "題目", "study"),
    "方案比較" to listOf("比較", "方案", "規格", "優缺點", "compare"),
    "行程規劃" to listOf("規劃", "行程", "旅遊", "安排行程", "trip"),
    "翻譯與用詞" to listOf("翻譯", "英文", "日文", "translate"),
)

/** Only predefined labels are ever copied to the sidebar. No raw chat text leaves this classifier. */
internal fun privateConversationTitle(messages: List<UIMessage>): String {
    val sample = messages.asSequence().filter { it.role == MessageRole.USER }.take(3)
        .flatMap { it.parts.filterIsInstance<UIMessagePart.Text>().asSequence() }
        .joinToString(" ") { it.text.take(600).lowercase() }
    return topics.map { (title, keywords) -> title to keywords.count { it in sample } }
        .maxByOrNull { it.second }?.takeIf { it.second > 0 }?.first ?: "對話"
}
