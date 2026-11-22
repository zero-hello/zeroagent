package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.material3.ButtonDefaults

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import me.rerere.zeroagent.ui.components.ui.ExpressiveDivider as HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog as AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import me.rerere.zeroagent.ui.components.ui.ZeroAgentModalBottomSheet as ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import me.rerere.zeroagent.ui.components.ui.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Console
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.FolderAdd
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Video01
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.zeroagent.ui.components.ui.ZeroAgentWorkspaceHeader
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.model.Avatar
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.model.Folder
import me.rerere.zeroagent.ui.components.ui.BackupReminderCard
import me.rerere.zeroagent.ui.components.ui.Greeting
import me.rerere.zeroagent.ui.components.ui.Tooltip
import me.rerere.zeroagent.ui.components.ui.AssistantAvatar
import me.rerere.zeroagent.ui.components.ui.UIAvatar
import me.rerere.zeroagent.ui.components.ui.UpdateCard
import androidx.compose.ui.draw.clip
import me.rerere.zeroagent.ui.context.LocalToaster
import me.rerere.zeroagent.ui.context.Navigator
import com.dokar.sonner.ToastType
import me.rerere.zeroagent.ui.hooks.EditStateContent
import me.rerere.zeroagent.ui.hooks.rememberIsPlayStoreVersion
import me.rerere.zeroagent.ui.hooks.useEditState
import me.rerere.zeroagent.ui.modifier.onClick
import me.rerere.zeroagent.utils.navigateToChatPage
import me.rerere.zeroagent.utils.toDp
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.core.net.toUri
import me.rerere.zeroagent.data.files.FilesManager
import me.rerere.zeroagent.utils.UpdateChecker
import kotlin.uuid.Uuid

