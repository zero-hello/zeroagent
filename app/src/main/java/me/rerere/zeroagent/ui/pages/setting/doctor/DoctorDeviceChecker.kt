package me.rerere.zeroagent.ui.pages.setting.doctor

import android.Manifest
import android.content.Context
import android.os.Build
import me.rerere.zeroagent.data.ai.tools.LocalToolOption
import me.rerere.zeroagent.data.ai.tools.local.AccessibilityServiceHandle
import me.rerere.zeroagent.data.ai.tools.local.NotificationListenerHandle
import me.rerere.zeroagent.data.ai.tools.local.PermissionHelper
import me.rerere.zeroagent.shizuku.ShizukuManager
import me.rerere.zeroagent.shizuku.ShizukuStatus
import me.rerere.zeroagent.R
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext

/** Friendly name for the row's "needed by:" subtitle. */
private fun LocalToolOption.shortName(ctx: Context): String = when (this) {
    LocalToolOption.Location -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_location)
    LocalToolOption.WifiInfo -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_wifi)
    LocalToolOption.NotificationListener -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_notif_listener)
    LocalToolOption.ScreenAutomation -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_screen_automation)
    LocalToolOption.Termux -> ctx.zeroAgentLocalizedContext().getString(R.string.embedded_terminal_title)
    LocalToolOption.SpeechToText -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_stt)
    LocalToolOption.Ssh -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_ssh)
    LocalToolOption.CronJobs -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_cron)
    LocalToolOption.Workflows -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_workflows)
    LocalToolOption.Notification -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_notification)
    LocalToolOption.Files -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_files)
    LocalToolOption.Browser -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_browser)
    LocalToolOption.SmsSend -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_sms_send)
    LocalToolOption.Wallpaper -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_wallpaper)
    LocalToolOption.Keystore -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_keystore)
    LocalToolOption.Nfc -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_nfc)
    LocalToolOption.ExternalStorage -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_ext_storage)
    LocalToolOption.Archive -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_archive)
    LocalToolOption.Shizuku -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_shizuku)
    LocalToolOption.AppLauncher -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_app_launcher)
    LocalToolOption.KeyboardControl -> ctx.zeroAgentLocalizedContext().getString(R.string.tool_name_keyboard)
    else -> this::class.simpleName ?: "?"
}

internal class DoctorDeviceChecker(context: Context) : DoctorCheckResources(context) {
    // ----- Permissions ----------------------------------------------------------------

