package me.rerere.zeroagent.data.permissions

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import me.rerere.zeroagent.R
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext
import me.rerere.zeroagent.service.ZeroAgentAccessibilityService
import me.rerere.zeroagent.service.ZeroAgentNotificationListenerService

/**
 * Auto-discovered inventory of every permission this app requires, grouped by how the
 * user grants it. Reads <uses-permission> entries at runtime via PackageManager so
 * future-added perms appear automatically. Curated labels take priority over platform
 * resources loaded with the selected app language.
 *
 * On top of <uses-permission>, two virtual rows surface service bindings the user must enable
 * via dedicated Android UIs (AccessibilityService, NotificationListenerService) — these are
 * not real permissions but behave the same from the user's standpoint.
 */
object PermissionInventory {

    enum class Group { ServicesAndIntegrations, SpecialAccess, Runtime, AutoGranted }

    enum class Status { GRANTED, DENIED, AUTO_GRANTED }

    sealed class GrantAction {
        /** No action required — install-time / signature-level / always granted. */
        object None : GrantAction()
        /** Request via ActivityResultContracts.RequestPermission. */
        data class Runtime(val permission: String) : GrantAction()
        /** Open this Intent, user toggles in system Settings. */
        data class SystemSettings(val intent: Intent) : GrantAction()
    }

    data class Row(
        val id: String,
        val label: String,
        val description: String,
        val status: Status,
        val group: Group,
        val grant: GrantAction,
    )

    fun build(context: Context): List<Row> {
        val localized = context.zeroAgentLocalizedContext()
        return buildLocalized(localized)
    }

    private fun buildLocalized(context: Context): List<Row> {
        val rows = mutableListOf<Row>()
        rows += accessibilityServiceRow(context)
        rows += notificationListenerRow(context)

        val declared = readDeclaredPermissions(context)
        for (perm in declared) {
            rows += classify(context, perm) ?: continue
        }
        return rows.sortedWith(
            compareBy({ it.group.ordinal }, { if (it.status == Status.DENIED) 0 else 1 }, { it.label })
        )
    }

