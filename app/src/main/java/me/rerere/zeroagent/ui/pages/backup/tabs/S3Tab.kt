package me.rerere.zeroagent.ui.pages.backup.tabs

import androidx.compose.material3.ButtonDefaults

import me.rerere.zeroagent.utils.localizedErrorMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.hugeicons.stroke.Upload02
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import me.rerere.zeroagent.ui.designsystem.ZeroCapsuleButton as Button
import androidx.compose.material3.CircularWavyProgressIndicator
import me.rerere.zeroagent.ui.components.ui.ExpressiveDivider as HorizontalDivider
import androidx.compose.material3.Icon
import me.rerere.zeroagent.ui.designsystem.ZeroIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import me.rerere.zeroagent.ui.components.ui.ZeroAgentModalBottomSheet as ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import me.rerere.zeroagent.ui.designsystem.ZeroTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import me.rerere.zeroagent.ui.components.ui.Switch
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.sync.BackupItem
import me.rerere.zeroagent.data.sync.S3BackupItem
import me.rerere.zeroagent.data.sync.s3.S3Config
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.context.LocalToaster
import me.rerere.zeroagent.ui.pages.backup.BackupVM
import me.rerere.zeroagent.ui.pages.backup.components.BackupItemPicker
import me.rerere.zeroagent.utils.UiState
import me.rerere.zeroagent.utils.fileSizeToString
import me.rerere.zeroagent.utils.onError
import me.rerere.zeroagent.utils.onLoading
import me.rerere.zeroagent.utils.onSuccess
import me.rerere.zeroagent.utils.toLocalDateTime
import java.time.Instant

