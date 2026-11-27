package me.rerere.zeroagent.ui.components.ui

import android.content.Context
import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.StrokeCap
import me.rerere.zeroagent.ui.designsystem.ZeroTheme
import me.rerere.zeroagent.ui.designsystem.ZeroMotion
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.R

internal data class DownloadProgress(val bytes: Long, val total: Long?) {
    val fraction: Float? get() = total?.takeIf { it > 0 }?.let { (bytes.toDouble() / it).toFloat().coerceIn(0f, 1f) }

    fun label(context: Context): String {
        val downloaded = Formatter.formatFileSize(context, bytes.coerceAtLeast(0))
        val size = total?.takeIf { it > 0 }
        return if (size == null) context.getString(R.string.transfer_download_unknown, downloaded)
        else context.getString(R.string.transfer_download_size, downloaded, Formatter.formatFileSize(context, size))
    }
}

@Composable
internal fun TransferProgress(fraction: Float?, modifier: Modifier = Modifier, detail: String? = null) {
    val colors = ZeroTheme.colors
    val bar = Modifier.fillMaxWidth().height(2.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (fraction == null) LinearProgressIndicator(modifier = bar, color = colors.accentOlive, trackColor = colors.borderSubtle, strokeCap = StrokeCap.Round)
        else {
            val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = ZeroMotion.fluid(), label = "progress")
            LinearProgressIndicator(progress = { animated.coerceIn(0f, 1f) }, modifier = bar, color = colors.accentOlive, trackColor = colors.borderSubtle, strokeCap = StrokeCap.Round)
        }
        if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"))
    }
}

@Composable
internal fun DownloadProgressIndicator(progress: DownloadProgress, modifier: Modifier = Modifier) {
    val count = progress.label(LocalContext.current)
    val percent = progress.fraction?.let { (it * 100).toInt() }
    val detail = if (percent == null) count else "${stringResource(R.string.local_llm_download_progress, percent)} · $count"
    TransferProgress(progress.fraction, modifier, detail)
}