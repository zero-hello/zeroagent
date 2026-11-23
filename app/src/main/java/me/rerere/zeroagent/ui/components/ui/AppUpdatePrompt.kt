package me.rerere.zeroagent.ui.components.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.dokar.sonner.ToastType
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.zeroagent.BuildConfig
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.context.LocalToaster
import me.rerere.zeroagent.utils.SignedUpdateInstaller
import me.rerere.zeroagent.utils.UiState
import me.rerere.zeroagent.utils.UpdateChecker
import me.rerere.zeroagent.utils.localizedErrorMessage
import org.koin.compose.koinInject

@Composable
internal fun AppUpdatePrompt(disabledUntil: Long) {
    val checker: UpdateChecker = koinInject()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val state by checker.updateState.collectAsStateWithLifecycle()
    val info = (state as? UiState.Success)?.data
    var checksEnabled by remember(disabledUntil) { mutableStateOf(System.currentTimeMillis() >= disabledUntil) }
    val preferences = remember(context) { context.getSharedPreferences("version_notice", Context.MODE_PRIVATE) }
    val navigation = LocalNavController.current
    val installedVersion = BuildConfig.VERSION_NAME
    val lastSeen = remember(preferences) { preferences.getString("seen_version", null) }
    val upgraded = remember(context, lastSeen, installedVersion) {
        if (lastSeen != null) {
            lastSeen != installedVersion
        } else {
            @Suppress("DEPRECATION")
            val installation = context.packageManager.getPackageInfo(context.packageName, 0)
            installation.lastUpdateTime > installation.firstInstallTime
        }
    }
    var showChanges by remember(upgraded) { mutableStateOf(upgraded) }
    var dismissedVersion by remember { mutableStateOf<String?>(null) }
    val transfer by checker.transfer.collectAsStateWithLifecycle()
    val downloading = transfer != null
    val withNotifications = rememberUpdateNotificationRequest()
    LaunchedEffect(disabledUntil, lifecycle, checker) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val remainingPause = (disabledUntil - System.currentTimeMillis()).coerceAtLeast(0L)
                checksEnabled = remainingPause == 0L
                delay(if (remainingPause > 0L) remainingPause else checker.refresh())
            }
        }
    }
    LaunchedEffect(preferences, lastSeen) {
        if (lastSeen == null && !upgraded) preferences.edit().putString("seen_version", installedVersion).apply()
    }
    val asset = remember(info, installedVersion) {
        info?.newerDownload(installedVersion, SignedUpdateInstaller::isCompatible)
    }
    val showUpdate = checksEnabled && info != null && dismissedVersion != info.version && asset != null
    val waitingForInitialCheck = checksEnabled && state == UiState.Loading
    if (showChanges && !showUpdate && !waitingForInitialCheck) {
        val close = {
            showChanges = false
            preferences.edit().putString("seen_version", installedVersion).apply()
        }
        ZeroAgentAlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.update_installed_version, installedVersion)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    ReleaseNotes(info?.takeIf { it.version == installedVersion }?.changelog?.ifBlank { null }
                        ?: stringResource(R.string.update_installed_changes_unavailable))
                    PreviousApkExport(showInstallNote = true, onBackup = {
                        close()
                        navigation.navigate(Screen.Backup)
                    })
                }
            },
            confirmButton = { TextButton(onClick = close) { Text(stringResource(android.R.string.ok)) } },
        )
    } else if (showUpdate && info != null && asset != null) {
        ZeroAgentAlertDialog(
            onDismissRequest = { if (!downloading) dismissedVersion = info.version },
            title = { Text(stringResource(R.string.update_card_new_version_found, info.version)) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    ReleaseNotes(info.changelog, Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()))
                    transfer?.let { UpdateDownloadProgress(it) }
                }
            },
            confirmButton = {
                TextButton(enabled = !downloading, onClick = {
                    withNotifications { scope.launch {
                        try {
                            checker.downloadUpdate(context, asset)
                            dismissedVersion = info.version
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            toaster.show(context.localizedErrorMessage(e), type = ToastType.Error)
                        }
                    } }
                }) { Text(stringResource(if (downloading) R.string.update_card_downloading else R.string.update_download_install)) }
            },
            dismissButton = { TextButton(enabled = !downloading, onClick = { dismissedVersion = info.version }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
internal fun PreviousApkExport(showInstallNote: Boolean = false, onBackup: (() -> Unit)? = null) {
    val context = LocalContext.current
    val previousVersion = remember(context) {
        context.getSharedPreferences("version_notice", Context.MODE_PRIVATE).getString("previous_version", null)
    }
    val previousApk = remember(context) { File(context.filesDir, "previous-installation.apk") }
    if (previousVersion == null || previousVersion == BuildConfig.VERSION_NAME || !previousApk.isFile) return
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = requireNotNull(context.contentResolver.openOutputStream(uri))
                    output.use { target -> previousApk.inputStream().use { source -> source.copyTo(target) } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toaster.show(context.localizedErrorMessage(e), type = ToastType.Error)
            }
        }
    }
    if (showInstallNote) Text(stringResource(R.string.update_rollback_requires_uninstall))
    if (onBackup != null) TextButton(onClick = onBackup) {
        Text(stringResource(R.string.update_open_backup))
    }
    TextButton(onClick = { export.launch("ZeroAgent-v$previousVersion.apk") }) {
        Text(stringResource(R.string.update_export_previous_apk))
    }
}
