package me.rerere.zeroagent.ui.pages.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.ui.context.LocalNavController
import kotlin.uuid.Uuid

@Composable
internal fun PracticeWorkspace() {
    val call = rememberLabModelCall()
    val modes = listOf("先給提示", "一起拆解", "完整解答")
    var mode by remember { mutableStateOf(modes.first()) }
    var topic by remember { mutableStateOf("") }
    var attempt by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    WorkspaceField(topic, { topic = it; feedback = "" }, "想練習的內容", minLines = 2, cap = 3_000)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { item -> FilterChip(item == mode, onClick = { mode = item }, label = { Text(item) }) }
    }
    WorkspaceField(attempt, { attempt = it }, "目前的想法或作法（可留空）", minLines = 2, cap = 4_000)
    ConfirmModelAction("取得練習回覆", "會傳送練習主題、選擇的模式和你填寫的作法。",
        call != null && topic.isNotBlank()) {
        val current = Triple(topic, mode, attempt)
        val input = Json.encodeToString(mapOf("topic" to current.first, "mode" to current.second, "attempt" to current.third))
        val answer = call?.invoke("請當練習夥伴，依 mode 提供提示、一起拆解或完整解答。若有 attempt，先針對使用者寫的內容給具體回饋，再給一題短練習。不要評分，不要求揭露私人資訊。\n$input")
        if (current == Triple(topic, mode, attempt)) feedback = answer.orEmpty()
    }
    if (feedback.isNotBlank()) {
        WorkspaceResult("這一回的練習", feedback)
        Text("你可以補上新的作法，再請模型接著陪練。")
    }
}

@Serializable
internal data class RequirementQuestion(val question: String, val suggestedAssumption: String)

internal fun decodeRequirementQuestions(text: String): List<RequirementQuestion> {
    val stripped = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    return Json.decodeFromString<List<RequirementQuestion>>(stripped)
        .also { result ->
            require(result.size <= 3 && result.all { it.question.isNotBlank() && it.suggestedAssumption.isNotBlank() }) {
                "模型沒有回傳有效的提問卡，請重試或直接開始。"
            }
        }
        .map { it.copy(question = it.question.take(300), suggestedAssumption = it.suggestedAssumption.take(600)) }
}

@Composable
internal fun RequirementsWorkspace() {
    val call = rememberLabModelCall()
    val nav = LocalNavController.current
    var task by remember { mutableStateOf("") }
    var questions by remember { mutableStateOf<List<RequirementQuestion>?>(null) }
    var answers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var preview by remember { mutableStateOf("") }
    fun buildDraft(defaults: Boolean) = buildString {
        append(task.trim())
        questions.orEmpty().forEachIndexed { index, question ->
            val answer = answers[index]?.takeIf(String::isNotBlank)
            if (answer != null) append("\n\n").append(question.question).append("\n").append(answer.trim())
            else if (defaults) append("\n\n採用的假設：").append(question.suggestedAssumption)
        }
    }
    WorkspaceField(task, { task = it; questions = null; answers = emptyMap(); preview = "" }, "要完成的事", minLines = 3, cap = 6_000)
    ConfirmModelAction("檢查還需要哪些條件", "只會送出這裡提供的任務描述。", call != null && task.isNotBlank()) {
        val source = task
        val payload = Json.encodeToString(mapOf("task" to source))
        val text = call?.invoke("判斷 task 能否直接開始。只有缺少會改變結果的條件才回傳至多三小題。只回傳 JSON 陣列，每項為 {\"question\":\"問題\",\"suggestedAssumption\":\"合理預設\"}。條件充足時回傳 []。不可直接執行任務。\n$payload").orEmpty()
        if (source == task) {
            questions = decodeRequirementQuestions(text)
            answers = emptyMap()
            preview = ""
        }
    }
    val identified = questions
    if (identified != null) {
        if (identified.isEmpty()) Text("目前可直接開始。")
        identified.forEachIndexed { index, question ->
            WorkspaceField(answers[index].orEmpty(), { answers = answers + (index to it); preview = "" }, question.question, cap = 1_000)
            Text("建議假設：${question.suggestedAssumption}")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = identified.indices.all { !answers[it].isNullOrBlank() }, onClick = { preview = buildDraft(false) }) { Text("預覽任務草稿") }
            if (identified.isNotEmpty()) TextButton(onClick = { preview = buildDraft(true) }) { Text("先照建議做") }
        }
    } else TextButton(enabled = task.isNotBlank(), onClick = { preview = buildDraft(false) }) { Text("直接預覽草稿") }
    if (preview.isNotBlank()) {
        WorkspaceField(preview, { preview = it }, "任務草稿", minLines = 3, cap = 16_000)
        TextButton(onClick = { nav.navigate(Screen.Chat(Uuid.random().toString(), text = preview)) }) { Text("帶到新對話") }
    }
}
