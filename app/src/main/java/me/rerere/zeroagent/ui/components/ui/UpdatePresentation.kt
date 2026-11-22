package me.rerere.zeroagent.ui.components.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.richtext.MarkdownBlock
import me.rerere.zeroagent.utils.UpdateTransfer

@Composable
internal fun ReleaseNotes(content: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    MarkdownBlock(content = content, modifier = modifier, style = style)
}

@Composable
internal fun UpdateDownloadProgress(transfer: UpdateTransfer) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (transfer.verifying) Text(stringResource(R.string.transfer_verifying_update))
        DownloadProgressIndicator(transfer.progress, Modifier.fillMaxWidth())
    }
}

/** A denied notification permission still leaves progress visible in the app. */
@Composable
internal fun rememberUpdateNotificationRequest(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val action = pending.value
        pending.value = null
        action?.invoke()
    }
    return { action ->
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (pending.value == null) {
                pending.value = action
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else action()
    }
}