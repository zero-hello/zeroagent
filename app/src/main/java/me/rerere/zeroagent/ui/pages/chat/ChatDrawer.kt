package me.rerere.zeroagent.ui.pages.chat

import me.rerere.zeroagent.ui.components.ui.zeroAgentGlass
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import me.rerere.hugeicons.stroke.ChartColumn
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
import me.rerere.zeroagent.data.model.Assistant
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.model.Folder
import me.rerere.zeroagent.data.repository.ConversationRepository
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import me.rerere.zeroagent.ui.hooks.EditStateContent
import me.rerere.zeroagent.ui.hooks.readBooleanPreference
import me.rerere.zeroagent.ui.hooks.rememberIsPlayStoreVersion
import me.rerere.zeroagent.ui.hooks.useEditState
import me.rerere.zeroagent.ui.modifier.onClick
import me.rerere.zeroagent.utils.navigateToChatPage
import me.rerere.zeroagent.utils.toDp
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun ChatDrawerContent(
    navController: Navigator,
    vm: ChatVM,
    settings: Settings,
    current: Conversation,
    hazeState: HazeState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val isPlayStore = rememberIsPlayStoreVersion()
    val repo = koinInject<ConversationRepository>()

    val activity = context as ComponentActivity
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = activity)

    val conversations = drawerVm.conversations.collectAsLazyPagingItems()
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

    val conversationJobs by vm.conversationJobs.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
    )

    // 昵称编辑状态
    val nicknameEditState = useEditState<String> { newNickname ->
        vm.updateSettings(
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

    // 底部功能区浮在对话列表之上：对话列表作为玻璃来源，底部卡片折射列表内容。
    val drawerHazeState = rememberHazeState()
    var bottomBarHeightPx by remember { mutableStateOf(0) }
    val bottomBarHeight = with(LocalDensity.current) { bottomBarHeightPx.toDp() }
    // 上方功能区固定悬浮在顶部，对话列表从其下方滚过；用实测高度给列表留出顶部空间。
    var topAreaHeightPx by remember { mutableStateOf(0) }
    val topAreaHeight = with(LocalDensity.current) { topAreaHeightPx.toDp() }

    // 整块抽屉做成磨砂：面板半透明，背后的聊天页被均匀虚化；上下的通透玻璃卡片与它拉开层次。
    val drawerWidth = (LocalConfiguration.current.screenWidthDp.dp * .84f).coerceAtMost(360.dp)
    val drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    // 上方功能区：玻璃卡片，固定悬浮在抽屉顶部；对话列表从其下方滚过并被折射虚化。
    val topBarShape = RoundedCornerShape(24.dp)
    val drawerTopArea: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZeroAgentWorkspaceHeader()

            if (updateChecksEnabled && !isPlayStore) {
                UpdateCard(vm)
            }

            BackupReminderCard(
                settings = settings,
                onClick = { navController.navigate(Screen.Backup) },
            )

            // 上方功能区：玻璃卡片（无边框；阴影 + 玻璃高光做立体感，与底部功能区一致）。
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .zeroAgentGlass(drawerHazeState, radius = 24),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (settings.displaySetting.liquidGlassEnabled) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
            // 用户头像和昵称自定义区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UIAvatar(
                    name = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                    value = settings.displaySetting.userAvatar,
                    onUpdate = { newAvatar ->
                        vm.updateSettings(
                            settings.copy(
                                displaySetting = settings.displaySetting.copy(
                                    userAvatar = newAvatar
                                )
                            )
                        )
                    },
                    modifier = Modifier.size(36.dp),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                nicknameEditState.open(settings.displaySetting.userNickname)
                            }
                        )

                        Icon(
                            imageVector = HugeIcons.PencilEdit01,
                            contentDescription = stringResource(R.string.accessibility_edit_nickname),
                            modifier = Modifier
                                .onClick {
                                    nicknameEditState.open(settings.displaySetting.userNickname)
                                }
                                .size(LocalTextStyle.current.fontSize.toDp())
                        )
                    }
                    Greeting(
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            DrawerActions(navController = navController)

            FolderBar(
                protectedFolderId = protectedFolderId,
                folders = folders,
                selectedFolderId = selectedFolderId,
                onSelect = { drawerVm.selectFolder(it) },
                onCreate = { showCreateFolderDialog = true },
                onRename = { folderToRename = it },
                onDelete = { folderToDelete = it },
            )
                }
            }
        }
    }

    ModalDrawerSheet(
        modifier = Modifier
            .width(drawerWidth)
            .clip(drawerShape),
        drawerShape = drawerShape,
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        drawerTonalElevation = 0.dp,
    ) {
        // Keep a consistent inset for conversation rows and the fixed header/footer.
        Box(modifier = Modifier.fillMaxSize()) {
            ConversationList(
                current = current,
                conversations = conversations,
                conversationJobs = conversationJobs.keys,
                listState = conversationListState,
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = topAreaHeight + 12.dp, bottom = bottomBarHeight + 12.dp,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = drawerHazeState)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            onClick = {
                    navigateToChatPage(navController, it.id)
                },
                onRename = {
                    drawerVm.requestRenameConversation(it)
                },
                onDelete = {
                    scope.launch {
                        vm.deleteConversation(it).join()
                        conversations.refresh()
                        if (it.id == current.id) {
                            navigateToChatPage(navController)
                        }
                    }
                },
                onPin = {
                    vm.updatePinnedStatus(it)
                },
                onMoveToAssistant = {
                    conversationToMove = it
                    showMoveToAssistantSheet = true
                },
                onMoveToFolder = {
                    conversationToMoveFolder = it
                    showMoveToFolderSheet = true
                }
            )

                // 上方功能区：固定悬浮在抽屉顶部，对话列表从其下方滚过时被玻璃折射虚化。
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { topAreaHeightPx = it.height },
                ) {
                    drawerTopArea()
                }

                // Measure the footer including its outer margin so the final chat stays visible.
                val bottomBarShape = RoundedCornerShape(24.dp)
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { bottomBarHeightPx = it.height }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .zeroAgentGlass(drawerHazeState, radius = 24),
                    shape = bottomBarShape,
                    color = if (settings.displaySetting.liquidGlassEnabled) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ZeroAgent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = { navController.navigate(Screen.SettingModels) }) {
                    Icon(HugeIcons.Settings03, stringResource(R.string.zeroagent11_model_parameters))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                androidx.compose.material3.FilledTonalIconButton(onClick = { navigateToChatPage(navController) }) { Icon(HugeIcons.Message01, stringResource(R.string.zeroagent_drawer_chats)) }
                androidx.compose.material3.FilledTonalIconButton(onClick = { navController.navigate(Screen.TaskCenter) }) { Icon(HugeIcons.TransactionHistory, stringResource(R.string.zeroagent_drawer_tasks)) }
                androidx.compose.material3.FilledTonalIconButton(onClick = { navController.navigate(Screen.Setting) }) { Icon(HugeIcons.Settings03, stringResource(R.string.settings)) }
            }
                    }
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
                    onClick = {
                        nicknameEditState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
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
                    onClick = {
                        drawerVm.createFolder(name, archive)
                        showCreateFolderDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.chat_page_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
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
                    onClick = {
                        drawerVm.renameFolder(folder.id, name, archive)
                        folderToRename = null
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.chat_page_save)) }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
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
                        onClick = {
                            vm.generateTitle(conversation, true)
                            drawerVm.dismissRenameConversation()
                        }
                    ) {
                        Text(stringResource(R.string.chat_page_regenerate_title))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        drawerVm.renameConversation(conversation.id, title)
                    },
                    enabled = title.isNotBlank()
                ) { Text(stringResource(R.string.chat_page_save)) }
            },
            dismissButton = {
                TextButton(onClick = { drawerVm.dismissRenameConversation() }) {
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
                    onClick = {
                        scope.launch {
                            when (drawerVm.deleteFolder(folder.id)) {
                                FolderDeleteOutcome.Deleted -> {
                                    folderToDelete = null
                                    conversations.refresh()
                                }
                                FolderDeleteOutcome.Generating ->
                                    toaster.show(context.getString(R.string.chat_page_delete_folder_generating), type = ToastType.Warning)
                                FolderDeleteOutcome.Protected ->
                                    toaster.show(context.getString(R.string.chat_page_delete_folder_protected), type = ToastType.Warning)
                            }
                        }
                    }
                ) { Text(stringResource(R.string.chat_page_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
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
                                    vm.moveConversationToAssistant(conversation, assistant.id)
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // 搜索入口
        Surface(
            onClick = { navController.navigate(Screen.MessageSearch) },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            shape = MaterialTheme.shapes.medium,
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Search01,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.chat_page_search_chats),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // 历史记录入口
        Surface(
            onClick = { navController.navigate(Screen.History) },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            shape = MaterialTheme.shapes.medium,
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.TransactionHistory,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.chat_page_history),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
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
                    .padding(10.dp)
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