    fun permissionChecks(enabled: Set<LocalToolOption>): List<DoctorCheck> = buildList {
        add(
            capabilityRow(
                id = "perm.notifications",
                category = DoctorCategory.Permissions,
                label = text(R.string.doctor_check_post_notifications),
                cap = DoctorCapability.Notifications,
                enabled = enabled,
                granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    PermissionHelper.hasRuntime(context, listOf(Manifest.permission.POST_NOTIFICATIONS)),
                grantedDetail = text(R.string.doctor_detail_granted),
                missingDetail = text(R.string.doctor_detail_perm_notifications_missing),
                fix = FixAction.OpenAppRoute(text(R.string.doctor_fix_open_permissions), AppRouteKey.SettingPermissions),
            )
        )
        add(
            capabilityRow(
                id = "perm.location",
                category = DoctorCategory.Permissions,
                label = text(R.string.doctor_check_fine_location),
                cap = DoctorCapability.FineLocation,
                enabled = enabled,
                granted = PermissionHelper.hasRuntime(context, listOf(Manifest.permission.ACCESS_FINE_LOCATION)),
                grantedDetail = text(R.string.doctor_detail_granted),
                missingDetail = text(R.string.doctor_detail_perm_location_missing),
                fix = FixAction.OpenAppRoute(text(R.string.doctor_fix_open_permissions), AppRouteKey.SettingPermissions),
            )
        )
        add(
            capabilityRow(
                id = "perm.battery_opt",
                category = DoctorCategory.Permissions,
                label = text(R.string.doctor_check_battery_whitelist),
                cap = DoctorCapability.BatteryWhitelist,
                enabled = enabled,
                granted = PermissionHelper.ignoresBatteryOptimizations(context),
                grantedDetail = text(R.string.doctor_detail_perm_battery_granted),
                missingDetail = text(R.string.doctor_detail_perm_battery_missing),
                fix = FixAction.OpenIntent(
                    label = text(R.string.doctor_fix_request_whitelist),
                    intent = PermissionHelper.requestIgnoreBatteryOptimizationsIntent(context),
                ),
            )
        )
        add(
            capabilityRow(
                id = "perm.notification_listener",
                category = DoctorCategory.Permissions,
                label = text(R.string.doctor_check_notif_listener_access),
                cap = DoctorCapability.NotificationListener,
                enabled = enabled,
                granted = PermissionHelper.hasNotificationListener(context),
                grantedDetail = text(R.string.doctor_detail_perm_notifications_granted),
                missingDetail = text(R.string.doctor_detail_perm_notifications_missing),
                fix = FixAction.OpenIntent(
                    label = text(R.string.doctor_fix_open_settings),
                    intent = PermissionHelper.notificationListenerSettingsIntent(),
                ),
            )
        )
        add(
            capabilityRow(
                id = "perm.accessibility",
                category = DoctorCategory.Permissions,
                label = text(R.string.doctor_check_accessibility),
                cap = DoctorCapability.Accessibility,
                enabled = enabled,
                granted = PermissionHelper.hasAccessibilityService(context),
                grantedDetail = text(R.string.doctor_detail_perm_accessibility_granted),
                missingDetail = text(R.string.doctor_detail_perm_accessibility_missing),
                fix = FixAction.OpenIntent(
                    label = text(R.string.doctor_fix_open_settings),
                    intent = PermissionHelper.accessibilitySettingsIntent(),
                ),
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(
                capabilityRow(
                    id = "perm.all_files",
                    category = DoctorCategory.Permissions,
                    label = text(R.string.doctor_check_all_files),
                    cap = DoctorCapability.AllFiles,
                    enabled = enabled,
                    granted = PermissionHelper.hasAllFilesAccess(context),
                    grantedDetail = text(R.string.doctor_detail_perm_files_granted),
                    missingDetail = text(R.string.doctor_detail_perm_files_missing),
                    fix = FixAction.OpenIntent(
                        label = text(R.string.doctor_fix_open_settings),
                        intent = PermissionHelper.allFilesAccessIntent(context),
                    ),
                )
            )
        }
        // Phase 25 — SEND_SMS runtime permission row for the send_sms tool.
        add(
            capabilityRow(
                id = "perm.send_sms",
                category = DoctorCategory.Permissions,
                label = text(R.string.doctor_check_send_sms),
                cap = DoctorCapability.SendSms,
                enabled = enabled,
                granted = PermissionHelper.hasRuntime(context, listOf(Manifest.permission.SEND_SMS)),
                grantedDetail = text(R.string.doctor_detail_granted),
                missingDetail = text(R.string.doctor_detail_perm_sms_missing),
                fix = FixAction.OpenAppRoute(text(R.string.doctor_fix_open_permissions), AppRouteKey.SettingPermissions),
            )
        )
        // Previously-unchecked permissions, now covered. Each is tool-aware: it only WARNs when
        // the feature that needs it is enabled, so the opt-in philosophy holds (a denied perm for
        // a disabled tool stays INFO). This is what fixes the "Doctor said all-clear while
        // Display-over-other-apps etc. were ungranted" report.
        add(
            capabilityRow(
                id = "perm.overlay",
                category = DoctorCategory.Permissions,
                label = text(R.string.doctor_check_overlay),
                cap = DoctorCapability.Overlay,
                enabled = enabled,
                granted = android.provider.Settings.canDrawOverlays(context),
                grantedDetail = text(R.string.doctor_detail_granted),
                missingDetail = text(R.string.doctor_detail_perm_overlay_missing),
                fix = FixAction.OpenAppRoute(text(R.string.doctor_fix_open_permissions), AppRouteKey.SettingPermissions),
            )
        )
        add(
            capabilityRow(
                id = "perm.write_settings",
                category = DoctorCategory.Permissions,
                label = text(R.string.doctor_check_write_settings),
                cap = DoctorCapability.WriteSettings,
                enabled = enabled,
                granted = PermissionHelper.hasWriteSettings(context),
                grantedDetail = text(R.string.doctor_detail_granted),
                missingDetail = text(R.string.doctor_detail_perm_brightness_missing),
                fix = FixAction.OpenAppRoute(text(R.string.doctor_fix_open_permissions), AppRouteKey.SettingPermissions),
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                capabilityRow(
                    id = "perm.bluetooth_connect",
                    category = DoctorCategory.Permissions,
                    label = text(R.string.doctor_check_bluetooth),
                    cap = DoctorCapability.BluetoothConnect,
                    enabled = enabled,
                    granted = PermissionHelper.hasRuntime(context, listOf(Manifest.permission.BLUETOOTH_CONNECT)),
                    grantedDetail = text(R.string.doctor_detail_granted),
                    missingDetail = text(R.string.doctor_detail_perm_bluetooth_missing),
                    fix = FixAction.OpenAppRoute(text(R.string.doctor_fix_open_permissions), AppRouteKey.SettingPermissions),
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                capabilityRow(
                    id = "perm.nearby_wifi",
                    category = DoctorCategory.Permissions,
                    label = text(R.string.doctor_check_nearby_wifi),
                    cap = DoctorCapability.NearbyWifi,
                    enabled = enabled,
                    granted = PermissionHelper.hasRuntime(context, listOf(Manifest.permission.NEARBY_WIFI_DEVICES)),
                    grantedDetail = text(R.string.doctor_detail_granted),
                    missingDetail = text(R.string.doctor_detail_perm_wifi_missing),
                    fix = FixAction.OpenAppRoute(text(R.string.doctor_fix_open_permissions), AppRouteKey.SettingPermissions),
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(
                capabilityRow(
                    id = "perm.background_location",
                    category = DoctorCategory.Permissions,
                    label = text(R.string.doctor_check_bg_location),
                    cap = DoctorCapability.BackgroundLocation,
                    enabled = enabled,
                    granted = PermissionHelper.hasRuntime(context, listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)),
                    grantedDetail = text(R.string.doctor_detail_granted),
                    missingDetail = text(R.string.doctor_detail_perm_geofence_missing),
                    fix = FixAction.OpenAppRoute(text(R.string.doctor_fix_open_permissions), AppRouteKey.SettingPermissions),
                )
            )
        }
        // Phase 25 — NFC combined hardware + system-toggle row. Tri-state: no hardware
        // (INFO, no fix), hardware present but disabled (WARN, open NFC settings), on (OK).
        run {
            val adapter = android.nfc.NfcAdapter.getDefaultAdapter(context)
            val nfcNeeders = requirersOf(DoctorCapability.Nfc, enabled)
            when {
                adapter == null -> add(
                    DoctorCheck(
                        id = "perm.nfc_enabled",
                        category = DoctorCategory.Permissions,
                        label = text(R.string.doctor_check_nfc),
                        detail = text(R.string.doctor_detail_nfc_no_hardware),
                        severity = Severity.INFO,
                    )
                )
                !adapter.isEnabled -> add(
                    DoctorCheck(
                        id = "perm.nfc_enabled",
                        category = DoctorCategory.Permissions,
                        label = text(R.string.doctor_check_nfc),
                        detail = if (nfcNeeders.isEmpty())
                            text(R.string.doctor_detail_nfc_off_not_required)
                        else
                            text(R.string.doctor_detail_nfc_off_needed_by_3, nfcNeeders.joinToString(", ") { it.shortName(context) }),
                        severity = if (nfcNeeders.isEmpty()) Severity.INFO else Severity.WARN,
                        fix = if (nfcNeeders.isEmpty()) null else FixAction.OpenIntent(
                            label = text(R.string.doctor_fix_open_nfc),
                            intent = android.content.Intent(android.provider.Settings.ACTION_NFC_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        ),
                    )
                )
                else -> add(
                    DoctorCheck(
                        id = "perm.nfc_enabled",
                        category = DoctorCategory.Permissions,
                        label = text(R.string.doctor_check_nfc),
                        detail = text(R.string.doctor_detail_nfc_ok),
                        severity = Severity.OK,
                    )
                )
            }
        }
    }

    /**
     * Build a capability-aware Doctor row.
     *   granted = true                                  -> Severity.OK
     *   granted = false AND no enabled tool needs cap   -> Severity.INFO ("not required")
     *   granted = false AND some enabled tool needs cap -> Severity.WARN ("needed by: …")
     *
     * The Fix button is offered only when granted=false AND at least one tool needs the
     * capability — we don't push the user to grant a permission they don't currently use.
     */
    private fun capabilityRow(
        id: String,
        category: DoctorCategory,
        label: String,
        cap: Set<LocalToolOption>,
        enabled: Set<LocalToolOption>,
        granted: Boolean,
        grantedDetail: String,
        missingDetail: String,
        fix: FixAction,
    ): DoctorCheck {
        val needers = requirersOf(cap, enabled)
        val severity = when {
            granted -> Severity.OK
            needers.isEmpty() -> Severity.INFO
            else -> Severity.WARN
        }
        val detail = when {
            granted -> grantedDetail
            needers.isEmpty() -> text(R.string.doctor_detail_not_required_tool)
            else -> text(R.string.doctor_detail_perm_needed_by_2, missingDetail, needers.joinToString(", ") { it.shortName(context) })
        }
        return DoctorCheck(
            id = id,
            category = category,
            label = label,
            detail = detail,
            severity = severity,
            fix = if (!granted && needers.isNotEmpty()) fix else null,
        )
    }

    // ----- Background services ---------------------------------------------------------

    suspend fun serviceChecks(enabled: Set<LocalToolOption>): List<DoctorCheck> = buildList {
        // AccessibilityService binding — only flagged if a tool that needs it is enabled.
        val accNeeders = requirersOf(DoctorCapability.Accessibility, enabled)
        if (accNeeders.isNotEmpty()) {
            val bound = AccessibilityServiceHandle.isRunning()
            // The post-action screen state depends on getWindows() returning real data; a
            // service bound with a pre-update config (missing flagRetrieveInteractiveWindows)
            // reports bound=true but windows stays empty.
            val windowsOk = bound && me.rerere.zeroagent.service.ZeroAgentAccessibilityService.instance
                ?.let { runCatching { it.windows.isNotEmpty() }.getOrDefault(false) } == true
            add(
                DoctorCheck(
                    id = "service.accessibility_bound",
                    category = DoctorCategory.Services,
                    label = text(R.string.doctor_check_accessibility_bound),
                    detail = when {
                        bound && windowsOk ->
                            text(R.string.doctor_detail_acc_alive, accNeeders.joinToString(", ") { it.shortName(context) })
                        bound ->
                            text(R.string.doctor_detail_acc_bound_no_window)
                        PermissionHelper.hasAccessibilityService(context) ->
                            text(R.string.doctor_detail_acc_enabled_not_bound)
                        else ->
                            text(R.string.doctor_detail_acc_not_enabled, accNeeders.joinToString(", ") { it.shortName(context) })
                    },
                    severity = if (bound && windowsOk) Severity.OK else Severity.WARN,
                    fix = if (!bound || !windowsOk) FixAction.OpenIntent(
                        label = text(R.string.doctor_fix_open_settings),
                        intent = PermissionHelper.accessibilitySettingsIntent(),
                    ) else null,
                )
            )
        }
        // NotificationListener binding — same logic.
        val nlNeeders = requirersOf(DoctorCapability.NotificationListener, enabled)
        if (nlNeeders.isNotEmpty()) {
            add(
                DoctorCheck(
                    id = "service.notification_listener_bound",
                    category = DoctorCategory.Services,
                    label = text(R.string.doctor_check_notif_listener_bound),
                    detail = if (NotificationListenerHandle.isBound())
                        text(R.string.doctor_detail_nl_bound, nlNeeders.joinToString(", ") { it.shortName(context) })
                    else if (PermissionHelper.hasNotificationListener(context))
                        text(R.string.doctor_detail_nl_granted_not_bound)
                    else
                        text(R.string.doctor_detail_nl_not_granted, nlNeeders.joinToString(", ") { it.shortName(context) }),
                    severity = when {
                        NotificationListenerHandle.isBound() -> Severity.OK
                        else -> Severity.WARN
                    },
                    fix = if (!NotificationListenerHandle.isBound()) FixAction.OpenIntent(
                        label = text(R.string.doctor_fix_open_settings),
                        intent = PermissionHelper.notificationListenerSettingsIntent(),
                    ) else null,
                )
            )
        }
    }

    // ----- Termux ----------------------------------------------------------------------

    suspend fun termuxChecks(enabled: Set<LocalToolOption>): List<DoctorCheck> = buildList {
        if (requirersOf(DoctorCapability.Termux, enabled).isEmpty()) return@buildList
        val ready = isEmbeddedTerminalReady()
        add(DoctorCheck(
            id = "terminal.embedded",
            category = DoctorCategory.Termux,
            label = text(R.string.embedded_terminal_title),
            detail = text(if (ready) R.string.embedded_terminal_ready else R.string.embedded_terminal_not_ready),
            severity = if (ready) Severity.OK else Severity.WARN,
        ))
    }

    // ----- Shizuku ------------------------------------------------------------------------

    /**
     * Doctor refresh: three rows tracking Shizuku the same way [termuxChecks] tracks Termux,
     * a companion privileged-helper app the `shizuku_exec` tool depends on. Unlike Termux,
     * these rows are always emitted (not skipped when unused) so the settings page shows
     * "not required" rather than silently omitting the whole category; severity still
     * downgrades to INFO when no enabled tool needs Shizuku, matching every other
     * capability-aware row in this file.
     *
     * All three derive their severity from a single [ShizukuStatus], computed once via
     * [ShizukuManager.status] (which itself delegates to
     * [me.rerere.zeroagent.shizuku.ShizukuStatusMapper.compute]) rather than re-deriving the
     * installed/running/permission precedence here.
     */
    fun shizukuChecks(enabled: Set<LocalToolOption>): List<DoctorCheck> = buildList {
        runCatching {
            val needers = requirersOf(DoctorCapability.Shizuku, enabled)
            val status = ShizukuManager.status(context)
            val fix = FixAction.OpenAppRoute(text(R.string.doctor_fix_open_shizuku), AppRouteKey.SettingShizuku)

            add(
                DoctorCheck(
                    id = "shizuku.installed",
                    category = DoctorCategory.Shizuku,
                    label = text(R.string.doctor_check_shizuku_installed),
                    detail = when {
                        status != ShizukuStatus.NOT_INSTALLED ->
                            text(R.string.doctor_detail_shizuku_present)
                        needers.isEmpty() -> "Not installed. Not required by any enabled tool."
                        else -> text(R.string.doctor_detail_shizuku_not_installed_needed, needers.joinToString(", ") { it.shortName(context) })
                    },
                    severity = when {
                        status != ShizukuStatus.NOT_INSTALLED -> Severity.OK
                        needers.isEmpty() -> Severity.INFO
                        else -> Severity.WARN
                    },
                    fix = if (status == ShizukuStatus.NOT_INSTALLED && needers.isNotEmpty()) fix else null,
                )
            )
            add(
                DoctorCheck(
                    id = "shizuku.running",
                    category = DoctorCategory.Shizuku,
                    label = text(R.string.doctor_check_shizuku_running),
                    detail = when (status) {
                        ShizukuStatus.NOT_INSTALLED -> text(R.string.doctor_detail_shizuku_cant_check)
                        ShizukuStatus.NOT_RUNNING ->
                            if (needers.isEmpty()) "Binder not alive. Not required by any enabled tool."
                            else text(R.string.doctor_detail_shizuku_binder_not_alive, needers.joinToString(", ") { it.shortName(context) })
                        else -> text(R.string.doctor_detail_shizuku_binder_alive)
                    },
                    severity = when (status) {
                        ShizukuStatus.NOT_INSTALLED -> Severity.INFO
                        ShizukuStatus.NOT_RUNNING -> if (needers.isEmpty()) Severity.INFO else Severity.WARN
                        else -> Severity.OK
                    },
                    fix = if (status == ShizukuStatus.NOT_RUNNING && needers.isNotEmpty()) fix else null,
                )
            )
            add(
                DoctorCheck(
                    id = "shizuku.permission",
                    category = DoctorCategory.Shizuku,
                    label = text(R.string.doctor_check_shizuku_perm),
                    detail = when (status) {
                        ShizukuStatus.NOT_INSTALLED, ShizukuStatus.NOT_RUNNING ->
                            text(R.string.doctor_detail_shizuku_not_applicable)
                        ShizukuStatus.PERMISSION_DENIED ->
                            if (needers.isEmpty()) "Not granted. Not required by any enabled tool."
                            else text(R.string.doctor_detail_shizuku_not_granted_needed, needers.joinToString(", ") { it.shortName(context) })
                        ShizukuStatus.READY -> text(R.string.doctor_detail_shizuku_granted)
                    },
                    severity = when (status) {
                        ShizukuStatus.NOT_INSTALLED, ShizukuStatus.NOT_RUNNING -> Severity.INFO
                        ShizukuStatus.PERMISSION_DENIED -> if (needers.isEmpty()) Severity.INFO else Severity.WARN
                        ShizukuStatus.READY -> Severity.OK
                    },
                    fix = if (status == ShizukuStatus.PERMISSION_DENIED && needers.isNotEmpty()) fix else null,
                )
            )
        }.onFailure {
            add(
                DoctorCheck(
                    id = "shizuku.probe_failed",
                    category = DoctorCategory.Shizuku,
                    label = text(R.string.doctor_check_shizuku),
                    detail = text(R.string.doctor_detail_probe_failed, it::class.simpleName ?: "?", it.message ?: "?"),
                    severity = Severity.WARN,
                )
            )
        }
    }
}
