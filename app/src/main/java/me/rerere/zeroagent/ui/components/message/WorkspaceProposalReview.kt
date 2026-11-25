package me.rerere.zeroagent.ui.components.message

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.zeroagent.data.repository.WorkspaceRepository
import me.rerere.zeroagent.ui.pages.extensions.workspace.WorkspaceDraftReview
import me.rerere.zeroagent.ui.pages.extensions.workspace.WorkspaceTextEdit
import me.rerere.zeroagent.ui.pages.extensions.workspace.calculateWorkspaceEdits
import me.rerere.zeroagent.ui.pages.extensions.workspace.applyWorkspaceEdits
import org.koin.compose.koinInject

@Composable
internal fun WorkspaceProposalReview(proposal: JsonObject) {
    fun text(key: String) = (proposal[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val workspace = text("workspace") ?: return
    val path = text("path") ?: return
    val previous = text("before")
    val proposed = text("after") ?: return
    val repo = koinInject<WorkspaceRepository>()
    val scope = rememberCoroutineScope()
    var pending by remember(proposal) { mutableStateOf(false) }
    var applied by remember(proposal) { mutableStateOf(false) }
    var edits by remember(proposal) { mutableStateOf<List<WorkspaceTextEdit>?>(null) }
    var error by remember(proposal) { mutableStateOf<String?>(null) }
    TextButton(onClick = {
        error = null
        pending = true
        scope.launch {
            try {
                edits = withContext(Dispatchers.Default) { calculateWorkspaceEdits(previous.orEmpty(), proposed) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "無法準備變更預覽。"
            } finally {
                pending = false
            }
        }
    }, enabled = !applied && !pending) { Text(if (applied) "已套用變更" else "檢查 $path 的變更") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    edits?.let { patches ->
        WorkspaceDraftReview(patches, onDismiss = { edits = null }, onApply = { selected ->
            pending = true
            edits = null
            scope.launch {
                try {
                    val merged = withContext(Dispatchers.Default) { applyWorkspaceEdits(previous.orEmpty(), selected) }
                    repo.writeReviewedText(workspace, path, previous, merged)
                    applied = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message ?: "套用變更失敗。"
                } finally {
                    pending = false
                }
            }
        })
    }
}