package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.material3.IconButtonDefaults

import me.rerere.zeroagent.ui.motion.*
import androidx.compose.material3.ButtonDefaults

import me.rerere.zeroagent.data.datastore.getCompactionContextLength
import me.rerere.zeroagent.data.datastore.getContextCompactionTargetTokens
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.TextButton
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog as AlertDialog
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import me.rerere.zeroagent.ui.components.ui.ZeroAgentModalBottomSheet as ModalBottomSheet
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import me.rerere.zeroagent.data.lab.LabPreferences
import me.rerere.zeroagent.ui.pages.lab.DraftRedactionDialog
import me.rerere.zeroagent.ui.motion.LocalConversationReplyMotion
import me.rerere.zeroagent.ui.motion.ReplyMotion
import me.rerere.zeroagent.ui.components.ui.rememberMotionEnabled
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.chat.ChatCommandEffect
import me.rerere.zeroagent.data.chat.ChatSlashCommand
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.findProvider
import me.rerere.zeroagent.data.datastore.getCurrentAssistant
import me.rerere.zeroagent.data.datastore.assistantForModel
import me.rerere.zeroagent.data.files.FilesManager
import me.rerere.zeroagent.data.files.importComposerAttachment
import me.rerere.zeroagent.data.chat.ChatExtras
import me.rerere.zeroagent.ui.theme.LocalDarkMode
import me.rerere.zeroagent.ui.theme.PresetThemes
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.repository.WorkspaceRepository
import me.rerere.zeroagent.service.ChatError
import me.rerere.zeroagent.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.zeroagent.ui.components.ai.completion.*
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.ui.components.ai.ChatInput
import me.rerere.zeroagent.ui.components.ai.FilesPicker
import me.rerere.zeroagent.ui.components.ai.SearchMode
import me.rerere.zeroagent.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.zeroagent.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.context.LocalToaster
import me.rerere.zeroagent.ui.context.Navigator
import me.rerere.zeroagent.ui.hooks.ChatInputState
import me.rerere.zeroagent.subagent.SubAgentRegistry
import me.rerere.zeroagent.subagent.SubAgentRun
import me.rerere.zeroagent.subagent.SubAgentStatus
import me.rerere.zeroagent.utils.base64Decode
import me.rerere.zeroagent.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@Composable
fun ChatPage(
    id: Uuid,
    text: String?,
    files: List<Uri>,
    nodeId: Uuid? = null,
    importId: String? = null,
    onImportConsumed: () -> Unit = {},
) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val filesManager: FilesManager = koinInject()
    val attachmentToaster = LocalToaster.current
    val navController = LocalNavController.current

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    // U4 — the sub-agent registry is not collected anywhere in the UI today, so a parent turn that
    // dispatched background work had no live signal at all. Fold it into the status line the chat
    // already shows instead of adding a second surface.
    val subAgentRegistry: SubAgentRegistry = koinInject()
    val subAgentRuns by subAgentRegistry.runs.collectAsStateWithLifecycle()
    val activeSubAgents = remember(id, subAgentRuns) {
        subAgentRuns.values.filter {
            it.parentChatId == id.toString() &&
                (it.status == SubAgentStatus.RUNNING || it.status == SubAgentStatus.PENDING)
        }
    }
    val subAgentStatus = subAgentStatusLine(activeSubAgents)
    var showSubAgentMonitor by remember(id) { mutableStateOf(false) }


    val appDrawer = LocalAppDrawer.current
    val isBigScreen = appDrawer.isPermanent

    val startVoiceMode = rememberVoiceModeStarter(vm, setting)

    val inputState = vm.inputState

    var importedRouteContent by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(files, text, importId) {
        if ((importId == null && importedRouteContent) || (files.isEmpty() && text.isNullOrBlank())) {
            return@LaunchedEffect
        }
        val (parts, rejected) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val rejected = mutableListOf<String>()
            files.mapNotNull { source -> filesManager.importComposerAttachment(source, onRejected = { rejected.add(it) }) } to rejected
        }
        rejected.forEach { attachmentToaster.show(it, type = ToastType.Error) }
        inputState.messageContent += parts
        text?.base64Decode()?.takeIf(String::isNotEmpty)?.let { decoded ->
            if (inputState.textContent.text.isNotEmpty()) inputState.appendText("\n\n")
            inputState.appendText(decoded)
        }
        if (importId == null) importedRouteContent = true
        onImportConsumed()
    }

    val chatListState = rememberSaveable(id.toString(), saver = LazyListState.Saver) { LazyListState() }
    val latestNodes by rememberUpdatedState(conversation.messageNodes)
    var consumedSearch by rememberSaveable(id.toString()) { mutableStateOf<String?>(null) }
    var initializedList by rememberSaveable(id.toString()) { mutableStateOf(false) }
    var searchHighlight by remember(id) { mutableStateOf<Uuid?>(null) }
    LaunchedEffect(id, nodeId, chatListState) {
        if (nodeId != null && consumedSearch != nodeId.toString()) {
            val index = snapshotFlow { latestNodes.displayIndexOf(nodeId) }.first { it >= 0 }
            searchHighlight = nodeId
            chatListState.requestScrollToItem(index)
            consumedSearch = nodeId.toString()
            initializedList = true
            delay(2_000)
            searchHighlight = null
        } else if (nodeId == null && !initializedList) {
            val nodes = snapshotFlow { latestNodes }.first { it.isNotEmpty() }
            chatListState.requestScrollToItem(nodes.groupAutomaticCompactionMessages().size)
            initializedList = true
        }
    }

    val themeContext = LocalContext.current
    val themeFlow = remember(themeContext, id) { ChatExtras.themeFlow(themeContext, id) }
    val themeId by themeFlow.collectAsStateWithLifecycle(initialValue = ChatExtras.theme(themeContext, id))
    val palette = PresetThemes.firstOrNull { it.id == themeId }
    MaterialExpressiveTheme(
        colorScheme = palette?.getColorScheme(LocalDarkMode.current) ?: MaterialTheme.colorScheme,
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        ChatPageContent(
            onStartVoiceMode = startVoiceMode,
            inputState = inputState,
            loadingJob = loadingJob,
            processingStatus = processingStatus,
            subAgentStatus = subAgentStatus,
            onOpenSubAgentMonitor = { showSubAgentMonitor = true },
            setting = setting,
            conversation = conversation,
            onOpenDrawer = appDrawer::open,
            navController = navController,
            vm = vm,
            chatListState = chatListState,
            searchHighlight = searchHighlight,

            currentChatModel = currentChatModel,
            bigScreen = isBigScreen,
            errors = errors,
            onDismissError = { vm.dismissError(it) },
            onClearAllErrors = { vm.clearAllErrors() },
        )
        if (showSubAgentMonitor) {
            SubAgentMonitorSheet(activeSubAgents, { showSubAgentMonitor = false }, { subAgentRegistry.requestCancel(it) })
        }
    }
}

