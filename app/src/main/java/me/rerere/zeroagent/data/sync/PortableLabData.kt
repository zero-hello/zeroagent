package me.rerere.zeroagent.data.sync

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.zeroagent.data.lab.AcceptanceRow
import me.rerere.zeroagent.data.lab.BatchRow
import me.rerere.zeroagent.data.lab.LabArtifact
import me.rerere.zeroagent.data.lab.LabPreferences
import me.rerere.zeroagent.data.lab.LabRecipe
import me.rerere.zeroagent.data.lab.LocalTodo
import me.rerere.zeroagent.data.lab.TODO_KEY
import me.rerere.zeroagent.data.lab.localTodos
import me.rerere.zeroagent.data.productivity.ChatProject
import me.rerere.zeroagent.data.productivity.ProjectStore
import kotlin.uuid.Uuid

internal enum class PortableLabSection(val toggle: String, val label: String) {
    RECIPES("recipes", "常用任務配方"),
    ARTIFACT("artifact", "成品與版本紀錄"),
    ACCEPTANCE("acceptance", "需求驗收表"),
    BATCH("batch", "批次小幫手的輸入與結果"),
    PROJECTS("project_desk", "專案工作桌及參考資料"),
    DRAFTS("draft_rescue", "所選聊天室的草稿與附件"),
    TODOS("todo_capture", "已確認待辦"),
    RELAY("handoff", "所選聊天室的接力摘要"),
    MEMORY("memory_drawer", "記憶抽屜與記憶偏好"),
    ;

    fun available(context: Context): Boolean = LabPreferences.enabled(context, toggle)
}