@Composable
fun S3Tab(
    vm: BackupVM,
    onShowRestartDialog: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s3Config = settings.s3Config
    val backupItemsState by vm.s3BackupItems.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val localizedResources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    var showBackupFiles by remember { mutableStateOf(false) }
    var restoringItemId by remember { mutableStateOf<String?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }

    fun updateS3Config(transform: S3Config.() -> S3Config) {
        vm.updateSettings { current -> current.copy(s3Config = current.s3Config.transform()) }
    }

    val lastBackupText = if (settings.backupReminderConfig.lastBackupTime == 0L) {
        stringResource(R.string.backup_page_reminder_no_record)
    } else {
        stringResource(
            R.string.backup_page_reminder_last_time,
            Instant.ofEpochMilli(settings.backupReminderConfig.lastBackupTime).toLocalDateTime()
        )
    }
    val backupFileSummary = when (val state = backupItemsState) {
        is UiState.Success -> "${stringResource(R.string.backup_page_files)}: ${state.data.size}"
        UiState.Loading -> "${stringResource(R.string.backup_page_files)}: ..."
        UiState.Idle -> "${stringResource(R.string.backup_page_files)}: -"
        is UiState.Error -> "${stringResource(R.string.backup_page_files)}: -"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackupStatusCard(
                title = stringResource(R.string.backup_page_s3_backup),
                lastBackupText = lastBackupText,
                fileSummaryText = backupFileSummary
            )

            CardGroup {
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_s3_endpoint)) },
                    supportingContent = {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = s3Config.endpoint,
                            onValueChange = { updateS3Config { copy(endpoint = it.trim()) } },
                            placeholder = { Text("https://s3.amazonaws.com") },
                            singleLine = true
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_s3_access_key_id)) },
                    supportingContent = {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = s3Config.accessKeyId,
                            onValueChange = { updateS3Config { copy(accessKeyId = it.trim()) } },
                            singleLine = true
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_s3_secret_access_key)) },
                    supportingContent = {
                        var passwordVisible by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = s3Config.secretAccessKey,
                            onValueChange = { updateS3Config { copy(secretAccessKey = it.trim()) } },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (passwordVisible) {
                                    HugeIcons.ViewOff
                                } else {
                                    HugeIcons.View
                                }
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = stringResource(if (passwordVisible) R.string.accessibility_hide_password else R.string.accessibility_show_password))
                                }
                            },
                            singleLine = true
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_s3_bucket)) },
                    supportingContent = {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = s3Config.bucket,
                            onValueChange = { updateS3Config { copy(bucket = it.trim()) } },
                            placeholder = { Text("my-bucket") },
                            singleLine = true
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_s3_path_style)) },
                    supportingContent = { Text(stringResource(R.string.backup_page_s3_path_style_desc)) },
                    trailingContent = {
                        Switch(
                            checked = s3Config.pathStyle,
                            onCheckedChange = { updateS3Config { copy(pathStyle = it) } },
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_s3_region)) },
                    supportingContent = {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = s3Config.region,
                            onValueChange = { updateS3Config { copy(region = it.trim()) } },
                            placeholder = { Text("auto") },
                            singleLine = true
                        )
                    },
                )
            }

            CardGroup {
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_backup_items)) },
                    supportingContent = {
                        BackupItemPicker(
                            selected = BackupItem.normalize(s3Config.items),
                            onToggle = { item ->
                                val current = BackupItem.normalize(s3Config.items)
                                val newItems = if (item in current) {
                                    current - item
                                } else {
                                    current + item
                                }
                                updateS3Config { copy(items = BackupItem.normalize(newItems)) }
                            },
                        )
                    },
                )
            }
        }

        HorizontalDivider()
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(
                shapes = ButtonDefaults.shapes(), onClick = {
                    scope.launch {
                        try {
                            vm.testS3()
                            toaster.show(
                                localizedResources.getString(R.string.backup_page_connection_success),
                                type = ToastType.Success
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                            toaster.show(
                                localizedResources.getString(
                                    R.string.backup_page_connection_failed,
                                    context.localizedErrorMessage(e)
                                ),
                                type = ToastType.Error
                            )
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.backup_page_test_connection))
            }
            OutlinedButton(
                shapes = ButtonDefaults.shapes(), onClick = {
                    vm.loadS3BackupFileItems()
                    showBackupFiles = true
                }
            ) {
                Text(stringResource(R.string.backup_page_restore))
            }

            Button(
                onClick = {
                    scope.launch {
                        isBackingUp = true
                        runCatching {
                            vm.backupToS3()
                            vm.loadS3BackupFileItems()
                            toaster.show(
                                localizedResources.getString(R.string.backup_page_backup_success),
                                type = ToastType.Success
                            )
                        }.onFailure {
                            it.printStackTrace()
                            toaster.show(
                                context.localizedErrorMessage(it),
                                type = ToastType.Error
                            )
                        }
                        isBackingUp = false
                    }
                },
                enabled = !isBackingUp
            ) {
                if (isBackingUp) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(HugeIcons.Upload02, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isBackingUp) {
                        stringResource(R.string.backup_page_backing_up)
                    } else {
                        stringResource(R.string.backup_page_backup_now)
                    }
                )
            }
        }
    }

    if (showBackupFiles) {
        ModalBottomSheet(
            onDismissRequest = {
                showBackupFiles = false
            },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.backup_page_s3_backup_files),
                    modifier = Modifier.fillMaxWidth()
                )
                backupItemsState.onSuccess {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(it) { item ->
                            S3BackupItemCard(
                                item = item,
                                isRestoring = restoringItemId == item.displayName,
                                onDelete = {
                                    scope.launch {
                                        runCatching {
                                            vm.deleteS3BackupFile(item)
                                            toaster.show(
                                                localizedResources.getString(R.string.backup_page_delete_success),
                                                type = ToastType.Success
                                            )
                                            vm.loadS3BackupFileItems()
                                        }.onFailure { err ->
                                            err.printStackTrace()
                                            toaster.show(
                                                localizedResources.getString(
                                                    R.string.backup_page_delete_failed,
                                                    context.localizedErrorMessage(err)
                                                ),
                                                type = ToastType.Error
                                            )
                                        }
                                    }
                                },
                                onRestore = { restoreItem ->
                                    scope.launch {
                                        restoringItemId = restoreItem.displayName
                                        runCatching {
                                            vm.restoreFromS3(item = restoreItem)
                                            toaster.show(
                                                localizedResources.getString(R.string.backup_page_restore_success),
                                                type = ToastType.Success
                                            )
                                            showBackupFiles = false
                                            onShowRestartDialog()
                                        }.onFailure { err ->
                                            err.printStackTrace()
                                            toaster.show(
                                                localizedResources.getString(
                                                    R.string.backup_page_restore_failed,
                                                    context.localizedErrorMessage(err)
                                                ),
                                                type = ToastType.Error
                                            )
                                        }
                                        restoringItemId = null
                                    }
                                },
                            )
                        }
                    }
                }.onError {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.backup_page_loading_failed, context.localizedErrorMessage(it)),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }.onLoading {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularWavyProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupStatusCard(
    title: String,
    lastBackupText: String,
    fileSummaryText: String,
) {
    CardGroup {
        item(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = lastBackupText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = fileSummaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
        )
    }
}

@Composable
private fun S3BackupItemCard(
    item: S3BackupItem,
    isRestoring: Boolean = false,
    onDelete: (S3BackupItem) -> Unit = {},
    onRestore: (S3BackupItem) -> Unit = {},
) {
    CardGroup {
        item(
            headlineContent = {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.lastModified.toLocalDateTime(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = item.size.fileSizeToString(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            shapes = ButtonDefaults.shapes(), onClick = {
                                onDelete(item)
                            },
                            enabled = !isRestoring
                        ) {
                            Text(stringResource(R.string.backup_page_delete))
                        }
                        Button(
                            onClick = {
                                onRestore(item)
                            },
                            enabled = !isRestoring
                        ) {
                            if (isRestoring) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (isRestoring) {
                                    stringResource(R.string.backup_page_restoring)
                                } else {
                                    stringResource(R.string.backup_page_restore_now)
                                }
                            )
                        }
                    }
                }
            },
        )
    }
}
