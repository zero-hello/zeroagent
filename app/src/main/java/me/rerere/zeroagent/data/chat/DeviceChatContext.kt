package me.rerere.zeroagent.data.chat

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.zeroagent.data.root.RootAccessManager
import me.rerere.zeroagent.data.root.RootAccessStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal data class DeviceChatStatus(
    val lowBatteryPercent: Int?,
    val severeThermalLoad: Boolean,
    val powerSave: Boolean,
    val metered: Boolean,
    val networkIssue: String?,
    val lowStorage: Boolean,
    val lowMemory: Boolean,
)

internal fun readDeviceChatStatus(context: Context): DeviceChatStatus {
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val percent = if (level in 0..scale && scale > 0) (100L * level / scale).toInt() else null
    val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    val power = context.getSystemService(PowerManager::class.java)
    val connection = context.getSystemService(ConnectivityManager::class.java)
    val activeNetwork = connection?.activeNetwork
    val capabilities = activeNetwork?.let { connection?.getNetworkCapabilities(it) }
    val networkIssue = when {
        connection != null && activeNetwork == null -> "no active connection"
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == false -> "active connection is not validated"
        else -> null
    }
    val lowStorage = runCatching { StatFs(context.filesDir.path).availableBytes < 500L * 1024 * 1024 }.getOrDefault(false)
    val activity = context.getSystemService(ActivityManager::class.java)
    val lowMemory = activity?.let { manager -> ActivityManager.MemoryInfo().also(manager::getMemoryInfo).lowMemory } == true
    return DeviceChatStatus(
        lowBatteryPercent = percent?.takeIf { it <= 15 && !plugged },
        severeThermalLoad = (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE,
        powerSave = power?.isPowerSaveMode == true,
        metered = activeNetwork != null && connection?.isActiveNetworkMetered == true,
        networkIssue = networkIssue,
        lowStorage = lowStorage,
        lowMemory = lowMemory,
    )
}

internal fun observeDeviceChatStatus(context: Context) = flow {
    while (true) {
        emit(runCatching { readDeviceChatStatus(context) }.getOrNull())
        delay(15_000)
    }
}.distinctUntilChanged().flowOn(Dispatchers.IO)

private val recentDeviceAlerts = object : LinkedHashMap<String, String>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 64
}

internal suspend fun deviceContextForChat(context: Context, conversationId: String, includeRoot: Boolean): String? = withContext(Dispatchers.IO) {
    val status = readDeviceChatStatus(context)
    val facts = buildList {
        if (status.lowBatteryPercent != null) add("battery is low and not charging")
        if (status.lowMemory) add("Android reports low available memory")
        if (status.powerSave) add("battery saver is on")
        if (status.networkIssue != null) add(status.networkIssue)
        if (status.metered) add("the active connection is metered")
        if (includeRoot && status.lowMemory && readRootMemoryPressure(context) == true) {
            add("most configured swap space is in use")
        }
        if (status.severeThermalLoad) add("device is under severe thermal load")
        if (status.lowStorage) add("less than 500 MB of internal storage available")
    }
    val current = facts.joinToString("; ")
    val changed = synchronized(recentDeviceAlerts) { recentDeviceAlerts.put(conversationId, current) != current }
    current.takeIf { changed && it.isNotEmpty() }?.let {
        "Android alert for this turn: $it. Mention it only if directly useful. Do not infer missing information."
    }
}

/** A fixed read-only probe. The system grant is rechecked by run and only a numeric result is used. */
internal suspend fun readRootMemoryPressure(context: Context): Boolean? {
    val root = RootAccessManager(context)
    if (!root.enabled || root.status.value != RootAccessStatus.AVAILABLE) return null
    val result = withTimeoutOrNull(2_500) {
        root.run("/system/bin/grep -E '^Swap(Total|Free):' /proc/meminfo", timeoutSeconds = 2)
    } ?: return null
    return if (result.exitCode == 0 && root.enabled) parseSwapPressure(result.output) else null
}

internal fun parseSwapPressure(output: String): Boolean? {
    val fields = Regex("(?m)^(SwapTotal|SwapFree):[ \\t]+([0-9]{1,12})[ \\t]+kB[ \\t]*\\r?$")
        .findAll(output).groupBy { it.groupValues[1] }
    val total = fields["SwapTotal"]?.singleOrNull()?.groupValues?.get(2)?.toLongOrNull() ?: return null
    val free = fields["SwapFree"]?.singleOrNull()?.groupValues?.get(2)?.toLongOrNull() ?: return null
    return if (total > 0 && free in 0..total) free * 10 < total else null
}