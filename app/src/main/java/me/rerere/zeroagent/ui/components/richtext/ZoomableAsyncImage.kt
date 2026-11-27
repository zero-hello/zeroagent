package me.rerere.zeroagent.ui.components.richtext

import me.rerere.zeroagent.ui.motion.imageFade
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.ui.ImagePreviewDialog
import me.rerere.zeroagent.ui.components.ui.LocalExportContext
import me.rerere.zeroagent.ui.modifier.shimmer
import me.rerere.zeroagent.ui.theme.LocalDarkMode

@Composable
fun ZoomableAsyncImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
) {
    var showImageViewer by remember { mutableStateOf(false) }
    var imageCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var openedFrom by remember { mutableStateOf<Rect?>(null) }
    val view = LocalView.current
    val context = LocalContext.current
    val placeholder = if(LocalDarkMode.current) R.drawable.placeholder_dark else R.drawable.placeholder
    val export = LocalExportContext.current
    val coilModel = ImageRequest.Builder(context)
        .data(model)
        .placeholder(placeholder)
        .error(placeholder)
        .crossfade(false)
        .allowHardware(!export)
        .build()
    var loading by remember(model) { mutableStateOf(false) }
    var ready by remember(model) { mutableStateOf(false) }
    AsyncImage(
        model = coilModel,
        contentDescription = contentDescription,
        modifier = modifier
            .imageFade(model, ready, export)
            .onGloballyPositioned { imageCoordinates = it }
            .clickable {
                openedFrom = imageCoordinates?.takeIf { it.isAttached }?.let { coordinates ->
                    val screen = IntArray(2).also(view::getLocationOnScreen)
                    val window = IntArray(2).also(view::getLocationInWindow)
                    coordinates.boundsInWindow().translate(Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat()))
                }?.takeIf { it.width > 0f && it.height > 0f }
                showImageViewer = true
            },
        contentScale = contentScale,
        alpha = alpha,
        alignment = alignment,
        onLoading = {
            loading = true
            ready = false
        },
        onSuccess = {
            ready = true
            loading = false
        },
        onError = {
            ready = true
            loading = false
        },
    )
    if (showImageViewer) {
        ImagePreviewDialog(images = listOf(model ?: ""), anchor = openedFrom) {
            showImageViewer = false
        }
    }
}
