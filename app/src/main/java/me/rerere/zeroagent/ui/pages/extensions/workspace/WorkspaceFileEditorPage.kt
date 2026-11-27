package me.rerere.zeroagent.ui.pages.extensions.workspace

import androidx.compose.material3.ButtonDefaults

import me.rerere.zeroagent.utils.localizedErrorMessage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import me.rerere.zeroagent.ui.designsystem.ZeroProgressIndicator as CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import me.rerere.zeroagent.ui.designsystem.ZeroTextField as TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.dokar.sonner.ToastType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.repository.WorkspaceRepository
import me.rerere.zeroagent.data.repository.WorkspaceTextChanges
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.webview.WebView
import me.rerere.zeroagent.ui.components.webview.rememberWebViewState
import me.rerere.zeroagent.ui.context.LocalToaster
import me.rerere.zeroagent.ui.theme.CustomColors
import me.rerere.zeroagent.ui.theme.JetbrainsMono
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.compose.koinInject

/**
 * 工作区文本文件编辑/Preview页.
 *
 * FILES 区文件可编辑并保存; LINUX (rootfs) 区文件仅只读Preview (readOnly), 避免误改系统文件.
 */
@Composable
fun WorkspaceFileEditorPage(
    id: String,
    area: WorkspaceStorageArea,
    path: String,
) {
    val repository = koinInject<WorkspaceRepository>()
    val errorContext = androidx.compose.ui.platform.LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val editable = area == WorkspaceStorageArea.FILES
    val fileName = path.substringAfterLast('/').ifBlank { path }
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val supportsPreview = extension in setOf("html", "htm", "svg")
    var showPreview by rememberSaveable(id, area, path) { mutableStateOf(supportsPreview) }

    // 不能用 rememberTextFieldState: 它会把全文存进 saved state Bundle, 大文件切后台时触发 TransactionTooLargeException (#1953).
    // 内容本就由下方 LaunchedEffect 从磁盘加载, 无需 saveable.
    val textState = remember(id, area, path) { TextFieldState() }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var draftReview by remember(id, area, path) { mutableStateOf<List<WorkspaceTextEdit>?>(null) }
    var savedText by remember(id, area, path) { mutableStateOf("") }
    var diff by remember(id, area, path) { mutableStateOf<Pair<WorkspaceTextChanges.Change, String>?>(null) }
    var restoring by remember(id, area, path) { mutableStateOf(false) }
    var restoreMessage by remember(id, area, path) { mutableStateOf<String?>(null) }
    var loadingDiff by remember(id, area, path) { mutableStateOf(false) }

    LaunchedEffect(id, area, path) {
        loading = true
        loadError = null
        runCatching {
            repository.readTextForPreview(id, area, path)
        }.onSuccess { content ->
            textState.setTextAndPlaceCursorAtEnd(content)
            savedText = content
            loading = false
        }.onFailure {
            loadError = it.message ?: "Could not read the file"
            loading = false
        }
    }

    draftReview?.let { edits ->
        WorkspaceDraftReview(edits, onDismiss = { draftReview = null }, onApply = { selected ->
            draftReview = null
            saving = true
            val previous = savedText
            scope.launch {
                try {
                    val merged = withContext(Dispatchers.Default) { applyWorkspaceEdits(previous, selected) }
                    repository.writeReviewedText(id, path, previous, merged)
                    savedText = merged
                    textState.setTextAndPlaceCursorAtEnd(merged)
                    toaster.show("已儲存所選變更", type = ToastType.Success)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    toaster.show(errorContext.localizedErrorMessage(error), type = ToastType.Error)
                } finally {
                    saving = false
                }
            }
        })
    }

    diff?.let { (change, lines) ->
        WorkspaceDiffDialog(
            diff = lines,
            removing = change.previous == null,
            restoring = restoring,
            error = restoreMessage,
            onDismiss = { if (!restoring) diff = null },
            onRestore = {
                restoring = true
                restoreMessage = null
                scope.launch {
                    try {
                        when (repository.restoreTextChange(id, path, change)) {
                            WorkspaceTextChanges.RestoreResult.Changed -> {
                                restoreMessage = resources.getString(R.string.workspace_diff_conflict)
                            }
                            WorkspaceTextChanges.RestoreResult.Removed -> {
                                loadError = resources.getString(R.string.workspace_diff_created_file_removed)
                                diff = null
                            }
                            WorkspaceTextChanges.RestoreResult.Restored -> {
                                val restored = checkNotNull(change.previous)
                                savedText = restored
                                textState.setTextAndPlaceCursorAtEnd(restored)
                                diff = null
                                toaster.show(resources.getString(R.string.workspace_diff_restored), type = ToastType.Success)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        restoreMessage = errorContext.localizedErrorMessage(error)
                    } finally {
                        restoring = false
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    if (supportsPreview && !loading && loadError == null) {
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { showPreview = !showPreview }) {
                            Text(if (showPreview) "Source" else "Preview")
                        }
                    }
                    if (editable && !loading && loadError == null) {
                        TextButton(
                            shapes = ButtonDefaults.shapes(),
                            enabled = !saving && !loadingDiff && textState.text.toString() == savedText,
                            onClick = {
                                loadingDiff = true
                                restoreMessage = null
                                scope.launch {
                                    try {
                                        val change = repository.latestTextChange(id, path)
                                        val unified = withContext(Dispatchers.Default) { change?.unifiedDiff(path) }
                                        if (change == null || unified == null) {
                                            toaster.show(resources.getString(R.string.workspace_diff_unavailable))
                                        } else if (textState.text.toString() == savedText) {
                                            diff = change to unified
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        toaster.show(errorContext.localizedErrorMessage(error), type = ToastType.Error)
                                    } finally {
                                        loadingDiff = false
                                    }
                                }
                            },
                        ) { Text(stringResource(R.string.workspace_diff_action)) }
                        TextButton(
                            shapes = ButtonDefaults.shapes(), onClick = {
                                if (saving) return@TextButton
                                saving = true
                                val submittedText = textState.text.toString()
                                val previous = savedText
                                scope.launch {
                                    try {
                                        draftReview = withContext(Dispatchers.Default) {
                                            calculateWorkspaceEdits(previous, submittedText)
                                        }.takeIf { it.isNotEmpty() }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        toaster.show(errorContext.localizedErrorMessage(error), type = ToastType.Error)
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving && textState.text.toString() != savedText,
                        ) {
                            Text(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_save))
                        }
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            loadError != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                Text(
                    text = androidx.compose.ui.platform.LocalContext.current.localizedErrorMessage(raw = loadError),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            showPreview -> WorkspaceWebPreview(
                content = textState.text.toString(),
                isSvg = extension == "svg",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> TextField(
                state = textState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
                readOnly = !editable,
                lineLimits = TextFieldLineLimits.MultiLine(),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = JetbrainsMono,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
            )
        }
    }
}

@Composable
private fun WorkspaceWebPreview(
    content: String,
    isSvg: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = rememberWebViewState(
        data = content,
        baseUrl = "https://workspace-preview.invalid/",
        mimeType = if (isSvg) "image/svg+xml" else "text/html",
        settings = {
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        },
    )
    WebView(state = state, modifier = modifier)
}
