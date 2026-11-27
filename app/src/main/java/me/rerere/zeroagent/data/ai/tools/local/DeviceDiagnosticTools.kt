package me.rerere.zeroagent.data.ai.tools.local

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

internal fun deviceDiagnosticTools(context: Context): List<Tool> = listOf(
    deviceDiagnostic("get_android_platform", "Read Android version, device model and supported CPU architectures.") {
        buildJsonObject {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("android_release", Build.VERSION.RELEASE)
            put("api_level", Build.VERSION.SDK_INT)
            put("supported_abis", buildJsonArray { Build.SUPPORTED_ABIS.forEach { add(it) } })
            put("logical_processors_available", Runtime.getRuntime().availableProcessors())
        }
    },
    deviceDiagnostic("get_device_memory", "Read available and total memory, plus Android's low-memory signal.") {
        val manager = context.getSystemService(ActivityManager::class.java)
        val info = manager?.let { ActivityManager.MemoryInfo().also(it::getMemoryInfo) }
        buildJsonObject {
            if (info == null) put("error", "memory status unavailable") else {
                put("total_bytes", info.totalMem)
                put("available_bytes", info.availMem)
                put("low_memory", info.lowMemory)
            }
        }
    },
    deviceDiagnostic("get_power_and_thermal_status", "Read battery saver and Android thermal severity. Does not measure temperature.") {
        val power = context.getSystemService(PowerManager::class.java)
        buildJsonObject {
            if (power == null) put("error", "power status unavailable") else {
                put("battery_saver", power.isPowerSaveMode)
                put("thermal_status", when (power.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_NONE -> "none"
                    PowerManager.THERMAL_STATUS_LIGHT -> "light"
                    PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
                    PowerManager.THERMAL_STATUS_SEVERE -> "severe"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
                    else -> "unknown"
                })
            }
        }
    },
    deviceDiagnostic("get_network_health", "Read whether the active connection is validated, metered, or behind a captive portal. Does not return network names or addresses.") {
        val service = context.getSystemService(ConnectivityManager::class.java)
        val network = service?.activeNetwork
        val capabilities = network?.let { service?.getNetworkCapabilities(it) }
        buildJsonObject {
            if (service == null) put("error", "network status unavailable") else {
                put("active_connection", network != null)
                if (capabilities != null) {
                    put("validated_internet", capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                    put("metered", !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
                    put("captive_portal", capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL))
                }
            }
        }
    },
    deviceDiagnostic("get_display_configuration", "Read the Android display area, density, and text scaling for layout troubleshooting.") {
        val metrics = context.resources.displayMetrics
        val bounds = context.getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds
        buildJsonObject {
            put("width_px", bounds?.width() ?: metrics.widthPixels)
            put("height_px", bounds?.height() ?: metrics.heightPixels)
            put("density_dpi", metrics.densityDpi)
            put("font_scale", context.resources.configuration.fontScale)
        }
    },
)

private fun deviceDiagnostic(name: String, description: String, read: () -> JsonObject): Tool = Tool(
    name = name,
    description = description,
    parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
    execute = {
        val result = withContext(Dispatchers.IO) {
            try { read() } catch (_: SecurityException) {
                buildJsonObject { put("error", "Android did not allow access to this status") }
            }
        }
        listOf(UIMessagePart.Text(result.toString()))
    },
)
