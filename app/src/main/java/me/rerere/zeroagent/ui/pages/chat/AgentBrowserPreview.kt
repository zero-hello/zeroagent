package me.rerere.zeroagent.ui.pages.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import me.rerere.zeroagent.R
import me.rerere.zeroagent.browser.BrowserController
import me.rerere.zeroagent.ui.designsystem.ZeroCapsuleButton
import me.rerere.zeroagent.ui.designsystem.ZeroHairlineCard
import me.rerere.zeroagent.ui.designsystem.ZeroStatusDot
import me.rerere.zeroagent.ui.designsystem.ZeroTheme

@Composable
internal fun AgentBrowserPreview(conversationId: String, modifier: Modifier = Modifier) {
    val owner by BrowserController.previewOwner.collectAsStateWithLifecycle()
    if (owner != conversationId) return
    val actions by BrowserController.recentActionsFlow().collectAsStateWithLifecycle()
    val active by BrowserController.taskActiveFlow().collectAsStateWithLifecycle()
    var expanded by remember(owner) { mutableStateOf(BrowserController.agentDisplay.previewExpanded) }
    var page by remember(owner) { mutableStateOf<String?>(null) }
    var thumbnail by remember(owner) { mutableStateOf<Bitmap?>(null) }
    var takeover by remember(owner) { mutableStateOf<WebView?>(null) }
    var busy by remember(owner) { mutableStateOf(false) }
    LaunchedEffect(owner, expanded, takeover) {
        if (takeover != null) return@LaunchedEffect
        while (true) {
            val session = BrowserController.currentMode() as? BrowserController.Mode.Headless
            if (session?.callerConvId != conversationId) break
            val view = session.webView
            page = view.url
            if (expanded && view.width > 0 && view.height > 0) thumbnail = captureThumbnail(view)
            delay(if (expanded) 900 else 1_500)
        }
    }
    ZeroHairlineCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active) ZeroStatusDot(animated = true)
                Text(
                    stringResource(R.string.zeroflow_agent_browser),
                    color = ZeroTheme.colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                ZeroCapsuleButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.chat_message_turn_collapse else R.string.chat_message_turn_expand))
                }
            }
            page?.let { url ->
                Text(url.substringBefore('?').substringBefore('#'), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
            }
            actions.firstOrNull()?.let { action ->
                Text(
                    me.rerere.zeroagent.browser.actionSentence(action.copy(detail = action.detail?.substringBefore('?')?.substringBefore('#'))),
                    style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (expanded) {
                thumbnail?.let { bitmap ->
                    Image(bitmap.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 210.dp), contentScale = ContentScale.Fit)
                }
                ZeroCapsuleButton(onClick = {
                    takeover = BrowserController.acquireManualControl(conversationId)
                    busy = takeover == null
                }) { Text(stringResource(R.string.zeroflow_browser_take_control)) }
                if (busy) Text(stringResource(R.string.zeroflow_browser_wait_for_tool), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    takeover?.let { view ->
        val parent = remember(view) { view.parent as? ViewGroup }
        DisposableEffect(view) {
            onDispose {
                (view.parent as? ViewGroup)?.removeView(view)
                val session = BrowserController.currentMode() as? BrowserController.Mode.Headless
                if (session?.webView === view) {
                    parent?.addView(view)
                    parent?.requestLayout()
                    if (parent != null && parent.width > 0 && parent.height > 0) {
                        val width = parent.width
                        val height = parent.height
                        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY))
                        view.layout(0, 0, width, height)
                    }
                }
                BrowserController.releaseManualControl(conversationId)
            }
        }
        Dialog(onDismissRequest = { takeover = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = ZeroTheme.colors.surface) {
                Column(Modifier.fillMaxSize()) {
                    ZeroCapsuleButton(onClick = { takeover = null }, modifier = Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.zeroflow_browser_return_control))
                    }
                    AndroidView(
                        factory = { context ->
                            android.widget.FrameLayout(context).apply {
                                (view.parent as? ViewGroup)?.removeView(view)
                                addView(view, ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                                ))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

private fun captureThumbnail(view: WebView): Bitmap? = runCatching {
    val width = minOf(320, view.width)
    val scale = width.toFloat() / view.width
    val height = (view.height * scale).toInt().coerceIn(1, 340)
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { image ->
        Canvas(image).apply { scale(scale, scale); view.draw(this) }
    }
}.getOrNull()