@Composable
fun ChatDrawerContent(
    navController: Navigator,
    settings: Settings,
    currentConversationId: Uuid?,

) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val localizedResources = androidx.compose.ui.platform.LocalResources.current
    val toaster = LocalToaster.current
    val isPlayStore = rememberIsPlayStoreVersion()

    val activity = androidx.activity.compose.LocalActivity.current as ComponentActivity
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = activity)
    val filesManager: FilesManager = koinInject()
    val updateChecker: UpdateChecker = koinInject()

    val conversations = drawerVm.conversations.collectAsLazyPagingItems()
    val unsentNewChats by drawerVm.unsentNewChats.collectAsStateWithLifecycle()
    val folders by drawerVm.folders.collectAsStateWithLifecycle()
    val selectedFolderId by drawerVm.selectedFolderId.collectAsStateWithLifecycle()
    val archiveFolderId by drawerVm.archiveFolderId.collectAsStateWithLifecycle()
    val protectedFolderId by drawerVm.protectedFolderId.collectAsStateWithLifecycle()
    val conversationListState = rememberLazyListState(
        initialFirstVisibleItemIndex = drawerVm.scrollIndex,
        initialFirstVisibleItemScrollOffset = drawerVm.scrollOffset,
    )

    LaunchedEffect(conversationListState) {
        snapshotFlow {
            conversationListState.firstVisibleItemIndex to
                conversationListState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collectLatest { (index, offset) ->
                drawerVm.saveScrollPosition(index, offset)
            }
    }

    val conversationJobs by drawerVm.conversationJobs.collectAsStateWithLifecycle()

    // 昵称编辑状态
    val nicknameEditState = useEditState<String> { newNickname ->
        drawerVm.updateSettings(
            settings.copy(
                displaySetting = settings.displaySetting.copy(
                    userNickname = newNickname
                )
            )
        )
    }

    // 移动对话状态
    var showMoveToAssistantSheet by remember { mutableStateOf(false) }
    var conversationToMove by remember { mutableStateOf<Conversation?>(null) }
    val bottomSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    // 文件夹相关状态
    var showMoveToFolderSheet by remember { mutableStateOf(false) }
    var conversationToMoveFolder by remember { mutableStateOf<Conversation?>(null) }
    val folderSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }
    // 与顶部标题点击共享，见 ChatDrawerVM.conversationToRename
    val conversationToRename by drawerVm.conversationToRename.collectAsStateWithLifecycle()

    // Menu popup 状态

    val updateCheckDisabledUntil = settings.displaySetting.updateCheckDisabledUntilEpochMillis
    var updateChecksEnabled by remember(updateCheckDisabledUntil) {
        mutableStateOf(updateCheckDisabledUntil <= System.currentTimeMillis())
    }
    LaunchedEffect(updateCheckDisabledUntil) {
        while (true) {
            val remaining = updateCheckDisabledUntil - System.currentTimeMillis()
            if (remaining <= 0) {
                updateChecksEnabled = true
                break
            }
            updateChecksEnabled = false
            delay(minOf(remaining, 60 * 60 * 1_000L))
        }
    }

    val drawerWidth = (LocalConfiguration.current.screenWidthDp.dp * 0.88f).coerceAtMost(320.dp)
    ModalDrawerSheet(
        modifier = Modifier.width(drawerWidth),
        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        drawerTonalElevation = 0.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val headerLimit = maxHeight * 0.45f
            val footerLimit = maxHeight * 0.35f
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = headerLimit)
                        .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ZeroAgentWorkspaceHeader()
                    FilledTonalButton(
                        onClick = { navigateToChatPage(navController) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Icon(HugeIcons.Message01, null, Modifier.padding(end = 8.dp).size(20.dp))
                        Text(stringResource(R.string.chat_page_new_chat))
                    }
                    DrawerActions(navController)
                    FolderBar(
                        protectedFolderId = protectedFolderId, folders = folders,
                        selectedFolderId = selectedFolderId,
                        onSelect = drawerVm::selectFolder,
                        onCreate = { showCreateFolderDialog = true },
                        onRename = { folderToRename = it },
                        onDelete = { folderToDelete = it },
                    )
                    if (updateChecksEnabled && !settings.init && !isPlayStore) UpdateCard(updateChecker)
                    BackupReminderCard(settings, onClick = { navController.navigate(Screen.Backup) })
                }
                ConversationList(
                    currentConversationId = currentConversationId,
                    conversations = conversations,
                    conversationJobs = conversationJobs.keys,
                    unsentNewChats = unsentNewChats,
                    onOpenUnsentChat = { navigateToChatPage(navController, it) },
                    listState = conversationListState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onClick = { navigateToChatPage(navController, it.id) },
                    onRename = { drawerVm.requestRenameConversation(it) },
                    onDelete = {
                        scope.launch {
                            drawerVm.deleteConversation(it).join()
                            conversations.refresh()
                            if (it.id == currentConversationId) navigateToChatPage(navController)
                        }
                    },
                    onPin = { drawerVm.updatePinnedStatus(it) },
                    onMoveToAssistant = { conversationToMove = it; showMoveToAssistantSheet = true },
                    onMoveToFolder = { conversationToMoveFolder = it; showMoveToFolderSheet = true },
                )
                HorizontalDivider()
                DrawerFooter(
                    modifier = Modifier.fillMaxWidth().heightIn(max = footerLimit),
                    settings = settings,
                    nicknameEditState = nicknameEditState,
                    onNavigate = { navController.navigate(it) { launchSingleTop = true } },
                    onAvatarUpdate = { avatar ->
                        val oldAvatar = settings.displaySetting.userAvatar
                        if (oldAvatar is Avatar.Image && oldAvatar != avatar) {
                            filesManager.deleteChatFiles(listOf(oldAvatar.url.toUri()))
                        }
                        drawerVm.updateSettings(settings.copy(displaySetting = settings.displaySetting.copy(userAvatar = avatar)))
                    },
                )
            }
        }
    }

    // 昵称编辑对话框
    nicknameEditState.EditStateContent { nickname, onUpdate ->
        AlertDialog(
            onDismissRequest = {
                nicknameEditState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_nickname))
            },
            text = {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.chat_page_nickname_placeholder)) }
                )
            },
            confirmButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(), onClick = {
                        nicknameEditState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(), onClick = {
                        nicknameEditState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // 移动到文件夹 Bottom Sheet
    if (showMoveToFolderSheet) {
        val doMove: (Uuid?) -> Unit = { folderId ->
            conversationToMoveFolder?.let { conversation ->
                drawerVm.moveConversationToFolder(conversation.id, folderId)
                scope.launch {
                    folderSheetState.hide()
                    showMoveToFolderSheet = false
                    conversationToMoveFolder = null
                    conversations.refresh()
                }
            }
        }
        ModalBottomSheet(
            onDismissRequest = {
                showMoveToFolderSheet = false
                conversationToMoveFolder = null
            },
            sheetState = folderSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.chat_page_move_to_folder),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 移出文件夹（未归类）
                Surface(
                    onClick = { doMove(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = if (conversationToMoveFolder?.folderId == null) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(HugeIcons.Folder01, null)
                        Text(
                            text = stringResource(R.string.chat_page_remove_from_folder),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(folders) { folder ->
                        val isCurrent = folder.id == conversationToMoveFolder?.folderId
                        Surface(
                            onClick = { doMove(folder.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            tonalElevation = if (isCurrent) 2.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(HugeIcons.Folder01, null)
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.titleMedium,
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

    // 新建文件夹对话框
    if (showCreateFolderDialog) {
        var name by remember { mutableStateOf("") }
        var archive by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.chat_page_create_folder)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.chat_page_folder_name)) }
                    )
                    // P2-23 (D7) - mark this folder as the assistant's sub-agent archive.
                    // One folder per assistant; an empty one stays deletable.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_page_folder_purpose_sub_agent),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = archive, onCheckedChange = { archive = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(), onClick = {
                        drawerVm.createFolder(name, archive)
                        showCreateFolderDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.chat_page_save)) }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { showCreateFolderDialog = false }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // 重命名文件夹对话框
    folderToRename?.let { folder ->
        var name by remember(folder.id) { mutableStateOf(folder.name) }
        var archive by remember(folder.id) { mutableStateOf(archiveFolderId == folder.id) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text(stringResource(R.string.chat_page_rename_folder)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_page_folder_purpose_sub_agent),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = archive, onCheckedChange = { archive = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(), onClick = {
                        drawerVm.renameFolder(folder.id, name, archive)
                        folderToRename = null
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.chat_page_save)) }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { folderToRename = null }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // 重命名会话对话框（顶部标题点击与本抽屉的 Rename 菜单项共享同一个目标状态）
    conversationToRename?.let { conversation ->
        var title by remember(conversation.id) { mutableStateOf(conversation.title) }
        AlertDialog(
            onDismissRequest = { drawerVm.dismissRenameConversation() },
            title = { Text(stringResource(R.string.chat_page_rename_chat)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    TextButton(
                        shapes = ButtonDefaults.shapes(), onClick = {
                            drawerVm.generateTitle(conversation, true)
                            drawerVm.dismissRenameConversation()
                        }
                    ) {
                        Text(stringResource(R.string.chat_page_regenerate_title))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(), onClick = {
                        drawerVm.renameConversation(conversation.id, title)
                    },
                    enabled = title.isNotBlank()
                ) { Text(stringResource(R.string.chat_page_save)) }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { drawerVm.dismissRenameConversation() }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // 删除文件夹确认
    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(R.string.chat_page_delete_folder)) },
            text = { Text(stringResource(R.string.chat_page_delete_folder_confirm, folder.name)) },
            confirmButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(), onClick = {
                        scope.launch {
                            when (drawerVm.deleteFolder(folder.id)) {
                                FolderDeleteOutcome.Deleted -> {
                                    folderToDelete = null
                                    conversations.refresh()
                                }
                                FolderDeleteOutcome.Generating ->
                                    toaster.show(localizedResources.getString(R.string.chat_page_delete_folder_generating), type = ToastType.Warning)
                                FolderDeleteOutcome.Protected ->
                                    toaster.show(localizedResources.getString(R.string.chat_page_delete_folder_protected), type = ToastType.Warning)
                            }
                        }
                    }
                ) { Text(stringResource(R.string.chat_page_delete)) }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { folderToDelete = null }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // 移动到助手 Bottom Sheet
    if (showMoveToAssistantSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showMoveToAssistantSheet = false
                conversationToMove = null
            },
            sheetState = bottomSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.chat_page_move_to_assistant),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(settings.assistants, key = { it.id }) { assistant ->
                        AssistantItem(
                            assistant = assistant,
                            isCurrentAssistant = assistant.id == conversationToMove?.assistantId,
                            onClick = {
                                conversationToMove?.let { conversation ->
                                    drawerVm.moveConversationToAssistant(conversation, assistant.id, currentConversationId)
                                    scope.launch {
                                        bottomSheetState.hide()
                                        showMoveToAssistantSheet = false
                                        conversationToMove = null
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerActions(navController: Navigator) {
    FlowRow(
        maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.15f) 1 else 2,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(shapes = ButtonDefaults.shapes(), onClick = { navController.navigate(Screen.MessageSearch) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(HugeIcons.Search01, null, Modifier.padding(end = 8.dp).size(18.dp))
            Text(stringResource(R.string.chat_page_search_chats), style = MaterialTheme.typography.labelMedium)
        }
        TextButton(shapes = ButtonDefaults.shapes(), onClick = { navController.navigate(Screen.History) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(HugeIcons.TransactionHistory, null, Modifier.padding(end = 8.dp).size(18.dp))
            Text(stringResource(R.string.chat_page_history), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun DrawerFooter(
    modifier: Modifier = Modifier,
    settings: Settings,
    nicknameEditState: me.rerere.zeroagent.ui.hooks.EditState<String>,
    onNavigate: (Screen) -> Unit,
    onAvatarUpdate: (Avatar) -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrawerFooterAction(HugeIcons.Console, stringResource(R.string.embedded_terminal_title)) { onNavigate(Screen.WorkspaceTerminal("android-shell")) }
            DrawerFooterAction(HugeIcons.TransactionHistory, stringResource(R.string.zeroagent6_tasks)) { onNavigate(Screen.TaskCenter) }
        }
        DrawerSettingsAction { onNavigate(Screen.Setting) }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UIAvatar(
                name = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                value = settings.displaySetting.userAvatar,
                onUpdate = onAvatarUpdate,
                modifier = Modifier.size(40.dp),
            )
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = { nicknameEditState.open(settings.displaySetting.userNickname) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            IconButton(onClick = { onNavigate(Screen.SettingModels) }) {
                Icon(HugeIcons.Sparkles, stringResource(R.string.zeroagent11_model_parameters))
            }
        }
    }
}

@Composable
private fun RowScope.DrawerFooterAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(
        shapes = ButtonDefaults.shapes(),
        onClick = onClick,
        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DrawerSettingsAction(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shapes = ButtonDefaults.shapes(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(HugeIcons.Settings03, null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.settings), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Icon(HugeIcons.ArrowRight01, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DrawerAction(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) {
            if (contentDescription != null) {
                this.contentDescription = contentDescription
            }
        },
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Tooltip(
            tooltip = {
                label()
            }
        ) {
            Box(
                modifier = Modifier
                    .padding(14.dp)
                    .size(20.dp),
            ) {
                icon()
            }
        }
    }
}

@Composable
private fun FolderBar(
    folders: List<Folder>,
    protectedFolderId: Uuid?,
    selectedFolderId: Uuid?,
    onSelect: (Uuid?) -> Unit,
    onCreate: () -> Unit,
    onRename: (Folder) -> Unit,
    onDelete: (Folder) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            FolderChip(
                label = stringResource(R.string.chat_page_folder_default),
                selected = selectedFolderId == null,
                onClick = { onSelect(null) },
                onLongClick = {},
            )
        }
        items(folders) { folder ->
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                FolderChip(
                    label = folder.name,
                    icon = HugeIcons.Folder01,
                    selected = selectedFolderId == folder.id,
                    onClick = { onSelect(folder.id) },
                    onLongClick = { menuExpanded = true },
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_page_rename)) },
                        leadingIcon = { Icon(HugeIcons.PencilEdit01, null) },
                        onClick = {
                            onRename(folder)
                            menuExpanded = false
                        }
                    )
                    // P2-23 (D7) - the sub-agent archive folder cannot be deleted while it
                    // holds conversations; the option is absent rather than failing on tap.
                    if (folder.id != protectedFolderId) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_page_delete)) },
                            leadingIcon = { Icon(HugeIcons.Delete01, null) },
                            onClick = {
                                onDelete(folder)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }
        item {
            FolderChip(
                label = stringResource(R.string.chat_page_folder_add),
                icon = HugeIcons.FolderAdd,
                selected = false,
                onClick = onCreate,
                onLongClick = {},
            )
        }
    }
}

@Composable
private fun FolderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    icon: ImageVector? = null,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            Color.Transparent
        },
        modifier = Modifier
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AssistantItem(
    assistant: Assistant,
    isCurrentAssistant: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isCurrentAssistant) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isCurrentAssistant) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AssistantAvatar(
                assistant = assistant,
                name = assistant.name,
                onUpdate = {},
                modifier = Modifier.size(40.dp),
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isCurrentAssistant) {
                    Text(
                        text = stringResource(R.string.assistant_page_current_assistant),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
