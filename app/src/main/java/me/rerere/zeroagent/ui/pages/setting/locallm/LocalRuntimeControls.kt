package me.rerere.zeroagent.ui.pages.setting.locallm

import androidx.compose.material3.ButtonDefaults

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.locallm.LocalRuntimeSupport
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.local.LocalModelDownloads.DownloadRestriction
import me.rerere.zeroagent.ui.components.ui.Switch

@Composable
fun LocalRuntimeControls(vm: SettingLocalLlmViewModel) {
    val limits = org.koin.compose.koinInject<me.rerere.zeroagent.data.local.GenerationResourceLimits>()
    val pauseLow by limits.pauseLocalLow.collectAsStateWithLifecycle()
    val batchCharging by limits.requireBatchCharging.collectAsStateWithLifecycle()
    val capabilities by vm.capabilities.collectAsStateWithLifecycle()
    val forceCpu by vm.forceCpu.collectAsStateWithLifecycle()
    val accelerator by vm.accelerator.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.local_v11_weights_portable), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("電量剩 15% 時暫停本機生成", Modifier.weight(1f))
            Switch(checked = pauseLow, onCheckedChange = limits::setPauseLocalLow)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("只在充電時執行批次工作", Modifier.weight(1f))
            Switch(checked = batchCharging, onCheckedChange = limits::setRequireBatchCharging)
        }
        capabilities?.let { caps ->
            Text(stringResource(R.string.local_v11_abi, caps.processAbi ?: "?", caps.deviceAbis.joinToString()))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = forceCpu || !caps.gpuAvailable, enabled = caps.cpuAvailable,
                    onClick = { vm.setForceCpu(true) }, label = { Text("CPU") })
                FilterChip(selected = !forceCpu && caps.gpuAvailable, enabled = caps.gpuAvailable,
                    onClick = { vm.setForceCpu(false) }, label = { Text("GPU") })
            }
            Text(stringResource(when (caps.reason) {
                LocalRuntimeSupport.Reason.NATIVE_LIBRARY -> R.string.local_v11_native_missing
                LocalRuntimeSupport.Reason.CPU_ONLY_RUNTIME -> R.string.local_v11_cpu_only
                LocalRuntimeSupport.Reason.GPU_DRIVER -> R.string.local_v11_gpu_driver_missing
                LocalRuntimeSupport.Reason.READY -> R.string.local_v11_gpu_candidate
            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.local_v11_actual_backend, accelerator ?: stringResource(R.string.local_v11_not_loaded)))
        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = vm::reDetectAccelerator) { Text(stringResource(R.string.local_llm_re_detect)) }
    }
}

@Composable
fun LocalDownloadControls(vm: SettingLocalLlmViewModel) {
    val progress by vm.downloadProgress.collectAsStateWithLifecycle()
    val paused by vm.downloadPaused.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val file by vm.downloadFile.collectAsStateWithLifecycle()
    val restrictions by vm.downloadRestrictions.collectAsStateWithLifecycle()
    val waiting by vm.downloadWaiting.collectAsStateWithLifecycle()
    var showRestrictions by remember { mutableStateOf(false) }
    TextButton(onClick = { showRestrictions = !showRestrictions }) {
        Text(if (showRestrictions) "收起下載條件" else "下載條件")
    }
    if (showRestrictions) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("條件會在下一次下載或續傳時套用。", style = MaterialTheme.typography.bodySmall)
            val options = listOf(
                DownloadRestriction.UNMETERED to "只用不限流量的網路",
                DownloadRestriction.BATTERY_NOT_LOW to "電量不足時暫停下載",
                DownloadRestriction.CHARGING to "只在充電時下載",
            )
            options.forEach { (restriction, label) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f))
                    Switch(checked = restriction in restrictions, onCheckedChange = { vm.setDownloadRestriction(restriction, it) })
                }
            }
        }
    }
    val context = LocalContext.current
    var notificationAllowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationAllowed = it
    }
    if (importing) {
        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = vm::pauseDownload) {
            Text(stringResource(R.string.cancel))
        }
    } else if (progress != null || paused) Column(modifier = Modifier.fillMaxWidth()) {
        file?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (waiting) Text("等待網路、電量、充電條件或下次重試。", style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.local_v11_background_download), style = MaterialTheme.typography.bodySmall)
        if (!notificationAllowed && Build.VERSION.SDK_INT >= 33) TextButton(shapes = ButtonDefaults.shapes(), onClick = {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }) { Text(stringResource(R.string.local_v11_allow_notifications)) }
        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { if (progress != null) vm.pauseDownload() else vm.resumeDownload() }) {
            Text(stringResource(if (progress != null) R.string.local_v11_pause else R.string.local_v11_resume))
        }
    }
}
