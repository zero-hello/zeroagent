package me.rerere.zeroagent.ui.designsystem

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.ui.TransferProgress
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog

@Composable
internal fun ZeroAppearanceSettings(modifier: Modifier = Modifier) {
    val appearance = rememberZeroAppearance()
    val scope = rememberCoroutineScope()
    val feedback = rememberZeroFeedback()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val progress = remember { MutableStateFlow<Float?>(null) }
    val fraction by progress.collectAsState()
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = false
        progress.value = null
        scope.launch {
            try {
                action()
                feedback(ZeroFeedbackEvent.Complete)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = true
            } finally {
                busy = false
                progress.value = null
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) run { appearance.installLocal(uri) { progress.value = it } }
    }
    ZeroHairlineCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.zero_v6_sound), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                ZeroAnimatedSwitch(appearance.soundEnabled, appearance::updateSoundPreference)
            }
            Text(stringResource(R.string.zero_v6_font), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.zero_v6_font_cache_info), color = ZeroTheme.colors.textSecondary)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to stringResource(R.string.zero_v6_system_font), "manrope" to "Manrope", "inter" to "Inter", "local" to stringResource(R.string.zero_v6_local_font)).forEach { (id, label) ->
                    if (id != "local" || id in appearance.cachedFonts) {
                        val download = id == "inter" && id !in appearance.cachedFonts
                        ZeroCapsuleButton(onClick = {
                            run { if (download) appearance.installInter { progress.value = it } else appearance.select(id) }
                        }, enabled = !busy && (download || appearance.selectedId != id), selected = appearance.selectedId == id) {
                            Text(if (download) stringResource(R.string.zero_v6_download_font, label) else label)
                        }
                    }
                }
            }
            if (busy) TransferProgress(fraction = fraction, modifier = Modifier.fillMaxWidth())
            if (error) Text(stringResource(R.string.zero_v6_font_failed), color = ZeroTheme.colors.destructive)
            ZeroCapsuleButton(onClick = { picker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")) }, enabled = !busy) {
                Text(stringResource(R.string.zero_v6_import_font))
            }
            if (appearance.cachedFonts.isNotEmpty()) ZeroCapsuleButton({ confirmClear = true }, enabled = !busy) {
                Text(stringResource(R.string.zero_v6_clear_fonts))
            }
        }
    }
    if (confirmClear) ZeroAgentAlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.zero_v6_clear_fonts)) },
        text = { Text(stringResource(R.string.zero_v6_clear_fonts_message)) },
        confirmButton = { ZeroCapsuleButton({ confirmClear = false; run { appearance.clearCache() } }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { ZeroCapsuleButton({ confirmClear = false }) { Text(stringResource(android.R.string.cancel)) } })
}