    private fun readDeclaredPermissions(context: Context): List<String> {
        val pm = context.packageManager
        val info: PackageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return emptyList()
        }
        return info.requestedPermissions?.toList() ?: emptyList()
    }

    private fun classify(context: Context, perm: String): Row? {
        val pm = context.packageManager
        val pkgUri: Uri = ("package:" + context.packageName).toUri()

        // Special-access permissions — each has its own canWrite / canDrawOverlays / etc check
        // and a deep-link Intent.
        when (perm) {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION -> return Row(
                id = perm,
                label = context.getString(R.string.zeroagent_permission_background_location),
                description = context.getString(R.string.zeroagent_permission_background_location_hint),
                status = if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) Status.GRANTED else Status.DENIED,
                group = Group.SpecialAccess,
                grant = GrantAction.SystemSettings(appDetailsIntent(context)),
            )
            Manifest.permission.MANAGE_EXTERNAL_STORAGE -> return Row(
                id = perm,
                label = context.getString(R.string.zeroagent_permission_all_files),
                description = context.getString(R.string.zeroagent_permission_all_files_hint),
                status = if (android.os.Environment.isExternalStorageManager()) Status.GRANTED else Status.DENIED,
                group = Group.SpecialAccess,
                grant = GrantAction.SystemSettings(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri)),
            )
            Manifest.permission.PACKAGE_USAGE_STATS -> {
                val ops = context.getSystemService(android.app.AppOpsManager::class.java)
                val granted = ops?.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == android.app.AppOpsManager.MODE_ALLOWED
                return Row(perm, context.getString(R.string.zeroagent_permission_usage), context.getString(R.string.zeroagent_permission_usage_hint),
                    if (granted) Status.GRANTED else Status.DENIED, Group.SpecialAccess,
                    GrantAction.SystemSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkgUri)))
            }
            Manifest.permission.SYSTEM_ALERT_WINDOW -> {
                val granted = Settings.canDrawOverlays(context)
                return Row(
                    id = perm,
                    label = context.getString(R.string.perm_label_overlay),
                    description = context.getString(R.string.perm_desc_overlay),
                    status = if (granted) Status.GRANTED else Status.DENIED,
                    group = Group.SpecialAccess,
                    grant = GrantAction.SystemSettings(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
                    ),
                )
            }
            Manifest.permission.WRITE_SETTINGS -> {
                val granted = Settings.System.canWrite(context)
                return Row(
                    id = perm,
                    label = context.getString(R.string.perm_label_write_settings),
                    description = context.getString(R.string.perm_desc_write_settings),
                    status = if (granted) Status.GRANTED else Status.DENIED,
                    group = Group.SpecialAccess,
                    grant = GrantAction.SystemSettings(
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri)
                    ),
                )
            }
            Manifest.permission.ACCESS_NOTIFICATION_POLICY -> {
                val nm = context.getSystemService(NotificationManager::class.java)
                val granted = nm?.isNotificationPolicyAccessGranted == true
                return Row(
                    id = perm,
                    label = context.getString(R.string.perm_label_dnd),
                    description = context.getString(R.string.perm_desc_dnd),
                    status = if (granted) Status.GRANTED else Status.DENIED,
                    group = Group.SpecialAccess,
                    grant = GrantAction.SystemSettings(
                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    ),
                )
            }
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> {
                val pwm = context.getSystemService(PowerManager::class.java)
                val granted = pwm?.isIgnoringBatteryOptimizations(context.packageName) == true
                return Row(
                    id = perm,
                    label = context.getString(R.string.perm_label_battery),
                    description = context.getString(R.string.perm_desc_battery),
                    status = if (granted) Status.GRANTED else Status.DENIED,
                    group = Group.SpecialAccess,
                    // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS pops a system dialog asking
                    // for the exemption directly — better UX than the long settings list.
                    grant = GrantAction.SystemSettings(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
                    ),
                )
            }
            Manifest.permission.POST_NOTIFICATIONS -> {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(context, perm) ==
                        PackageManager.PERMISSION_GRANTED
                    Row(
                        id = perm,
                        label = context.getString(R.string.perm_label_post_notifications),
                        description = context.getString(R.string.perm_desc_post_notifications),
                        status = if (granted) Status.GRANTED else Status.DENIED,
                        group = Group.Runtime,
                        grant = GrantAction.Runtime(perm),
                    )
                } else {
                    autoRow(context, perm, context.getString(R.string.perm_label_post_notifications))
                }
            }
        }

        // Only normal permissions are automatically granted; signature access may be unavailable.
        val info: PermissionInfo? = try {
            pm.getPermissionInfo(perm, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        if (info == null) {
            // Unknown to this device — typically a custom perm declared by an app that isn't
            // installed (e.g. com.termux.permission.RUN_COMMAND when Termux isn't installed).
            // Best we can do is check checkSelfPermission and offer no grant flow.
            val granted = ContextCompat.checkSelfPermission(context, perm) ==
                PackageManager.PERMISSION_GRANTED
            return Row(
                id = perm,
                label = resolveLabel(context, perm, null),
                description = context.getString(R.string.perm_desc_custom),
                status = if (granted) Status.GRANTED else Status.DENIED,
                group = Group.Runtime,
                grant = GrantAction.None,
            )
        }

        val protectionBase = info.protection
        val isDangerous = protectionBase == PermissionInfo.PROTECTION_DANGEROUS
        val granted = ContextCompat.checkSelfPermission(context, perm) ==
            PackageManager.PERMISSION_GRANTED

        return if (isDangerous) {
            Row(
                id = perm,
                label = resolveLabel(context, perm, info),
                description = PermissionLabelResolution.description(descriptionFor(context, perm), platformText(context, info, info.descriptionRes), context.getString(R.string.perm_desc_runtime_default)),
                status = if (granted) Status.GRANTED else Status.DENIED,
                group = Group.Runtime,
                grant = GrantAction.Runtime(perm),
            )
        } else if (protectionBase == PermissionInfo.PROTECTION_NORMAL) {
            autoRow(context, perm, resolveLabel(context, perm, info))
        } else {
            Row(perm, resolveLabel(context, perm, info), context.getString(R.string.zeroagent_permission_restricted),
                if (granted) Status.GRANTED else Status.DENIED, Group.SpecialAccess, GrantAction.None)
        }
    }

    private fun autoRow(context: Context, perm: String, label: String) = Row(
        id = perm,
        label = label,
        description = context.getString(R.string.perm_desc_auto),
        status = Status.AUTO_GRANTED,
        group = Group.AutoGranted,
        grant = GrantAction.None,
    )

    private fun accessibilityServiceRow(context: Context): Row {
        val component = ComponentName(context, ZeroAgentAccessibilityService::class.java)
            .flattenToString()
        val enabled = (Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: "").split(":").any { it.equals(component, ignoreCase = true) }
        return Row(
            id = "zeroagent.SERVICE_ACCESSIBILITY",
            label = context.getString(R.string.perm_label_accessibility),
            description = context.getString(R.string.perm_desc_accessibility),
            status = if (enabled) Status.GRANTED else Status.DENIED,
            group = Group.ServicesAndIntegrations,
            grant = GrantAction.SystemSettings(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ),
        )
    }

    private fun notificationListenerRow(context: Context): Row {
        val component = ComponentName(context, ZeroAgentNotificationListenerService::class.java)
            .flattenToString()
        val enabled = (Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: "").split(":").any { it.equals(component, ignoreCase = true) }
        return Row(
            id = "zeroagent.SERVICE_NOTIFICATION_LISTENER",
            label = context.getString(R.string.perm_label_notification_listener),
            description = context.getString(R.string.perm_desc_notification_listener),
            status = if (enabled) Status.GRANTED else Status.DENIED,
            group = Group.ServicesAndIntegrations,
            grant = GrantAction.SystemSettings(
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ),
        )
    }

    // -- Friendly labels for every dangerous permission we currently request ------------------

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    private fun resolveLabel(context: Context, perm: String, info: PermissionInfo?): String =
        PermissionLabelResolution.label(labelFor(context, perm), info?.let {
            platformText(context, it, it.labelRes) ?: it.nonLocalizedLabel?.toString()
        }, context.getString(R.string.zeroagent_permission_other))

    // PackageManager.loadLabel uses the device configuration; use the app's selected language.
    internal fun platformText(context: Context, info: PermissionInfo, resId: Int): String? =
        if (resId == 0) null else runCatching {
            context.createPackageContext(info.packageName, 0)
                .createConfigurationContext(context.resources.configuration).getText(resId).toString()
        }.getOrNull()

    private fun labelFor(context: Context, perm: String): String? = when (perm) {
        Manifest.permission.CAMERA -> context.getString(R.string.perm_label_camera)
        Manifest.permission.RECORD_AUDIO -> context.getString(R.string.perm_label_microphone)
        Manifest.permission.READ_PHONE_STATE -> context.getString(R.string.perm_label_phone_state)
        Manifest.permission.ACCESS_FINE_LOCATION -> context.getString(R.string.perm_label_fine_location)
        Manifest.permission.ACCESS_COARSE_LOCATION -> context.getString(R.string.perm_label_coarse_location)
        Manifest.permission.READ_CONTACTS -> context.getString(R.string.perm_label_contacts)
        Manifest.permission.READ_CALL_LOG -> context.getString(R.string.perm_label_call_log)
        Manifest.permission.READ_SMS -> context.getString(R.string.perm_label_sms)
        Manifest.permission.SEND_SMS -> context.getString(R.string.perm_label_send_sms)
        Manifest.permission.POST_NOTIFICATIONS -> context.getString(R.string.perm_label_post_notifications)
        "com.termux.permission.RUN_COMMAND" -> context.getString(R.string.perm_label_termux_run)
        "dev.patrickgold.florisboard.permission.AGENT_KEYBOARD_API" -> context.getString(R.string.zeroagent_permission_keyboard)
        "moe.shizuku.manager.permission.API_V23" -> context.getString(R.string.zeroagent_permission_shizuku)
        else -> null
    }

    private fun descriptionFor(context: Context, perm: String): String? = when (perm) {
        Manifest.permission.CAMERA -> context.getString(R.string.perm_desc_camera)
        Manifest.permission.RECORD_AUDIO -> context.getString(R.string.perm_desc_microphone)
        Manifest.permission.READ_PHONE_STATE -> context.getString(R.string.perm_desc_phone_state)
        Manifest.permission.ACCESS_FINE_LOCATION -> context.getString(R.string.perm_desc_fine_location)
        Manifest.permission.ACCESS_COARSE_LOCATION -> context.getString(R.string.perm_desc_coarse_location)
        Manifest.permission.READ_CONTACTS -> context.getString(R.string.perm_desc_contacts)
        Manifest.permission.READ_CALL_LOG -> context.getString(R.string.perm_desc_call_log)
        Manifest.permission.READ_SMS -> context.getString(R.string.perm_desc_sms)
        Manifest.permission.SEND_SMS -> context.getString(R.string.perm_desc_send_sms)
        "com.termux.permission.RUN_COMMAND" -> context.getString(R.string.perm_desc_termux_run)
        else -> null
    }

}

private fun String.toUri(): Uri = Uri.parse(this)
