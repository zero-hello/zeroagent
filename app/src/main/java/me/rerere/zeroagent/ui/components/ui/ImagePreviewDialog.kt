package me.rerere.zeroagent.ui.components.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalView
import me.rerere.zeroagent.ui.designsystem.ZeroMotion
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import me.rerere.zeroagent.ui.designsystem.ZeroIconButton as IconButton
import me.rerere.zeroagent.ui.designsystem.ZeroTheme
import me.rerere.zeroagent.ui.designsystem.rememberZeroPredictiveDismiss
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.res.stringResource
import me.rerere.hugeicons.stroke.Cancel01
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.jvziyaoyao.scale.zoomable.pager.PagerGestureScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.stroke.Share04
import java.io.File
import kotlin.math.abs
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.compose.rememberAsyncImagePainter
import com.dokar.sonner.ToastType
import com.jvziyaoyao.scale.image.pager.ImagePager
import com.jvziyaoyao.scale.zoomable.pager.rememberZoomablePagerState
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Download01
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.files.FilesManager
import me.rerere.zeroagent.ui.context.LocalToaster
import org.koin.compose.koinInject

@Composable
fun ImagePreviewDialog(
    images: List<String>,
    initialPage: Int = 0,
    anchor: Rect? = null,
    onDismissRequest: () -> Unit,
) {
    if (images.isEmpty()) return
    val context = LocalContext.current
    val localizedResources = androidx.compose.ui.platform.LocalResources.current
    val filesManager: FilesManager = koinInject()
    val state = rememberZoomablePagerState(initialPage = initialPage.coerceIn(images.indices)) { images.size }
    val toaster = LocalToaster.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dismiss by rememberUpdatedState(onDismissRequest)
    val scope = rememberCoroutineScope()
    val spring = remember(anchor) { Animatable(if (anchor == null) 1f else 0f) }
    var closing by remember { mutableStateOf(false) }
    var windowOffset by remember { mutableStateOf(Offset.Zero) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                if (anchor != null) spring.animateTo(0f, ZeroMotion.snappy())
                dismiss()
            }
        }
    }
    LaunchedEffect(anchor) { if (anchor != null) spring.animateTo(1f, ZeroMotion.fluid()) }
    var controlsVisible by remember { mutableStateOf(true) }
    var sharing by remember { mutableStateOf(false) }
    val gestures = remember { PagerGestureScope(onTap = { controlsVisible = !controlsVisible }) }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val back = rememberZeroPredictiveDismiss(onDismiss = close)
        val view = LocalView.current
        Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
            val screen = IntArray(2).also(view::getLocationOnScreen)
            val window = IntArray(2).also(view::getLocationInWindow)
            windowOffset = coordinates.boundsInWindow().topLeft + Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
        }) {
            val paper = ZeroTheme.colors.background
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = spring.value.coerceIn(0f, 1f) * (1f - .4f * back.value) }.background(paper))
            ImagePager(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    if (anchor != null && size.width > 0 && size.height > 0) {
                        val remaining = 1f - spring.value.coerceIn(0f, 1f) * (1f - .6f * back.value)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = 1f + (anchor.width / size.width - 1f) * remaining
                        scaleY = 1f + (anchor.height / size.height - 1f) * remaining
                        translationX = (anchor.left - windowOffset.x) * remaining
                        translationY = (anchor.top - windowOffset.y) * remaining
                    } else {
                        scaleX = 1f - .08f * back.value
                        scaleY = scaleX
                        alpha = 1f - .25f * back.value
                    }
                }.pointerInput(state) {
                    awaitEachGesture {
                        val start = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } > 1 || (state.zoomableViewState.value?.scale?.value ?: 1f) > 1.01f) break
                            val change = event.changes.firstOrNull { it.id == start.id } ?: break
                            val dx = abs(change.position.x - start.position.x)
                            val dy = change.position.y - start.position.y
                            if (dx > viewConfiguration.touchSlop && dx >= abs(dy) || dy < -viewConfiguration.touchSlop) break
                            if (dy > 96.dp.toPx() && dy > dx * 1.5f) {
                                change.consume()
                                close()
                                break
                            }
                        } while (change.pressed)
                    }
                },
                detectGesture = gestures,
                pagerState = state,
                imageLoader = { index ->
                    val painter = rememberAsyncImagePainter(images[index])
                    return@ImagePager Pair(painter, painter.intrinsicSize)
                },
            )

            AnimatedVisibility(
                visible = controlsVisible && !closing,
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().zIndex(1f),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
            Row(
                modifier = Modifier
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        lifecycleOwner.lifecycleScope.launch {
                            runCatching {
                                toaster.show(localizedResources.getString(R.string.image_preview_saving))
                                val imgUrl = images[state.currentPage]
                                filesManager.saveMessageImage(context, imgUrl)
                                toaster.show(
                                    message = localizedResources.getString(R.string.image_preview_saved),
                                    type = ToastType.Success,
                                )
                            }.onFailure {
                                it.printStackTrace()
                                toaster.show(
                                    message = it.toString(),
                                    type = ToastType.Error
                                )
                            }
                        }
                    }
                ) {
                    Icon(HugeIcons.Download01, stringResource(R.string.image_preview_saving))
                }
                IconButton(enabled = !sharing, onClick = {
                    val source = images.getOrNull(state.currentPage) ?: return@IconButton
                    sharing = true
                    lifecycleOwner.lifecycleScope.launch {
                        try {
                            sharePreviewImage(context, source)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            toaster.show(localizedResources.getString(R.string.zero_v6_image_failed), type = ToastType.Error)
                        } finally {
                            sharing = false
                        }
                    }
                }) { Icon(HugeIcons.Share04, stringResource(R.string.share)) }
                IconButton(onClick = close) {
                    Icon(HugeIcons.Cancel01, stringResource(R.string.quick_chat_close))
                }
            }
            }
        }
    }
}

private suspend fun sharePreviewImage(context: Context, source: String) {
    val uri = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context).data(source).size(2048).allowHardware(false).build()
        val image = (context.imageLoader.execute(request) as? SuccessResult)?.image ?: error("Could not load image")
        val directory = File(context.cacheDir, "chat-image-share").apply { mkdirs() }
        val expired = System.currentTimeMillis() - 24 * 60 * 60 * 1_000L
        directory.listFiles()?.filter { it.isFile && it.lastModified() < expired }?.forEach { it.delete() }
        val file = File.createTempFile("ZeroAgent-", ".png", directory)
        try {
            val bitmap = image.asDrawable(context.resources).toBitmap()
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("ZeroAgent", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, context.getString(R.string.share)))
}
