package me.rerere.zeroagent.ui.pages.setting

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.dokar.sonner.ToastType
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.zeroagent.R
import me.rerere.zeroagent.utils.localizedErrorMessage
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext
import me.rerere.ai.provider.ModelAbility
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.data.datastore.getCurrentAssistant
import me.rerere.zeroagent.data.datastore.getCurrentChatModel
import me.rerere.zeroagent.data.model.hasPhoneControlTools
import me.rerere.zeroagent.data.model.withPhoneControlTools
import org.koin.androidx.compose.koinViewModel
import me.rerere.zeroagent.data.ai.tools.local.AccessibilityServiceHandle
import me.rerere.zeroagent.data.ai.tools.local.PermissionHelper
import me.rerere.zeroagent.data.permissions.KeepAliveVendor
import me.rerere.zeroagent.data.permissions.keepAliveVendorOf
import me.rerere.zeroagent.service.ActionLogEntry
import me.rerere.zeroagent.service.ZeroAgentAccessibilityService
import me.rerere.zeroagent.shizuku.ACCESSIBILITY_REPAIR_TIMEOUT_MS
import me.rerere.zeroagent.shizuku.ShizukuManager
import me.rerere.zeroagent.shizuku.ShizukuStatus
import me.rerere.zeroagent.shizuku.buildAccessibilityRepairCommand
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.components.ui.SettingsAction
import me.rerere.zeroagent.ui.components.ui.SettingsActionCard
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Tools
import me.rerere.hugeicons.stroke.Image02
import me.rerere.zeroagent.ui.context.LocalToaster
import me.rerere.zeroagent.ui.theme.CustomColors
import java.io.File
import java.io.FileOutputStream

/**
 * Settings page for the screen-automation AccessibilityService.
 * Shows live running status, last 50 actions, deep-link to system settings, and a
 * diagnostic-screenshot button so the user can verify capture works without going through
 * the LLM.
 */