/**
 * U4 — a one-line live summary of the sub-agent runs in flight, folded into the chat's existing
 * status line. Shows how many are running and how long the oldest has been going, and re-composes
 * once a second so the elapsed time keeps moving — the moving clock is the "still alive" signal
 * that was missing while a parent turn waited on background work. Returns null when none are
 * active, so the status line is exactly as before on an install that never dispatches.
 */
@Composable
private fun subAgentStatusLine(active: List<SubAgentRun>): String? {
    if (active.isEmpty()) return null
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active.size) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val oldestStartMs = active.minOf { it.startedAtMs }
    val elapsedSeconds = ((nowMs - oldestStartMs) / 1_000L).coerceAtLeast(0L)
    return stringResource(
        R.string.chat_page_subagents_running,
        active.size,
        formatElapsed(elapsedSeconds),
    )
}

private fun formatElapsed(seconds: Long): String {
    val minutes = seconds / 60
    val remaining = seconds % 60
    return "%d:%02d".format(minutes, remaining)
}

@Composable
private fun ChatPageContent(
    onStartVoiceMode: () -> Unit,
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    subAgentStatus: String? = null,
    onOpenSubAgentMonitor: () -> Unit = {},
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    onOpenDrawer: () -> Unit,

    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    searchHighlight: Uuid?,
    currentChatModel: Model?,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val localizedResources = androidx.compose.ui.platform.LocalResources.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    val turnFooters by vm.turnFooters.collectAsStateWithLifecycle()
    var previewMode by rememberSaveable { mutableStateOf(false) }
    val assistant = currentChatModel?.let { setting.assistantForModel(it) } ?: setting.getCurrentAssistant()
    var showFilesSheet by remember { mutableStateOf(false) }
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )

    var slashCompress by remember { mutableStateOf(false) }
    var slashHelp by remember { mutableStateOf(false) }
    var accessRequest by remember { mutableIntStateOf(0) }
    fun handleSlash(draft: String): Boolean = vm.handleSlashCommand(draft) { effect ->
        when (effect) {
            ChatCommandEffect.COMPACT -> slashCompress = true
            ChatCommandEffect.IMAGE -> navController.navigate(Screen.ImageGen)
            ChatCommandEffect.VOICE -> onStartVoiceMode()
            ChatCommandEffect.ACCESS -> accessRequest++
            ChatCommandEffect.HELP -> slashHelp = true
            ChatCommandEffect.DIRECTIVE_SAVED ->
                toaster.show(localizedResources.getString(R.string.zeroagent8_directive_saved))
            ChatCommandEffect.DIRECTIVE_REMOVED ->
                toaster.show(localizedResources.getString(R.string.zeroagent8_directive_off))
            ChatCommandEffect.GOAL_USAGE ->
                toaster.show(localizedResources.getString(R.string.zeroagent8_goal_usage))
        }
    }
    if (slashCompress) me.rerere.zeroagent.ui.components.ai.CompressContextDialog(
        defaultTargetTokens = setting.getContextCompactionTargetTokens(setting.getCompactionContextLength(currentChatModel)),
        onDismiss = { slashCompress = false }, onConfirm = vm::handleCompressContext)
    if (slashHelp) AlertDialog(onDismissRequest = { slashHelp = false },
        title = { Text(stringResource(R.string.zeroagent8_commands)) },
        text = { Column { ChatSlashCommand.entries.forEach { Text("/" + it.command + " — " + stringResource(it.description)) } } },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { slashHelp = false }) { Text(stringResource(R.string.zeroagent_voice_ok)) } })
    val completionProviders = remember(context, assistant.workspaceId, conversation.workspaceCwd, workspaceRepository) {
        listOf(SlashCommandCompletionProvider(context)) + (assistant.workspaceId?.let { workspaceId ->
            listOf(
                WorkspaceCompletionProvider(
                    workspaceId = workspaceId.toString(),
                    repository = workspaceRepository,
                    currentCwd = conversation.workspaceCwd,
                )
            )
        }.orEmpty())
    }

    // Model / search-provider switching moved out of the input bar and into the attachments panel
    // (the bar's "+"), so the same handlers now feed that sheet instead.
    val onUpdateSearchMode: (SearchMode) -> Unit = { mode ->
        val modelId = currentChatModel?.id
        vm.updateSettings { current ->
            current.withChatModelSearchMode(modelId, mode)
        }
    }
    val onUpdateChatModel: (Model) -> Unit = { model ->
        vm.setChatModel(model = model)
    }
    val onUpdateSearchService: (Int) -> Unit = { index ->
        vm.updateSettings { it.copy(searchServiceSelected = index) }
    }

    var sentCheck by remember(conversation.id) { mutableStateOf(false) }
    var pendingSentCheck by remember(conversation.id) { mutableStateOf(false) }
    val maskEnabled by remember(context) { LabPreferences.flow(context, "redaction", "false") }
        .collectAsStateWithLifecycle(LabPreferences.get(context, "redaction", "false"))
    var draftToMask by remember(conversation.id) { mutableStateOf<List<UIMessagePart>?>(null) }
    draftToMask?.takeIf { maskEnabled == "true" }?.let { outgoing ->
        DraftRedactionDialog(outgoing, onSend = { selected ->
            draftToMask = null
            pendingSentCheck = true
            vm.handleMessageSend(selected)
            inputState.clearInput()
        }, onDismiss = { draftToMask = null })
    }
    val lastSavedUserId = conversation.currentMessages.lastOrNull { it.role == me.rerere.ai.core.MessageRole.USER }?.id
    var observedUserId by remember(conversation.id) { mutableStateOf(lastSavedUserId) }
    LaunchedEffect(lastSavedUserId) {
        if (pendingSentCheck && lastSavedUserId != null && lastSavedUserId != observedUserId) {
            pendingSentCheck = false
            observedUserId = lastSavedUserId
            sentCheck = true
            delay(1300)
            sentCheck = false
        }
        observedUserId = lastSavedUserId
    }
    TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)
    val pawsFlow = remember(context) { LabPreferences.flow(context, "cat_paws", "false") }
    val paws by pawsFlow.collectAsStateWithLifecycle(initialValue = "false")
    val motionEnabled = rememberMotionEnabled()
    val liveReplyMotion = if (motionEnabled) ReplyMotion("soft", paws == "true") else null
    val privateChatFlow = remember(context) { LabPreferences.flow(context, "private_chat", "false") }
    val privateChatEnabled by privateChatFlow.collectAsStateWithLifecycle(initialValue = LabPreferences.get(context, "private_chat", "false"))
    val forkFlow = remember(context) { LabPreferences.flow(context, "chat_fork", "false") }
    val forkEnabled by forkFlow.collectAsStateWithLifecycle(initialValue = LabPreferences.get(context, "chat_fork", "false"))
    var regenerateInBranch by remember(conversation.id) { mutableStateOf<UIMessage?>(null) }
    regenerateInBranch?.let { original ->
        AlertDialog(
            onDismissRequest = { regenerateInBranch = null },
            title = { Text("從這則訊息重新生成？") },
            text = { Text("ZeroAgent 會從這裡建立新分支。原本的回覆和後續聊天會完整保留。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    regenerateInBranch = null
                    scope.launch {
                        try {
                            val branch = vm.regenerateInFork(original)
                            navigateToChatPage(navController, chatId = branch.id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            toaster.show("無法從這則訊息建立分支，請稍後再試。", type = ToastType.Error)
                        }
                    }
                }) { Text("建立分支並生成") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { regenerateInBranch = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    var showTemporary by remember(conversation.id) { mutableStateOf(false) }
    if (showTemporary && privateChatEnabled == "true") TemporaryChatDialog(setting, currentChatModel, onDismiss = { showTemporary = false })

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        AssistantBackground(setting = setting, modifier = Modifier)
        Scaffold(
            containerColor = Color.Transparent,
            // The title bar is drawn inside the content instead (see the overlay below): Scaffold
            // places its content over the top bar, and the conversation has to scroll UNDER the
            // title and fade out behind it.
            topBar = {},
            bottomBar = {
                val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
                Column {
                    if (subAgentStatus != null) {
                        androidx.compose.material3.TextButton(onClick = onOpenSubAgentMonitor) {
                            Text(subAgentStatus)
                        }
                    }
                    ChatContextMeter(
                        conversation = conversation,
                        contextLength = currentChatModel?.contextLength,
                        canCompact = loadingJob == null && conversation.messageNodes.size > 1,
                        onCompact = { handleSlash("/compact") },
                    )
                    ChatInput(
                        onStartVoiceMode = onStartVoiceMode,
                        onUpdateChatModel = onUpdateChatModel,
                        accessControl = {
                            Column {
                                if (conversation.customSystemPrompt?.contains("[zeroagent:plan]") == true) Text(stringResource(R.string.zeroagent8_plan_active), style = MaterialTheme.typography.labelSmall)
                                if (conversation.customSystemPrompt?.contains("[zeroagent:goal]") == true) Text(stringResource(R.string.zeroagent8_goal_active), style = MaterialTheme.typography.labelSmall)
                                me.rerere.zeroagent.ui.components.ai.ConversationAccessControl(
                                    conversation.id, accessRequest,
                                    modelName = currentChatModel?.let { it.displayName.ifBlank { it.modelId } }.orEmpty(),
                                )
                            }
                        },
                        state = inputState,
                        messageQueue = messageQueue,
                        onRemoveQueuedMessage = vm::removeQueuedMessage,
                        onBeginEditQueuedMessage = vm::beginEditQueuedMessage,
                        onFinishEditQueuedMessage = vm::finishEditQueuedMessage,
                        onResumeMessageQueue = vm::resumeMessageQueue,
                        loading = loadingJob != null,
                        settings = setting,
                        assistant = assistant,
                        model = currentChatModel,

                        completionProviders = completionProviders,
                        onCancelClick = {
                            vm.stopGeneration()
                        },
                        onSendClick = {
                            if (!inputState.isEditing() && inputState.messageContent.isEmpty() && handleSlash(inputState.textContent.text.toString())) return@ChatInput
                            if (currentChatModel == null) {
                                toaster.show(
                                    localizedResources.getString(R.string.chat_select_model_first),
                                    type = ToastType.Error,
                                )
                                // Dead-end fix: instead of only refusing, take the user to the
                                // first-run checklist so they can see everything that still needs
                                // setting up — and land on the "add a model" step.
                                navController.navigate(Screen.SettingProvider)
                                return@ChatInput
                            }
                            if (inputState.isEditing()) {
                                vm.handleMessageEdit(
                                    parts = inputState.getContents(),
                                    messageId = inputState.editingMessage!!,
                                )
                            } else {
                                val outgoing = inputState.getContents()
                                if (maskEnabled == "true" && outgoing.isNotEmpty()) {
                                    draftToMask = outgoing
                                    return@ChatInput
                                }
                                pendingSentCheck = true
                                vm.handleMessageSend(outgoing)
                            }
                            inputState.clearInput()
                        },
                        onLongSendClick = {
                            if (inputState.isEditing()) {
                                vm.handleMessageEdit(
                                    parts = inputState.getContents(),
                                    messageId = inputState.editingMessage!!,
                                )
                            } else {
                                vm.handleMessageSend(content = inputState.getContents(), answer = false)
                            }
                            inputState.clearInput()
                        },
                        onUpdateReasoningLevel = { level ->
                            currentChatModel?.id?.let { modelId ->
                                vm.updateSettings { it.withChatModelReasoning(modelId, level) }
                            }
                        },
                        onMoreClick = {
                            showFilesSheet = true
                        },
                    )
                }
            },
        ) { innerPadding ->
            // The title bar rides above the message list, so its measured height is what the list
            // has to stay clear of at rest; the rest of the inset becomes content padding, which is
            // what lets messages scroll underneath the bar.
            var topBarHeightPx by remember { mutableStateOf(0) }
            val topBarHeight = with(LocalDensity.current) { topBarHeightPx.toDp() }
            val layoutDirection = LocalLayoutDirection.current
            val listInset = PaddingValues(
                start = innerPadding.calculateLeftPadding(layoutDirection),
                top = if (topBarHeightPx > 0) {
                    topBarHeight
                } else {
                    innerPadding.calculateTopPadding() + 64.dp
                },
                end = innerPadding.calculateRightPadding(layoutDirection),
                bottom = innerPadding.calculateBottomPadding(),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (sentCheck) MotionIllustration(MotionArtwork.Success,
                    Modifier.align(Alignment.TopCenter).padding(top = listInset.calculateTopPadding()).size(32.dp),
                    description = "訊息已加入對話")
                if (conversation.currentMessages.isEmpty() && loadingJob == null && errors.isEmpty()) {
                    if (setting.providers.any { it.isReadyForOnboarding() }) {
                        ZeroAgentWelcome(Modifier.fillMaxSize().padding(listInset)) { inputState.setMessageText(it) }
                    } else {
                        val cloudProviders = setting.providers.filter { it.acceptsOnboardingApiKey() }
                        ChatModelSetupCard(
                            providers = cloudProviders,
                            onProviderClick = { navController.navigate(Screen.SettingProviderDetail(it.id.toString())) },
                            onAllProvidersClick = { navController.navigate(Screen.SettingProvider) },
                            canOfferLocal = setting.providers.any { it is ProviderSetting.LlamaCppLocal },
                            modifier = Modifier.fillMaxSize().padding(listInset),
                        )
                    }
                }
                if (vm.pendingDelete != null) {
                    androidx.compose.material3.Snackbar(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = listInset.calculateTopPadding() + 8.dp)
                            .padding(horizontal = 12.dp).zIndex(3f),
                        action = { androidx.compose.material3.TextButton(onClick = vm::undoDeleteMessage) { Text("復原") } },
                    ) { Text("這則訊息即將刪除") }
                }
                CompositionLocalProvider(LocalConversationReplyMotion provides liveReplyMotion) {
                if (conversation.currentMessages.isNotEmpty() || loadingJob != null || errors.isNotEmpty()) ChatList(
                    innerPadding = listInset,
                    conversation = conversation,
                    turnFooters = turnFooters,
                    state = chatListState,
                    searchHighlight = searchHighlight,
                    loading = loadingJob != null,
                    processingStatus = processingStatus,
                    previewMode = previewMode,
                    settings = setting,

                    errors = errors,
                    onDismissError = onDismissError,
                    onClearAllErrors = onClearAllErrors,
                    onRegenerate = {
                        if (conversation.messageNodes.lastOrNull()?.messages?.any { version -> version.id == it.id } == true) {
                            vm.regenerateAtMessage(it)
                        } else {
                            regenerateInBranch = it
                        }
                    },
                    onQuote = { message ->
                        val quote = message.toText().trim().take(4000).lineSequence().joinToString("\n") { "> $it" }
                        if (quote.isNotBlank()) {
                            val previous = inputState.textContent.text.toString()
                            if (inputState.isEditing()) inputState.clearInput()
                            inputState.setMessageText(quote + "\n\n" + previous)
                        }
                    },
                    onEdit = {
                        inputState.editingMessage = it.id
                        inputState.setContents(it.parts)
                    },
                    onRetryWithoutContext = { reply, excluded -> vm.regenerateAtMessage(reply, excludedContext = excluded) },
                    onForkMessage = if (forkEnabled == "true") ({ message ->
                        scope.launch {
                            val fork = vm.forkMessage(message = message)
                            navigateToChatPage(navController, chatId = fork.id)
                        }
                    }) else null,
                    onDelete = {
                        if (loadingJob != null) {
                            vm.showDeleteBlockedWhileGeneratingError()
                        } else {
                            vm.deleteMessage(it)
                        }
                    },
                    onUpdateMessage = vm::updateMessageVersion,
                    onClickSuggestion = { suggestion ->
                        if (inputState.isEditing()) inputState.clearInput()
                        inputState.setMessageText(suggestion)
                    },
                    onTranslate = { message, locale ->
                        vm.translateMessage(message, locale)
                    },
                    onClearTranslation = { message ->
                        vm.clearTranslationField(message.id)
                    },
                    onJumpToMessage = { messageNodeId ->
                        val index = conversation.messageNodes.displayIndexOf(messageNodeId)
                        if (index >= 0) {
                            previewMode = false
                            chatListState.requestScrollToItem(index)
                        }
                    },
                    onToolApproval = { toolCallId, approved, reason, scope, toolName ->
                        vm.handleToolApproval(toolCallId, approved, reason, scope, toolName)
                    },
                    onToolAnswer = { toolCallId, answer ->
                        vm.handleToolAnswer(toolCallId, answer)
                    },
                    onRerunTool = { toolCallId ->
                        vm.rerunTool(toolCallId)
                    },
                    onToggleFavorite = { node ->
                        vm.toggleMessageFavorite(node)
                    },
                    onConversationSystemPromptChange = { newPrompt ->
                        vm.updateConversation(conversation.copy(customSystemPrompt = newPrompt))
                        vm.saveConversationAsync()
                    },
                )
                }
                // Fade + title bar, drawn over the list: content dissolves as it slides under the
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { topBarHeightPx = it.height },
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to MaterialTheme.colorScheme.background,
                                    0.5f to MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                                    1f to Color.Transparent,
                                )
                            )
                    )
                    TopBar(
                        settings = setting,
                        assistant = assistant,
                        model = currentChatModel,
                        conversation = conversation,
                        onTemporaryChat = if (privateChatEnabled == "true") ({ showTemporary = true }) else null,
                        onClearDraft = vm::clearSavedDraft,
                        bigScreen = bigScreen,
                        onOpenDrawer = onOpenDrawer,
                        onOpenTerminal = {
                            navController.navigate(Screen.WorkspaceTerminal("android-shell"))
                        },

                        previewMode = previewMode,
                        onNewChat = {
                            navigateToChatPage(navController)
                        },
                        onClickMenu = {
                            previewMode = !previewMode
                        },
                    )
                }
            }
        }

        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                assistant = assistant,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                currentChatModel = currentChatModel,
                onUpdateSearchMode = onUpdateSearchMode,
                onUpdateChatModel = onUpdateChatModel,
                onUpdateSearchService = onUpdateSearchService,
                onStartVoiceMode = onStartVoiceMode,
                onDismiss = { showFilesSheet = false },
            )
        }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    assistant: Assistant,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    currentChatModel: Model?,
    onUpdateSearchMode: (SearchMode) -> Unit,
    onUpdateChatModel: (Model) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val navController = LocalNavController.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showInjectionSheet by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showInjectionSheet = false
        showCompressDialog = false
        onDismiss()
    }

    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = { dismissAll() },
    ) {
        TextButton(onClick = {
            dismissAll()
            navController.navigate(Screen.Productivity(conversation.id.toString()))
        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("效率工具 · 專案、模型比較與文件")
        }
        FilesPicker(
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            currentModel = currentChatModel,
            mcpManager = vm.mcpManager,
            enableSearch = assistant.enableWebSearch,
            onUpdateSearchMode = onUpdateSearchMode,
            onUpdateChatModel = onUpdateChatModel,
            onUpdateSearchService = onUpdateSearchService,
            onCompressContext = { additionalPrompt, targetTokens, keepRecentMessages ->
                vm.handleCompressContext(additionalPrompt, targetTokens, keepRecentMessages)
            },
            onUpdateAssistant = { updated ->
                vm.updateSettings { current ->
                    current.copy(assistants = current.assistants.map { profile ->
                        if (profile.id != updated.id) profile else profile.copy(
                            mcpServers = if (updated.mcpServers != assistant.mcpServers) updated.mcpServers else profile.mcpServers,
                            workspaceId = if (updated.workspaceId != assistant.workspaceId) updated.workspaceId else profile.workspaceId,
                            quickMessageIds = if (updated.quickMessageIds != assistant.quickMessageIds) updated.quickMessageIds else profile.quickMessageIds,
                            modeInjectionIds = if (updated.modeInjectionIds != assistant.modeInjectionIds) updated.modeInjectionIds else profile.modeInjectionIds,
                            lorebookIds = if (updated.lorebookIds != assistant.lorebookIds) updated.lorebookIds else profile.lorebookIds,
                            enabledSkills = if (updated.enabledSkills != assistant.enabledSkills) updated.enabledSkills else profile.enabledSkills,
                        )
                    })
                }
            },
            onUpdateConversation = {
                vm.updateConversation(it)
                vm.saveConversationAsync()
            },
            showInjectionSheet = showInjectionSheet,
            onShowInjectionSheetChange = { showInjectionSheet = it },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
            onStartVoiceMode = null,
        )
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    assistant: Assistant,
    model: Model?,
    conversation: Conversation,
    onOpenDrawer: () -> Unit,
    onOpenTerminal: () -> Unit,
    onTemporaryChat: (() -> Unit)?,
    onClearDraft: () -> Unit,

    bigScreen: Boolean,
    previewMode: Boolean,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
) {
    val toaster = LocalToaster.current
    // 与抽屉的 Rename 菜单项共享同一个重命名对话框状态，见 ChatDrawerVM.conversationToRename
    val activity = LocalActivity.current as ComponentActivity
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = activity)

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            if (!bigScreen) {
                ChatTopBarButton(
                    onClick = onOpenDrawer,
                ) {
                    Icon(HugeIcons.Menu03, stringResource(R.string.menu))
                }
            }
        },
        title = {
            val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
            Surface(
                onClick = {
                    if (conversation.messageNodes.isNotEmpty()) {
                        drawerVm.requestRenameConversation(conversation)
                    } else {
                        toaster.show(editTitleWarning, type = ToastType.Warning)
                    }
                },
                color = Color.Transparent,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                    val provider = model?.findProvider(providers = settings.providers, checkOverwrite = false)
                    Text(
                        text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (model != null && provider != null) {
                        Text(
                            text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            color = LocalContentColor.current.copy(0.65f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                            )
                        )
                    }
                    }
                }
            }
        },
        actions = {
            ChatTopBarButton(onClick = onOpenTerminal) {
                Icon(HugeIcons.ComputerTerminal01, stringResource(R.string.workspace_terminal_title))
            }
            ConversationMenu(conversation, previewMode, onClickMenu, onTemporaryChat, onClearDraft)

            ChatTopBarButton(
                onClick = {
                    onNewChat()
                },

            ) {
                Icon(HugeIcons.MessageAdd01, stringResource(R.string.chat_page_new_message))
            }
        },
    )
}

/** Compact tonal controls over the conversation background. */
@Composable
private fun ChatTopBarButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.padding(horizontal = 4.dp).size(48.dp),
        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        content = content,
    )
}
