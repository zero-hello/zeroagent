package me.rerere.zeroagent.ui.pages.developer

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.IOException
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.zeroagent.AppScope
import me.rerere.zeroagent.BuildConfig
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.ai.AILogging
import me.rerere.zeroagent.data.ai.AILoggingManager
import me.rerere.zeroagent.data.datastore.AiLogLevel
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.components.ui.Switch
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.utils.plus
import org.koin.compose.koinInject

@Composable
fun DeveloperPage() {
    var showUiTest by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (showUiTest) {
        ZeroUiTestScreen(onBack = { showUiTest = false })
        return
    }
    val store: SettingsStore = koinInject()
    val diagnostics: AILoggingManager = koinInject()
    val appScope: AppScope = koinInject()
    val settings by store.settingsFlow.collectAsStateWithLifecycle()
    val logs by diagnostics.getLogs().collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val appContext = context.applicationContext
    val options = settings.developerDisplayOptions
    val requests = remember(logs) { logs.filterIsInstance<AILogging.Generation>() }
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    var sample by remember { mutableStateOf<DeviceSnapshot?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(settings.developerMode, refresh) {
        if (settings.developerMode) {
            sample = withContext(Dispatchers.IO) { DeviceSnapshot.read(appContext) }
        }
    }

    // Keep a pending write alive if the user immediately leaves this screen.
    fun save(afterSave: () -> Unit = {}, update: (Settings) -> Settings) {
        appScope.launch {
            try {
                store.update(update)
                afterSave()
            } catch (_: IOException) {
                Toast.makeText(appContext, R.string.developer_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun copySummary(summary: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("ZeroAgent", summary)) ?: return
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(context, R.string.developer_copied, Toast.LENGTH_SHORT).show()
        }
    }

    ZeroAgentScaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.accessibility_developer_options)) }, navigationIcon = { BackButton() })
    }) { insets ->
        if (settings.developerMode) LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = insets + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup {
                    item(
                        headlineContent = { Text(stringResource(R.string.developer_mode_enabled)) },
                        supportingContent = { Text(stringResource(R.string.developer_mode_enabled_desc)) },
                        trailingContent = {
                            DeveloperSwitch(R.string.developer_mode_enabled, true) { enabled ->
                                if (!enabled) {
                                    save(afterSave = diagnostics::clearLogs) { it.copy(developerMode = false) }
                                    nav.popBackStack()
                                }
                            }
                        },
                    )
                }
            }
            item {
                CardGroup(title = { Text(stringResource(R.string.developer_request_diagnostics)) }) {
                    item(
                        headlineContent = { Text(stringResource(R.string.developer_record_requests)) },
                        supportingContent = { Text(stringResource(R.string.developer_record_requests_desc)) },
                        trailingContent = {
                            DeveloperSwitch(R.string.developer_record_requests, settings.aiLogLevel != AiLogLevel.OFF) { on ->
                                save(afterSave = { if (!on) diagnostics.clearLogs() }) {
                                    it.copy(aiLogLevel = if (on) AiLogLevel.INFO else AiLogLevel.OFF)
                                }
                            }
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.developer_show_requests)) },
                        supportingContent = {
                            Text(stringResource(R.string.developer_request_counts, requests.size, requests.count { it.stream }))
                        },
                        trailingContent = {
                            DeveloperSwitch(R.string.developer_show_requests, options.showRequests) { on ->
                                save { it.copy(developerDisplayOptions = it.developerDisplayOptions.copy(showRequests = on)) }
                            }
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.developer_only_streaming)) },
                        trailingContent = {
                            DeveloperSwitch(R.string.developer_only_streaming, options.onlyStreaming) { on ->
                                save { it.copy(developerDisplayOptions = it.developerDisplayOptions.copy(onlyStreaming = on)) }
                            }
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.developer_show_request_time)) },
                        trailingContent = {
                            DeveloperSwitch(R.string.developer_show_request_time, options.showRequestTime) { on ->
                                save { it.copy(developerDisplayOptions = it.developerDisplayOptions.copy(showRequestTime = on)) }
                            }
                        },
                    )
                    if (requests.isNotEmpty()) item(
                        onClick = diagnostics::clearLogs,
                        headlineContent = { Text(stringResource(R.string.developer_clear_requests)) },
                    )
                }
            }
            if (options.showRequests) items(requests.asReversed().filter { !options.onlyStreaming || it.stream }) { request ->
                CardGroup {
                    item(
                        headlineContent = { Text(stringResource(if (request.stream) R.string.developer_request_stream else R.string.developer_request_regular)) },
                        supportingContent = {
                            val details = stringResource(R.string.developer_request_details, request.inputMessageCount, request.availableToolCount)
                            Text(if (options.showRequestTime) "$details\n${timeFormat.format(Date(request.timestampMillis))}" else details)
                        },
                    )
                }
            }
            item {
                val snapshot = sample
                val summary = snapshot?.describe(context)
                CardGroup(title = { Text(stringResource(R.string.developer_device_info)) }) {
                    item(
                        headlineContent = { Text(stringResource(R.string.developer_device_snapshot)) },
                        supportingContent = { Text(summary ?: stringResource(R.string.developer_pending)) },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.developer_refresh_snapshot)) },
                        onClick = { sample = null; refresh++ },
                    )
                    if (summary != null) item(
                        headlineContent = { Text(stringResource(R.string.developer_copy_snapshot)) },
                        supportingContent = { Text(stringResource(R.string.developer_copy_snapshot_desc)) },
                        onClick = {
                            val counts = resources.getString(R.string.developer_request_counts, requests.size, requests.count { it.stream })
                            copySummary("$summary\n$counts")
                        },
                    )
                }
            }
            item {
                me.rerere.zeroagent.ui.designsystem.ZeroHairlineCard(
                    onClick = { showUiTest = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.zero_v6_showcase), Modifier.padding(16.dp))
                }
            }
            item {
                CardGroup(title = { Text(stringResource(R.string.developer_system_tools)) }) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_page_doctor)) },
                        onClick = { nav.navigate(Screen.SettingDoctor) },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_page_request_logs)) },
                        onClick = { nav.navigate(Screen.Log) },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.developer_android_app_settings)) },
                        onClick = {
                            try {
                                context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, R.string.developer_settings_unavailable, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeveloperSwitch(label: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val description = stringResource(label)
    Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.semantics { contentDescription = description })
}

private data class DeviceSnapshot(
    val freeMemory: Long?,
    val freeStorage: Long?,
    val networkValidated: Boolean?,
    val animationScale: Float?,
    val debuggable: Boolean,
) {
    fun describe(context: Context): String {
        val unknown = context.getString(R.string.developer_unknown)
        val memory = freeMemory?.let { Formatter.formatShortFileSize(context, it) } ?: unknown
        val storage = freeStorage?.let { Formatter.formatShortFileSize(context, it) } ?: unknown
        val network = when (networkValidated) {
            true -> context.getString(R.string.developer_network_validated)
            false -> context.getString(R.string.developer_network_not_validated)
            null -> unknown
        }
        return buildString {
            appendLine("ZeroAgent ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
            appendLine(Build.SUPPORTED_ABIS.joinToString())
            appendLine(context.getString(R.string.developer_memory_storage, memory, storage))
            appendLine(context.getString(R.string.developer_network, network))
            appendLine(context.getString(R.string.developer_animation_scale, animationScale?.let { "${it}×" } ?: unknown))
            append(context.getString(if (debuggable) R.string.developer_debuggable else R.string.developer_not_debuggable))
        }
    }

    companion object {
        fun read(context: Context): DeviceSnapshot {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val memory = activityManager?.let { manager -> ActivityManager.MemoryInfo().also(manager::getMemoryInfo).availMem }
            val storage = runCatching { StatFs(context.filesDir.absolutePath).availableBytes }.getOrNull()
            val network = runCatching {
                context.getSystemService(ConnectivityManager::class.java)?.let { manager ->
                    manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                }
            }.getOrNull()
            val scale = runCatching {
                AndroidSettings.Global.getFloat(context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE)
            }.getOrNull()
            return DeviceSnapshot(memory, storage, network, scale, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        }
    }
}