@Composable
fun SettingAccessibilityPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val assistant = settings.getCurrentAssistant()
    val model = settings.getCurrentChatModel()
    val navController = LocalNavController.current
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // The live service singleton is absent for a moment after every process restart, and a
    // `remember` whose key never changes would latch that first miss for the whole visit: the
    // page would then say "Not running" while the system switch is on and the tools still work.
    // So the three facts that make up the status are kept in state, re-read on resume and on a
    // light tick, and "is it enabled at all" falls back to the system setting - the page then
    // corrects itself within a second instead of lying until it is reopened.
    var liveService by remember { mutableStateOf(ZeroAgentAccessibilityService.instance) }
    var enabledInSettings by remember {
        mutableStateOf(AccessibilityServiceHandle.isEnabledInSettings(context))
    }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var repairing by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    // OEM power-management state; re-read on resume because the user grants it on a system page.
    var ignoresBatteryOpt by remember {
        mutableStateOf(PermissionHelper.ignoresBatteryOptimizations(context))
    }

    // Pull StateFlows from the live service if present; otherwise show empty defaults.
    val idleRunningFlow = remember { MutableStateFlow(false) }
    val idleActionsFlow = remember { MutableStateFlow<List<ActionLogEntry>>(emptyList()) }
    val runningFlow: StateFlow<Boolean> = liveService?.running ?: idleRunningFlow
    val running by runningFlow.collectAsStateWithLifecycle()

    val actionsFlow: StateFlow<List<ActionLogEntry>> = liveService?.lastActions ?: idleActionsFlow
    val actions by actionsFlow.collectAsStateWithLifecycle()

    val repairShizukuNotInstalled =
        stringResource(R.string.setting_page_accessibility_repair_shizuku_not_installed)
    val repairShizukuNotRunning =
        stringResource(R.string.setting_page_accessibility_repair_shizuku_not_running)
    val repairShizukuPermissionDenied =
        stringResource(R.string.setting_page_accessibility_repair_shizuku_permission_denied)

    // Re-check on resume so the rows update immediately after the user returns from a system
    // settings deep-link.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                liveService = ZeroAgentAccessibilityService.instance
                enabledInSettings = AccessibilityServiceHandle.isEnabledInSettings(context)
                overlayGranted = Settings.canDrawOverlays(context)
                ignoresBatteryOpt = PermissionHelper.ignoresBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ...and while the page is on screen, so a service that attaches a beat after the first
    // composition still flips the status without the user having to reopen the page.
    LaunchedEffect(Unit) {
        while (true) {
            val svc = ZeroAgentAccessibilityService.instance
            if (svc != liveService) liveService = svc
            if (svc == null) {
                val enabled = AccessibilityServiceHandle.isEnabledInSettings(context)
                if (enabled != enabledInSettings) enabledInSettings = enabled
            }
            delay(1_000)
        }
    }

    // Vendor-specific wording for the keep-alive card (the menu path differs per ROM). Kept as
    // a plain mapping so the vendor enum, not the composable, decides.
    val keepAliveVendor = remember { keepAliveVendorOf(Build.MANUFACTURER, Build.BRAND) }
    val keepAliveTitleRes = when (keepAliveVendor) {
        KeepAliveVendor.VIVO -> R.string.setting_page_accessibility_keepalive_title_vivo
        KeepAliveVendor.XIAOMI -> R.string.setting_page_accessibility_keepalive_title_xiaomi
        KeepAliveVendor.HUAWEI -> R.string.setting_page_accessibility_keepalive_title_huawei
        KeepAliveVendor.OPPO -> R.string.setting_page_accessibility_keepalive_title_oppo
        KeepAliveVendor.GENERIC -> R.string.setting_page_accessibility_keepalive_title_generic
    }
    val keepAliveBodyRes = when (keepAliveVendor) {
        KeepAliveVendor.VIVO -> R.string.setting_page_accessibility_keepalive_body_vivo
        KeepAliveVendor.XIAOMI -> R.string.setting_page_accessibility_keepalive_body_xiaomi
        KeepAliveVendor.HUAWEI -> R.string.setting_page_accessibility_keepalive_body_huawei
        KeepAliveVendor.OPPO -> R.string.setting_page_accessibility_keepalive_body_oppo
        KeepAliveVendor.GENERIC -> R.string.setting_page_accessibility_keepalive_body_generic
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_accessibility)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsActionCard(
                title = stringResource(R.string.zeroagent_phone_control),
                description = listOf(
                    stringResource(if (assistant.hasPhoneControlTools())
                        R.string.zeroagent_phone_tools_on else R.string.zeroagent_phone_tools_off),
                    stringResource(R.string.zeroagent_phone_model) + ": " + stringResource(when {
                        model == null -> R.string.zeroagent_phone_model_missing
                        ModelAbility.TOOL in model.abilities -> R.string.zeroagent_phone_model_ready
                        else -> R.string.zeroagent_phone_model_no_tools
                    }),
                    stringResource(R.string.zeroagent_phone_manage_hint, assistant.disabledLocalTools.size),
                ).joinToString("\n\n"),
                icon = HugeIcons.SmartPhone01,
                actions = buildList {
                    if (!assistant.hasPhoneControlTools() || "agent-core" !in assistant.enabledSkills) {
                        add(SettingsAction(
                            label = stringResource(R.string.zeroagent_phone_enable),
                            enabled = !settings.init,
                            onClick = {
                                val id = assistant.id
                                vm.updateSettings { current ->
                                    current.copy(assistants = current.assistants.map {
                                        if (it.id == id) it.withPhoneControlTools() else it
                                    })
                                }
                            },
                        ))
                    }
                    add(SettingsAction(
                        label = stringResource(R.string.zeroagent_phone_manage),
                        onClick = { navController.navigate(Screen.AssistantLocalTool(assistant.id.toString())) },
                    ))
                },
            )
            SettingsActionCard(
                title = stringResource(R.string.setting_page_accessibility_status_section),
                description = buildList {
                    add(stringResource(when {
                        running -> R.string.setting_page_accessibility_status_running
                        enabledInSettings -> R.string.setting_page_accessibility_status_enabled_not_connected
                        else -> R.string.setting_page_accessibility_status_not_running
                    }))
                    if (!running) {
                        add(stringResource(if (enabledInSettings)
                            R.string.setting_page_accessibility_status_enabled_help
                        else R.string.setting_page_accessibility_status_help))
                    }
                    add(stringResource(R.string.setting_page_accessibility_repair_desc))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(stringResource(R.string.setting_page_accessibility_restricted_help))
                    }
                }.joinToString("\n\n"),
                icon = HugeIcons.Settings03,
                announceChanges = true,
                actions = buildList {
                    add(SettingsAction(
                        label = stringResource(R.string.setting_page_accessibility_open_settings),
                        onClick = { context.startActivity(PermissionHelper.accessibilitySettingsIntent()) },
                    ))
                    add(SettingsAction(
                        label = stringResource(if (repairing)
                            R.string.accessibility_repair_running else R.string.setting_page_accessibility_repair),
                        enabled = !repairing,
                        onClick = {
                            scope.launch {
                                repairing = true
                                try {
                                    val needShizuku = when (ShizukuManager.status(context)) {
                                        ShizukuStatus.READY -> null
                                        ShizukuStatus.NOT_INSTALLED -> repairShizukuNotInstalled
                                        ShizukuStatus.NOT_RUNNING -> repairShizukuNotRunning
                                        ShizukuStatus.PERMISSION_DENIED -> repairShizukuPermissionDenied
                                    }
                                    if (needShizuku != null) {
                                        toaster.show(needShizuku, type = ToastType.Error)
                                        return@launch
                                    }
                                    val component = ComponentName(
                                        context, ZeroAgentAccessibilityService::class.java,
                                    ).flattenToString()
                                    val result = ShizukuManager.exec(
                                        context,
                                        buildAccessibilityRepairCommand(component),
                                        ACCESSIBILITY_REPAIR_TIMEOUT_MS,
                                    )
                                    val repaired = AccessibilityServiceHandle.isEnabledInSettings(context)
                                    enabledInSettings = repaired
                                    liveService = ZeroAgentAccessibilityService.instance
                                    if (repaired) {
                                        toaster.show(context.zeroAgentLocalizedContext().getString(
                                            R.string.setting_page_accessibility_repair_ok_toast))
                                    } else {
                                        toaster.show(
                                            context.zeroAgentLocalizedContext().getString(
                                                R.string.setting_page_accessibility_repair_fail_toast,
                                                context.localizedErrorMessage(raw = result.toString())),
                                            type = ToastType.Error,
                                        )
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    toaster.show(context.localizedErrorMessage(error), type = ToastType.Error)
                                } finally {
                                    repairing = false
                                }
                            }
                        },
                    ))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(SettingsAction(
                            label = stringResource(R.string.setting_page_accessibility_open_app_info),
                            onClick = {
                                context.startActivity(Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + context.packageName),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            },
                        ))
                    }
                },
            )
            SettingsActionCard(
                title = stringResource(keepAliveTitleRes),
                description = listOf(
                    stringResource(keepAliveBodyRes),
                    stringResource(if (ignoresBatteryOpt)
                        R.string.setting_page_accessibility_keepalive_battery_ok
                    else R.string.setting_page_accessibility_keepalive_battery_needed),
                ).joinToString("\n\n"),
                icon = HugeIcons.Tools,
                actions = listOf(
                    SettingsAction(
                        label = stringResource(R.string.accessibility_battery_manage),
                        onClick = {
                            val intent = if (ignoresBatteryOpt) PermissionHelper.batteryOptimizationsListIntent()
                                else PermissionHelper.requestIgnoreBatteryOptimizationsIntent(context)
                            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                    ),
                    SettingsAction(
                        label = stringResource(R.string.setting_page_accessibility_keepalive_open_app_info),
                        onClick = {
                            context.startActivity(Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + context.packageName),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                    ),
                ),
            )
            SettingsActionCard(
                title = stringResource(R.string.setting_page_accessibility_overlay_section),
                description = listOf(
                    stringResource(if (overlayGranted)
                        R.string.setting_page_accessibility_overlay_granted
                    else R.string.setting_page_accessibility_overlay_not_granted),
                    stringResource(R.string.setting_page_accessibility_overlay_help),
                ).joinToString("\n\n"),
                icon = HugeIcons.SmartPhone01,
                actions = listOf(SettingsAction(
                    label = stringResource(R.string.accessibility_overlay_manage),
                    onClick = {
                        context.startActivity(Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.packageName),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                )),
            )
            SettingsActionCard(
                title = stringResource(R.string.setting_page_accessibility_diagnostics_section),
                description = stringResource(R.string.accessibility_diagnostics_description),
                icon = HugeIcons.Image02,
                actions = listOf(SettingsAction(
                    label = stringResource(if (capturing)
                        R.string.accessibility_capture_running else R.string.setting_page_accessibility_capture_diag),
                    enabled = !capturing,
                    onClick = {
                        scope.launch {
                            capturing = true
                            try {
                                val live = ZeroAgentAccessibilityService.instance
                                if (live == null) {
                                    toaster.show(
                                        context.zeroAgentLocalizedContext().getString(
                                            R.string.setting_page_accessibility_capture_fail_toast,
                                            context.zeroAgentLocalizedContext().getString(R.string.accessibility_service_inactive)),
                                        type = ToastType.Error,
                                    )
                                    return@launch
                                }
                                when (val result = live.captureScreenshot(0)) {
                                    is ZeroAgentAccessibilityService.ScreenshotOutcome.Success -> {
                                        try {
                                            val file = withContext(Dispatchers.IO) {
                                                val cacheDir = File(context.cacheDir, "screenshots").apply { mkdirs() }
                                                File(cacheDir, "diag-" + System.currentTimeMillis() + ".png").also { file ->
                                                    FileOutputStream(file).use { output ->
                                                        check(result.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                                                    }
                                                }
                                            }
                                            toaster.show(context.zeroAgentLocalizedContext().getString(
                                                R.string.setting_page_accessibility_capture_ok_toast, file.name))
                                        } finally {
                                            result.bitmap.recycle()
                                        }
                                    }
                                    is ZeroAgentAccessibilityService.ScreenshotOutcome.Failure -> {
                                        toaster.show(
                                            context.zeroAgentLocalizedContext().getString(
                                                R.string.setting_page_accessibility_capture_fail_toast,
                                                context.localizedErrorMessage(raw = result.reason)),
                                            type = ToastType.Error,
                                        )
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                toaster.show(context.localizedErrorMessage(error), type = ToastType.Error)
                            } finally {
                                capturing = false
                            }
                        }
                    },
                )),
            )

            // Recent actions card
            Text(
                text = stringResource(R.string.setting_page_accessibility_recent_section),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
            )
            if (actions.isEmpty()) {
                CardGroup {
                    item(
                        headlineContent = {
                            Text(stringResource(R.string.setting_page_accessibility_no_actions))
                        }
                    )
                }
            } else {
                CardGroup {
                    actions.asReversed().forEach { entry ->
                        item(
                            headlineContent = { Text("${stringResource(accessibilityActionResource(entry.type))}: ${entry.paramsSummary}") },
                            supportingContent = {
                                Text(formatRelativeTime(System.currentTimeMillis() - entry.timestampMs))
                            },
                            trailingContent = {
                                Text(
                                    text = stringResource(if (entry.success) R.string.accessibility_action_ok else R.string.accessibility_action_failed),
                                    color = if (entry.success)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun formatRelativeTime(deltaMs: Long): String {
    val s = deltaMs / 1000
    return when {
        s < 60 -> stringResource(R.string.setting_page_accessibility_action_ago_seconds, s.toInt().coerceAtLeast(0))
        s < 3600 -> stringResource(R.string.setting_page_accessibility_action_ago_minutes, (s / 60).toInt())
        else -> stringResource(R.string.setting_page_accessibility_action_ago_hours, (s / 3600).toInt())
    }
}

internal fun accessibilityActionResource(type: String): Int = when (type) {
    "tap" -> R.string.accessibility_action_tap
    "long_press" -> R.string.accessibility_action_long_press
    "swipe" -> R.string.accessibility_action_swipe
    "scroll" -> R.string.accessibility_action_scroll
    "find_node" -> R.string.accessibility_action_find_node
    "click_node" -> R.string.accessibility_action_click_node
    "set_text" -> R.string.accessibility_action_set_text
    "take_screenshot" -> R.string.accessibility_action_screenshot
    "read_window_tree" -> R.string.accessibility_action_read_window
    "global_action" -> R.string.accessibility_action_system
    else -> R.string.accessibility_action_other
}
