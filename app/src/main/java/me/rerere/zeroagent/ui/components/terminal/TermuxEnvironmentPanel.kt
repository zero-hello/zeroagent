package me.rerere.zeroagent.ui.components.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dokar.sonner.ToastType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.ai.tools.local.TermuxIntegration
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.getCurrentAssistant
import me.rerere.zeroagent.data.terminal.TermuxInstaller
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.components.ui.permission.PermissionInfo
import me.rerere.zeroagent.ui.components.ui.permission.PermissionManager
import me.rerere.zeroagent.ui.components.ui.permission.rememberPermissionState
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.context.LocalToaster
import me.rerere.zeroagent.utils.localizedErrorMessage
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext
import org.koin.compose.koinInject

/** Standard Termux setup stays user-controlled; no alternate shell or private-data replacement. */
@Composable
fun TermuxEnvironmentPanel(verifyTimeoutMs: Long) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val nav = LocalNavController.current
    val store: SettingsStore = koinInject()
    val appSettings by store.settingsFlow.collectAsStateWithLifecycle()
    val manager = remember(context) { WorkManager.getInstance(context) }
    val jobs by remember(manager) { manager.getWorkInfosForUniqueWorkFlow(TermuxInstaller.WORK) }
        .collectAsStateWithLifecycle(emptyList())
    val active = jobs.firstOrNull { !it.state.isFinished }
    val latest = active ?: jobs.maxByOrNull {
        maxOf(it.outputData.getLong("requested_at", 0L), it.progress.getLong("requested_at", 0L))
    }
    var integration by remember { mutableStateOf(TermuxIntegration.State.NOT_INSTALLED) }
    var verified by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var installRequest by remember { mutableIntStateOf(0) }
    var preparingInstall by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    fun openTermux() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.termux")
            if (intent == null) toaster.show(context.zeroAgentLocalizedContext().getString(R.string.termux_setup_open_failed), type = ToastType.Error)
            else context.startActivity(intent)
        } catch (failure: Exception) { toaster.show(context.localizedErrorMessage(failure), type = ToastType.Error) }
    }

    fun refreshState() {
        integration = TermuxIntegration.state(context)
        val changedAt = runCatching { context.packageManager.getPackageInfo("com.termux", 0).lastUpdateTime }.getOrDefault(Long.MAX_VALUE)
        verified = integration == TermuxIntegration.State.READY && TermuxIntegration.lastVerifiedOkAtMs >= changedAt
        if (!verified && TermuxIntegration.lastVerifiedOkAtMs != 0L) TermuxIntegration.clearVerified()
        refresh++
    }
    val install = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshState() }
    val allowInstall = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) installRequest++
        else toaster.show(context.zeroAgentLocalizedContext().getString(R.string.termux_setup_install_permission), type = ToastType.Error)
    }
    val permissionInfo = remember { PermissionInfo(
        permission = "com.termux.permission.RUN_COMMAND",
        displayName = { Text(stringResource(R.string.setting_termux_status_permission)) },
        usage = { Text(stringResource(R.string.termux_setup_permission_usage)) }, required = true,
    ) }
    val commandPermission = rememberPermissionState(permissionInfo)
    PermissionManager(commandPermission)
    LaunchedEffect(commandPermission.allRequiredPermissionsGranted) { refreshState() }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refreshState() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { refreshState() }
    LaunchedEffect(installRequest) {
        if (installRequest == 0 || integration != TermuxIntegration.State.NOT_INSTALLED) return@LaunchedEffect
        preparingInstall = true
        try {
            val file = TermuxInstaller.validatedFile(context)
            if (!context.packageManager.canRequestPackageInstalls()) {
                allowInstall.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            } else install.launch(TermuxInstaller.installIntent(context, file))
        } catch (cancel: CancellationException) { throw cancel
        } catch (failure: Exception) { toaster.show(context.localizedErrorMessage(failure), type = ToastType.Error)
        } finally { preparingInstall = false }
    }
    val apkPresent = remember(latest?.state, refresh) { TermuxInstaller.target(context).let { it.isFile && it.length() == TermuxInstaller.BYTES } }
    val installed = integration != TermuxIntegration.State.NOT_INSTALLED
    CardGroup(title = { Text(stringResource(R.string.termux_setup_environment)) }) {
        item(headlineContent = { Text(stringResource(R.string.setting_termux_status_app)) },
            supportingContent = { Text(stringResource(if (installed) R.string.setting_termux_status_app_installed else R.string.setting_termux_status_app_missing)) },
            onClick = {
                if (installed) openTermux()
                else if (active == null) {
                    if (apkPresent) installRequest++ else TermuxInstaller.download(context)
                }
            })
        if (!installed) item(headlineContent = { Text(stringResource(R.string.termux_setup_source_size,
            TermuxInstaller.VERSION, android.text.format.Formatter.formatFileSize(context.zeroAgentLocalizedContext(), TermuxInstaller.BYTES))) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (active != null) {
                        val percent = active.progress.getInt("percent", 0).coerceIn(0, 100)
                        Text(stringResource(R.string.termux_setup_downloading) + " · $percent%")
                        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { manager.cancelUniqueWork(TermuxInstaller.WORK) }) { Text(stringResource(R.string.local_v11_pause)) }
                    } else {
                        if (latest?.state == WorkInfo.State.FAILED) Text(context.localizedErrorMessage(raw = latest.outputData.getString("error")), color = MaterialTheme.colorScheme.error)
                        Button(enabled = !preparingInstall, onClick = {
                            if (apkPresent) installRequest++ else TermuxInstaller.download(context)
                        }) { Text(stringResource(if (apkPresent) R.string.termux_setup_install else R.string.termux_setup_download)) }
                        if (preparingInstall) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            })
        if (installed) {
            item(headlineContent = { Text(stringResource(R.string.setting_termux_status_open)) },
                supportingContent = { Text(stringResource(R.string.setting_termux_status_open_desc)) },
                onClick = ::openTermux)
            item(headlineContent = { Text(stringResource(R.string.setting_termux_status_permission)) },
                supportingContent = { Text(stringResource(if (integration == TermuxIntegration.State.READY)
                    R.string.setting_termux_status_permission_granted else R.string.setting_termux_status_permission_missing)) },
                onClick = { commandPermission.requestPermission("com.termux.permission.RUN_COMMAND") })
            item(headlineContent = { Text(stringResource(R.string.termux_setup_configure)) },
                supportingContent = { Text(stringResource(R.string.termux_setup_configure_hint)) },
                onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Termux", TERMUX_LINK_COMMAND))
                    toaster.show(context.zeroAgentLocalizedContext().getString(R.string.termux_setup_copied))
                })
            item(headlineContent = { Text(stringResource(R.string.setting_termux_status_verify)) },
                supportingContent = { Text(stringResource(if (verified) R.string.setting_termux_verify_ok else R.string.setting_termux_status_verify_unknown)) },
                trailingContent = {
                    Button(enabled = !verifying && integration == TermuxIntegration.State.READY, onClick = { scope.launch {
                        verifying = true
                        try {
                            val result = TermuxIntegration.verify(context, verifyTimeoutMs)
                            if (result == TermuxIntegration.VerifyResult.Ok) TermuxIntegration.markVerifiedOk() else TermuxIntegration.clearVerified()
                            refreshState()
                            val localized = context.zeroAgentLocalizedContext()
                            val message = when (result) {
                                TermuxIntegration.VerifyResult.Ok -> localized.getString(R.string.setting_termux_verify_ok)
                                TermuxIntegration.VerifyResult.NotInstalled -> localized.getString(R.string.setting_termux_verify_not_installed)
                                TermuxIntegration.VerifyResult.NoPermission -> localized.getString(R.string.setting_termux_verify_no_permission)
                                TermuxIntegration.VerifyResult.AllowExternalAppsMissing -> localized.getString(R.string.setting_termux_verify_allow_external_apps)
                                is TermuxIntegration.VerifyResult.UnexpectedOutput -> localized.getString(R.string.setting_termux_verify_unexpected_output)
                                is TermuxIntegration.VerifyResult.OtherError -> localized.localizedErrorMessage(raw = result.message)
                            }
                            toaster.show(message, type = if (verified) ToastType.Success else ToastType.Error)
                        } catch (cancel: CancellationException) { throw cancel
                        } catch (failure: Exception) { toaster.show(context.localizedErrorMessage(failure), type = ToastType.Error)
                        } finally { verifying = false }
                    } }) { Text(stringResource(R.string.setting_termux_status_verify)) }
                })
        }
        item(headlineContent = { Text(stringResource(R.string.termux_setup_model_tools)) },
            supportingContent = { Text(stringResource(R.string.termux_setup_model_tools_hint)) },
            onClick = if (appSettings.init) null else { { nav.navigate(Screen.AssistantLocalTool(appSettings.getCurrentAssistant().id.toString())) } })
    }
}

/** The user pastes this into standard Termux; another app cannot silently change this setting. */
private const val TERMUX_LINK_COMMAND = "mkdir -p \"\$HOME/.termux\"; touch \"\$HOME/.termux/termux.properties\"; sed -i '/^[[:space:]]*allow-external-apps[[:space:]]*=/d' \"\$HOME/.termux/termux.properties\"; printf '\\nallow-external-apps = true\\n' >> \"\$HOME/.termux/termux.properties\"; termux-reload-settings; pkg install -y tmux"
