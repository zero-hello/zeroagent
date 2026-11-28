package me.rerere.zeroagent.ui.components.ai

import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import me.rerere.zeroagent.ui.designsystem.ZeroTheme
import me.rerere.zeroagent.ui.designsystem.rememberZeroFeedback
import me.rerere.zeroagent.ui.designsystem.ZeroFeedbackEvent

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import me.rerere.zeroagent.ui.designsystem.ZeroIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.dokar.sonner.ToastType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.core.ReasoningCapabilities
import me.rerere.zeroagent.data.datastore.findProvider
import me.rerere.zeroagent.ui.theme.AppLayout
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Mic01
import me.rerere.hugeicons.stroke.Voice
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Fullscreen
import me.rerere.hugeicons.stroke.Zap
import me.rerere.zeroagent.utils.localizedErrorMessage
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.getCurrentAssistant
import me.rerere.zeroagent.data.datastore.getQuickMessagesOfAssistant
import me.rerere.zeroagent.data.files.FilesManager
import me.rerere.zeroagent.data.files.importComposerAttachment
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.data.model.QuickMessage
import me.rerere.zeroagent.service.MessageQueueState
import me.rerere.zeroagent.service.QueuedMessage
import me.rerere.zeroagent.ui.components.ai.completion.ChatCompletionContext
import me.rerere.zeroagent.ui.components.ai.completion.ChatCompletionItem
import me.rerere.zeroagent.ui.components.ai.completion.ChatCompletionList
import me.rerere.zeroagent.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.zeroagent.ui.components.ui.KeepScreenOn
import me.rerere.zeroagent.ui.components.ui.permission.PermissionManager
import me.rerere.zeroagent.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.zeroagent.ui.components.ui.permission.rememberPermissionState
import me.rerere.zeroagent.ui.context.LocalSettings
import me.rerere.zeroagent.ui.context.LocalToaster
import me.rerere.zeroagent.ui.hooks.ChatInputState
import me.rerere.zeroagent.data.ai.ChatModelPreflight
import me.rerere.zeroagent.data.datastore.findModelById
import me.rerere.ai.provider.Modality
import me.rerere.zeroagent.utils.SoundEffectPlayer
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

data class ComposerToolActions(val openEditor: () -> Unit, val openReasoning: (() -> Unit)?)