@Serializable
internal data class PortableLabData(
    val recipes: List<LabRecipe>? = null,
    val artifact: LabArtifact? = null,
    val moodReport: String? = null,
    val acceptance: List<AcceptanceRow>? = null,
    val batch: List<BatchRow>? = null,
    val batchInstruction: String? = null,
    val projects: List<ChatProject>? = null,
    val draftTexts: Map<String, String>? = null,
    val confirmedTodos: List<LocalTodo>? = null,
    val relays: Map<String, PortableRelay>? = null,
    val memory: PortableMemory? = null,
    val drafts: Map<String, PortableDraft>? = null,
) {
    val labels: List<String>
        get() = listOfNotNull(
            recipes?.let { "${it.size} 個任務配方" },
            artifact?.let { "成品與版本紀錄" },
            acceptance?.let { "${it.size} 項需求驗收" },
            batch?.let { "${it.size} 筆批次資料" },
            projects?.let { "${it.size} 個專案工作桌" },
            draftTexts?.let { "${it.size} 則文字草稿" },
            drafts?.let { "${it.size} 則草稿" },
            confirmedTodos?.let { "${it.size} 項已確認待辦" },
            relays?.let { "${it.size} 份接力摘要" },
            memory?.let { "${it.count} 筆記憶及記憶偏好" },
        )

    fun validate(conversationIds: Set<String>) {
        require(labels.isNotEmpty() || moodReport != null) { "沒有可用的功能資料。" }
        memory?.validate()
        confirmedTodos?.let { todos ->
            require(todos.size in 1..250 && todos.map(LocalTodo::id).distinct().size == todos.size &&
                todos.all { it.title.isNotBlank() && it.title.length <= 500 && runCatching { Uuid.parse(it.id) }.isSuccess }) {
                "待辦數量、內容或識別碼不符。"
            }
        }
        relays?.let { summaries ->
            require(summaries.isNotEmpty() && summaries.keys.all { it in conversationIds }) { "接力摘要必須屬於所選聊天室。" }
        }
        recipes?.let { entries ->
            require(entries.size <= 200 && entries.all { it.name.isNotBlank() && it.name.length <= 200 && it.template.length <= 30_000 }) {
                "任務配方的數量或長度過大。"
            }
        }
        artifact?.let {
            require(it.content.length <= 200_000 && it.history.size <= 10 && it.history.all { text -> text.length <= 200_000 }) {
                "成品或版本紀錄超出大小限制。"
            }
        }
        acceptance?.let { rows ->
            require(rows.size <= 1_000 && rows.all { row ->
                row.id.length in 1..100 && row.requirement.length in 1..5_000 && row.evidence.length <= 20_000 &&
                    row.status in setOf("尚未驗證", "內容已核對", "實測通過") &&
                    (row.artifactDigest == null || row.artifactDigest.matches(Regex("[a-f0-9]{64}"))) &&
                    (row.sourceUrl.isBlank() || portableWebLink(row.sourceUrl))
            } && rows.map(AcceptanceRow::id).distinct().size == rows.size) { "驗收表的內容或連結不符。" }
        }
        batch?.let { rows ->
            require(rows.size <= 100 && rows.all { it.input.length <= 30_000 && (it.answer?.length ?: 0) <= 200_000 }) {
                "批次資料超出大小限制。"
            }
        }
        require((batch != null || batchInstruction == null) && (batchInstruction?.length ?: 0) <= 30_000) { "批次指令超出大小限制。" }
        drafts?.let { records ->
            require(records.isNotEmpty() && records.keys.all { it in conversationIds } && records.keys.none { it in draftTexts.orEmpty() }) {
                "草稿必須屬於所選聊天室，且每則只能出現一次。"
            }
            records.values.forEach(PortableDraft::validate)
        }
        draftTexts?.let { drafts ->
            require(drafts.keys.all { it in conversationIds } && drafts.values.all { it.length in 1..100_000 }) {
                "草稿必須屬於所選聊天室，且長度不可超出限制。"
            }
        }
        projects?.let { entries ->
            val links = entries.flatMap { it.conversationIds }
            require(entries.size <= 100 && entries.map(ChatProject::id).distinct().size == entries.size &&
                links.all { it in conversationIds } && links.distinct().size == links.size && entries.all { project ->
                    runCatching { Uuid.parse(project.id) }.isSuccess && project.name.isNotBlank() && project.name.length <= 200 &&
                        project.instructions.length <= 30_000 && project.decisions.length <= 30_000 &&
                        project.documents.size <= 100 && project.documents.map { it.id }.distinct().size == project.documents.size &&
                        project.documents.all { it.name.length in 1..300 && it.text.length <= 200_000 }
                }) { "專案、參考資料或聊天室連結格式不符。" }
        }
    }

    /** Merge preserves an existing artifact or report. Recipes are deduplicated by content. */
    fun restoreValues(context: Context, replace: Boolean): Map<String, String> = buildMap {
            confirmedTodos?.let { incoming ->
                val current = if (replace) emptyList() else localTodos(context)
                put(TODO_KEY, Json.encodeToString(mergePortableTodos(current, incoming)))
            }
            recipes?.let { incoming ->
                val old = if (replace) emptyList() else readStoredRecipes(context).orEmpty()
                put("recipes_data", Json.encodeToString((old + incoming).distinct().take(200)))
            }
            artifact?.let {
                if (replace || LabPreferences.get(context, "artifact_data").isBlank()) put("artifact_data", Json.encodeToString(it))
            }
            acceptance?.let { incoming ->
                val old = if (replace) emptyList() else readList<AcceptanceRow>(context, "acceptance_data").orEmpty()
                put("acceptance_data", Json.encodeToString(mergeAcceptanceRows(old, incoming)))
            }
            batch?.let {
                if (replace || LabPreferences.get(context, "batch_data").isBlank()) {
                    put("batch_data", Json.encodeToString(it.map { row -> BatchRow(row.input, row.answer, row.failed) }))
                    put("batch_usage_id", "")
                    put("batch_instruction", batchInstruction.orEmpty())
                }
            }
        }

    companion object {
        fun read(context: Context, sections: Set<PortableLabSection>, ids: Set<Uuid>): PortableLabData? {
            val selected = sections.filter { it.available(context) }.toSet()
            val recipes = if (PortableLabSection.RECIPES in selected) readStoredRecipes(context)?.takeIf { it.isNotEmpty() } else null
            val artifact = if (PortableLabSection.ARTIFACT in selected) {
                LabPreferences.get(context, "artifact_data").takeIf(String::isNotBlank)?.let { Json.decodeFromString<LabArtifact>(it) }
            } else null
            val acceptance = if (PortableLabSection.ACCEPTANCE in selected) readList<AcceptanceRow>(context, "acceptance_data")?.takeIf { it.isNotEmpty() } else null
            val batch = if (PortableLabSection.BATCH in selected) readList<BatchRow>(context, "batch_data")
                ?.map { BatchRow(it.input, it.answer, it.failed) }?.takeIf { it.isNotEmpty() } else null
            val instruction = if (batch != null) LabPreferences.get(context, "batch_instruction") else null
            val selectedIds = ids.mapTo(hashSetOf()) { it.toString() }
            val projects = if (PortableLabSection.PROJECTS in selected) ProjectStore.get(context).projects.value
                .map { project -> project.copy(conversationIds = project.conversationIds.intersect(selectedIds)) }
                .takeIf { it.isNotEmpty() } else null
            val todos = if (PortableLabSection.TODOS in selected) localTodos(context).takeIf { it.isNotEmpty() } else null
            return PortableLabData(recipes, artifact, acceptance = acceptance, batch = batch, batchInstruction = instruction, projects = projects, confirmedTodos = todos)
                .takeIf { it.labels.isNotEmpty() }?.also { it.validate(selectedIds) }
        }

        private fun readStoredRecipes(context: Context): List<LabRecipe>? =
            readList<LabRecipe>(context, "recipes_data")

        private inline fun <reified T> readList(context: Context, key: String): List<T>? =
            LabPreferences.get(context, key).takeIf(String::isNotBlank)?.let { Json.decodeFromString<List<T>>(it) }
    }
}

internal fun mergeAcceptanceRows(existing: List<AcceptanceRow>, incoming: List<AcceptanceRow>): List<AcceptanceRow> =
    (existing + incoming).distinctBy(AcceptanceRow::id).take(1_000)

private fun portableWebLink(link: String): Boolean = link.length <= 2_048 && runCatching {
    val uri = java.net.URI(link)
    uri.scheme?.lowercase() in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)

internal fun mergePortableTodos(current: List<LocalTodo>, incoming: List<LocalTodo>): List<LocalTodo> {
    val combined = (current + incoming).distinctBy(LocalTodo::id)
    require(combined.size <= 250) { "合併後待辦會超過 250 項，請先整理目前的清單。" }
    return combined
}