@Composable
fun ChatInput(
    state: ChatInputState,
    loading: Boolean,
    settings: Settings,
    assistant: Assistant,
    model: Model?,

    onUpdateReasoningLevel: (me.rerere.ai.core.ReasoningLevel) -> Unit,
    onMoreClick: (ComposerToolActions) -> Unit,
    selectedQuote: String? = null,
    onRemoveQuote: () -> Unit = {},
    quoteFocusRequest: Int = 0,
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    completionProviders: List<ChatCompletionProvider> = emptyList(),
    accessControl: (@Composable () -> Unit)? = null,
    messageQueue: MessageQueueState = MessageQueueState(),
    onRemoveQueuedMessage: (Uuid) -> Unit = {},
    onBeginEditQueuedMessage: (Uuid) -> QueuedMessage? = { null },
    onFinishEditQueuedMessage: (Uuid, List<UIMessagePart>?) -> Unit = { _, _ -> },
    onResumeMessageQueue: () -> Unit = {},
    onStartVoiceMode: (() -> Unit)? = null,
    onUpdateChatModel: (Model) -> Unit,
) {
    val errorContext = androidx.compose.ui.platform.LocalContext.current
    val toaster = LocalToaster.current

    val composerFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(quoteFocusRequest) {
        if (quoteFocusRequest > 0 && selectedQuote != null) {
            composerFocus.requestFocus()
            androidx.compose.runtime.withFrameNanos { }
            keyboardController?.show()
        }
    }

    val focusManager = LocalFocusManager.current

    var isFullScreen by remember { mutableStateOf(false) }

    val ocrModel = settings.findModelById(settings.ocrModelId)
    val hasOcrModel = ocrModel != null && Modality.IMAGE in ocrModel.inputModalities &&
        ocrModel.findProvider(settings.providers)?.enabled == true
    val mediaWarning = model?.let { selected ->
        ChatModelPreflight.unsupportedMedia(selected, state.messageContent, hasOcrModel)
    }.orEmpty()
    fun warning(): String? = when {
        mediaWarning.isNotEmpty() -> "目前的模型無法讀取「${mediaWarning.joinToString("、") { it.displayName() }}」。請切換模型或移除附件。"
        model != null && ChatModelPreflight.fitsDraft(model, state.getContents()) == false ->
            "這則訊息已超過模型的輸入上限，請縮短內容或切換模型。"
        else -> null
    }

    val feedback = rememberZeroFeedback()
    fun sendMessage() {
        if (!state.isEmpty()) warning()?.let {
            toaster.show(it, type = ToastType.Error)
            return
        }
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) {
            onCancelClick()
        } else {
            if (!state.isEmpty()) feedback(ZeroFeedbackEvent.Send)
            onSendClick()
        }
    }

    fun sendMessageWithoutAnswer() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) onCancelClick() else onLongSendClick()
    }

    val showReasoning = model?.abilities?.contains(ModelAbility.REASONING) == true
    // Keep the composer multiline and stable while typing.
    val radius by androidx.compose.animation.core.animateDpAsState(
        if (state.textContent.text.lineSequence().take(2).count() > 1) 22.dp else 28.dp,
        me.rerere.zeroagent.ui.designsystem.ZeroMotion.fluid(), label = "Composer corners",
    )
    val containerShape = RoundedCornerShape(radius)

    val capabilityRevision by ReasoningCapabilities.revision.collectAsState()
    val provider = model?.findProvider(settings.providers)
    val reasoningOptions = remember(model, provider, capabilityRevision) { ReasoningCapabilities.forModel(model, provider) }
    val reasoningLevel = ReasoningCapabilities.effective(
        model,
        provider,
        model?.generation?.reasoningLevel ?: assistant.reasoningLevel,
    )
    var showReasoningPicker by remember(model?.id, provider?.id) { mutableStateOf(false) }
    if (showReasoningPicker) ReasoningPicker(
        reasoningLevel = reasoningLevel,
        supportedLevels = reasoningOptions.levels,
        capabilitiesDetected = reasoningOptions.detected,
        modelLabel = model?.displayName,
        onDismissRequest = { showReasoningPicker = false },
        onUpdateReasoningLevel = onUpdateReasoningLevel,
    )

    Surface(
        color = Color.Transparent,
    ) {
        BoxWithConstraints(
            modifier = modifier.fillMaxWidth().imePadding().navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
        val compact = maxHeight < 360.dp
        val composerHeight = if (compact) (maxHeight * .35f).coerceIn(minOf(52.dp, maxHeight), maxHeight) else maxHeight
        Column(
            modifier = Modifier.widthIn(max = AppLayout.chatWidth).fillMaxWidth()
                .heightIn(max = composerHeight).verticalScroll(rememberScrollState(), reverseScrolling = compact)
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Permission belongs to the conversation; it floats above the composer.
            accessControl?.let { control ->
                ComposerPermissionRow(control)
            }
            MessageQueuePanel(
                state = messageQueue,
                onRemove = onRemoveQueuedMessage,
                onBeginEdit = onBeginEditQueuedMessage,
                onFinishEdit = onFinishEditQueuedMessage,
                onResume = onResumeMessageQueue,
            )
            selectedQuote?.let { quote ->
                me.rerere.zeroagent.ui.designsystem.ZeroQuotePreviewCard(
                    quote, stringResource(R.string.quick_chat_close), onRemoveQuote,
                )
            }
            if (state.messageContent.isNotEmpty()) MediaFileInputRow(state = state)
            val colors = ZeroTheme.colors
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(containerShape),
                shape = containerShape,
                tonalElevation = 0.dp,
                color = colors.surface,
                border = BorderStroke(1.dp, if (loading) colors.accentOlive else colors.borderSubtle),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (mediaWarning.isNotEmpty()) {
                        Text(
                            "目前的模型無法讀取：${mediaWarning.joinToString("、") { it.displayName() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (!state.isEmpty() && model != null && !ChatModelPreflight.toolsAvailable(model) &&
                        (assistant.localTools.isNotEmpty() || assistant.mcpServers.isNotEmpty() || assistant.workspaceId != null)
                    ) {
                        Text(
                            "這個模型可以回覆文字，但無法使用助理工具。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val quickPrompts = settings.getQuickMessagesOfAssistant(assistant)
                    if (quickPrompts.isNotEmpty() && !state.isEditing()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            quickPrompts.forEach { prompt ->
                                androidx.compose.material3.AssistChip(
                                    onClick = {
                                        state.setMessageText(prompt.content)
                                        if (settings.displaySetting.quickPromptAutoSend && !loading) sendMessage()
                                    },
                                    label = { Text(prompt.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                    }
                    ComposerActionRow(
                        loading = loading,
                        hasDraft = !state.isEmpty(),
                        onAttach = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            feedback(ZeroFeedbackEvent.Tools)
                            onMoreClick(ComposerToolActions(
                                openEditor = { isFullScreen = true },
                                openReasoning = if (showReasoning && reasoningOptions.levels.size > 1) {
                                    { showReasoningPicker = true }
                                } else null,
                            ))
                        },
                        onVoiceCall = onStartVoiceMode,
                        onSend = { sendMessage() },
                        onStop = onCancelClick,
                        onModelOptions = null,
                        onLongSend = { sendMessageWithoutAnswer() },
                    ) { fieldModifier ->
                        TextInputRow(
                            state = state,
                            completionProviders = completionProviders,
                            focusRequester = composerFocus,
                            onSendMessage = { sendMessage() },
                            modifier = fieldModifier,
                        )
                    }
                    if (settings.displaySetting.enableHoldDictation) {
                        HoldToDictate { text ->
                            if (state.textContent.text.isNotBlank()) state.appendText(" ")
                            state.appendText(text)
                        }
                    }
                    if (loading) {
                        KeepScreenOn()
                    }
                }
            }
        }
    }

    }
    if (isFullScreen) {
        FullScreenEditor(state = state) {
            isFullScreen = false
        }
    }
}

@Composable
private fun TextInputRow(
    state: ChatInputState,
    completionProviders: List<ChatCompletionProvider>,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val settings = LocalSettings.current
    val assistant = settings.getCurrentAssistant()
    val quickMessages = remember(settings.quickMessages, assistant.quickMessageIds) {
        settings.getQuickMessagesOfAssistant(assistant)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.isEditing()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.editing))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { state.clearInput() }) {
                        Icon(HugeIcons.Cancel01, stringResource(R.string.cancel_edit))
                    }
                }
            }
        }

        var isFocused by remember { mutableStateOf(false) }
        var completionList by remember { mutableStateOf<ChatCompletionList?>(null) }
        val receiveContentListener = rememberPasteLongTextAsFileListener(state)

        LaunchedEffect(completionProviders, isFocused) {
            if (!isFocused || completionProviders.isEmpty()) {
                completionList = null
                return@LaunchedEffect
            }

            snapshotFlow {
                ChatCompletionContext(
                    text = state.textContent.text.toString(),
                    selection = state.textContent.selection,
                )
            }.collectLatest { context ->
                val lists = completionProviders.mapNotNull { provider ->
                    try {
                        provider.complete(context)
                            ?.takeIf { it.items.isNotEmpty() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
                val primary = lists.firstOrNull()
                completionList = primary?.let { list ->
                    val mergedItems = lists
                        .filter { it.replacementRange == list.replacementRange }
                        .flatMap { it.items }
                        .distinctBy { it.label to it.insertText }
                        .sortedWith(
                            compareByDescending<ChatCompletionItem> { it.sortScore }
                                .thenBy { it.label.length }
                                .thenBy { it.label.lowercase() }
                        )
                        .take(8)
                    list.copy(items = mergedItems)
                }
            }
        }

        val visibleCompletion = completionList?.takeIf { it.items.isNotEmpty() }
        var lastCompletion by remember { mutableStateOf<ChatCompletionList?>(null) }
        androidx.compose.runtime.SideEffect { if (visibleCompletion != null) lastCompletion = visibleCompletion }
        val feedback = rememberZeroFeedback()
        val slashVisible = visibleCompletion?.providerId == "zeroagent-slash"
        LaunchedEffect(slashVisible) { if (slashVisible) feedback(ZeroFeedbackEvent.Command) }
        androidx.compose.animation.AnimatedVisibility(
            visible = visibleCompletion != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(expandFrom = Alignment.Bottom),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            val list = visibleCompletion ?: lastCompletion ?: return@AnimatedVisibility
            CompletionPopup(list, onItemClick = { item ->
                if (visibleCompletion === list) state.applyCompletion(list.replacementRange, item)
                completionList = null
            })
        }

        TextField(
            state = state.textContent,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .testTag("chat_input")
                .contentReceiver(receiveContentListener)
                .onFocusChanged {
                    isFocused = it.isFocused
                    onFocusChanged(it.isFocused)
                },
            shape = MaterialTheme.shapes.largeIncreased,
            placeholder = {
                Text(stringResource(R.string.chat_input_hint_1))
            },
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (settings.displaySetting.sendOnEnter) ImeAction.Send else ImeAction.Default
            ),
            onKeyboardAction = {
                if (settings.displaySetting.sendOnEnter && state.textContent.composition == null && !state.isEmpty()) {
                    onSendMessage()
                }
            },
            colors = TextFieldDefaults.colors().copy(
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            leadingIcon = if (quickMessages.isNotEmpty()) {
                {
                    QuickMessageButton(quickMessages = quickMessages, state = state)
                }
            } else null,
        )
    }
}

// Note: keyboard clipboard-chip insertions go through commitText, which bypasses
// Compose's content receiver; only long-press Paste and Ctrl+V trigger this listener.
@Composable
private fun rememberPasteLongTextAsFileListener(state: ChatInputState): ReceiveContentListener {
    val settings = LocalSettings.current
    val filesManager: FilesManager = koinInject()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val error = stringResource(R.string.attachment_open_failed)
    return remember(state, filesManager, toaster, scope, error, settings.displaySetting.pasteLongTextAsFile,
        settings.displaySetting.pasteLongTextThreshold) {
        ReceiveContentListener { content ->
            content.consume { item ->
                val uri = item.uri?.takeIf { it.scheme == "content" }
                val text = item.text?.toString()
                val attachText = settings.displaySetting.pasteLongTextAsFile && content.hasMediaType(MediaType.Text) &&
                    text != null && text.length > settings.displaySetting.pasteLongTextThreshold
                if (uri == null && !attachText) return@consume false
                scope.launch {
                    var part: UIMessagePart? = null
                    var issue: String? = null
                    try {
                        withContext(Dispatchers.IO) {
                            part = uri?.let { filesManager.importComposerAttachment(it) { message -> issue = message } }
                            if (part == null && attachText && text != null) {
                                part = filesManager.createChatTextFile(text)
                                issue = null
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        val copiedPath = when (val copied = part) {
                            is UIMessagePart.Image -> copied.url
                            is UIMessagePart.Audio -> copied.url
                            is UIMessagePart.Video -> copied.url
                            is UIMessagePart.Document -> copied.url
                            else -> null
                        }
                        if (copiedPath != null) withContext(NonCancellable + Dispatchers.IO) {
                            filesManager.deleteChatFiles(listOf(copiedPath.toUri()))
                        }
                        throw cancelled
                    } catch (_: Exception) {
                        issue = error
                    }
                    val attachment = part
                    if (attachment != null) state.messageContent += attachment
                    else {
                        if (!text.isNullOrEmpty()) state.appendText(text)
                        toaster.show(issue ?: error, type = ToastType.Error)
                    }
                }
                true
            }
        }
    }
}

@Composable
private fun CompletionPopup(
    completionList: ChatCompletionList,
    onItemClick: (ChatCompletionItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, ZeroTheme.colors.borderSubtle),
        color = ZeroTheme.colors.surface,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            items(
                items = completionList.items,
                key = { item -> "${item.label}:${item.insertText}" },
            ) { item ->
                Surface(
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.detail?.let { detail ->
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ChatInputState.applyCompletion(
    replacementRange: TextRange,
    item: ChatCompletionItem,
) {
    val textLength = textContent.text.length
    val start = replacementRange.min.coerceIn(0, textLength)
    val end = replacementRange.max.coerceIn(start, textLength)
    textContent.edit {
        replace(start, end, item.insertText)
        selection = TextRange(start + item.insertText.length)
    }
}

@Composable
private fun QuickMessageButton(
    quickMessages: List<QuickMessage>,
    state: ChatInputState,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            expanded = !expanded
        }) {
        Icon(HugeIcons.Zap, null)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 200.dp, max = 360.dp)
        ) {
            quickMessages.forEach { quickMessage ->
                Surface(
                    onClick = {
                        state.appendText(quickMessage.content)
                        expanded = false
                    },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = quickMessage.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = quickMessage.content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenEditor(
    state: ChatInputState, onDone: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = {
            onDone()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row {
                        TextButton(
                            shapes = ButtonDefaults.shapes(), onClick = {
                                onDone()
                            }) {
                            Text(stringResource(R.string.chat_page_save))
                        }
                    }
                    val receiveContentListener = rememberPasteLongTextAsFileListener(state)
                                TextField(
                        state = state.textContent,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .fillMaxSize()
                            .contentReceiver(receiveContentListener),
                        shape = RoundedCornerShape(32.dp),
                        placeholder = {
                            Text(stringResource(R.string.chat_input_hint_1))
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        colors = TextFieldDefaults.colors().copy(
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}

private fun Modality.displayName(): String = when (this) {
    Modality.IMAGE -> "圖片"
    Modality.AUDIO -> "音訊"
    Modality.VIDEO -> "影片"
    Modality.TEXT -> "文字"
}