package me.rerere.zeroagent.ui.motion

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.sin

internal data class ReplyMotion(val mode: String, val paws: Boolean)
internal val LocalConversationReplyMotion = staticCompositionLocalOf<ReplyMotion?> { null }
internal val LocalReplyMotion = staticCompositionLocalOf<ReplyMotion?> { null }

private const val RevealMillis = 225L
private const val PawMillis = 440L
private const val MinSampleMillis = 55L
private const val SoftLiftPixels = 3f
private const val PawCadenceMillis = 730L
private const val MaxRanges = 4
private const val MaxRevealCharacters = 120

internal fun trailingRevealStart(previous: String, next: String): Int? {
    if (next.length <= previous.length || !next.startsWith(previous)) return null
    var start = maxOf(previous.length, next.length - MaxRevealCharacters)
    if (next[start].isLowSurrogate()) start++
    return start.takeIf { it < next.length }
}

private data class GlyphRange(val start: Int, val end: Int, val appearedAt: Long, val paw: Boolean)

internal data class ReplyTextMotion(val modifier: Modifier, val onTextLayout: (TextLayoutResult) -> Unit)

/** Stable text layout and selection stay with Markdown. Only the trailing ranges paint through a transient layer. */
@Composable
internal fun rememberReplyTextMotion(text: String, modifier: Modifier = Modifier): ReplyTextMotion {
    val motion = LocalReplyMotion.current ?: return ReplyTextMotion(modifier, {})
    val latestText by rememberUpdatedState(text)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var ranges by remember { mutableStateOf<List<GlyphRange>>(emptyList()) }
    var now by remember { mutableLongStateOf(0L) }
    val pawColor = MaterialTheme.colorScheme.tertiary
    LaunchedEffect(motion) {
        var previous = ""
        var lastSample = -MinSampleMillis
        var lastPaw = -PawCadenceMillis
        while (true) {
            val instant = SystemClock.uptimeMillis()
            val liveText = latestText
            if (instant - lastSample >= MinSampleMillis && liveText != previous) {
                val end = liveText.length
                val start = trailingRevealStart(previous, liveText)
                if (start != null) {
                    val paw = motion.paws && liveText.codePointCount(start, end) >= 3 && instant - lastPaw >= PawCadenceMillis
                    if (paw) lastPaw = instant
                    ranges = (ranges + GlyphRange(start, end, instant, paw)).takeLast(MaxRanges)
                } else {
                    // A Markdown delimiter can change the parsed text. Leave restyled text at rest.
                    ranges = emptyList()
                }
                previous = liveText
                lastSample = instant
            }
            val active = ranges.filter { instant - it.appearedAt < if (it.paw) PawMillis else RevealMillis }
            if (active.size != ranges.size) ranges = active
            if (active.isNotEmpty()) {
                now = instant
                withFrameNanos { }
            } else if (liveText != previous) {
                delay((MinSampleMillis - (instant - lastSample)).coerceAtLeast(1L))
            } else {
                snapshotFlow { latestText }.first { it != previous }
            }
        }
    }
    return ReplyTextMotion(modifier.drawWithContent {
        val textDrawScope = this
        val positioned = layout
        val current = ranges
        if (positioned == null || current.isEmpty() || positioned.layoutInput.text.text != latestText) {
            drawContent()
            return@drawWithContent
        }
        val timestamp = now
        val pending = current.filter { timestamp - it.appearedAt < RevealMillis }
        val paths = pending.map { it to positioned.getPathForRange(it.start, it.end) }
        if (paths.isEmpty()) drawContent() else {
            drawIntoCanvas { canvas ->
                canvas.save()
                try {
                    paths.forEach { (_, path) -> canvas.clipPath(path, ClipOp.Difference) }
                    drawContent()
                } finally { canvas.restore() }
            }
            paths.forEach { (range, path) ->
                val progress = ((timestamp - range.appearedAt).toFloat() / RevealMillis).coerceIn(0f, 1f)
                val eased = FastOutSlowInEasing.transform(progress)
                val lift = if (motion.mode == "soft") (1f - eased) * SoftLiftPixels else 0f
                translate(top = lift) {
                    clipPath(path) {
                        val painter = Paint().apply { alpha = .08f + .92f * eased }
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(path.getBounds(), painter)
                            try { textDrawScope.drawContent() } finally { canvas.restore() }
                        }
                    }
                }
            }
        }
        current.filter(GlyphRange::paw).forEach { range ->
            val progress = ((timestamp - range.appearedAt).toFloat() / PawMillis).coerceIn(0f, 1f)
            val offset = (range.end - 1).coerceAtLeast(range.start)
            val glyph = positioned.getBoundingBox(offset)
            val radius = 1.4.dp.toPx()
            val x = (glyph.right + 5.dp.toPx()).coerceIn(radius * 3f, (size.width - radius * 3f).coerceAtLeast(radius * 3f))
            val center = Offset(x, glyph.top + 1.dp.toPx() - progress * 8.dp.toPx())
            val tint = pawColor.copy(alpha = .24f * (1f - progress))
            rotate(-14 + 20 * progress, center) {
                val padCenter = Offset(center.x, center.y + radius)
                drawOval(tint, topLeft = Offset(padCenter.x - 1.35f * radius, padCenter.y - radius),
                    size = androidx.compose.ui.geometry.Size(2.7f * radius, 2f * radius))
                for (toe in 0..3) {
                    drawCircle(tint, .64f * radius, Offset(center.x + (toe - 1.5f) * 1.22f * radius,
                        center.y - (if (toe == 0 || toe == 3) 0.6f else 1.25f) * radius))
                }
            }
        }
    }, { layout = it })
}

@Composable
internal fun ReplyWaitingDots(label: String, motionEnabled: Boolean) {
    var tick by remember { mutableLongStateOf(0L) }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    LaunchedEffect(motionEnabled) {
        val start = SystemClock.uptimeMillis()
        while (motionEnabled) {
            withFrameNanos { }
            tick = SystemClock.uptimeMillis() - start
        }
    }
    Canvas(Modifier.size(width = 32.dp, height = 20.dp).semantics { contentDescription = label }) {
        val radius = 2.dp.toPx()
        repeat(3) { dot ->
            val opacity = if (motionEnabled) .62f + .26f * sin((tick / 850.0 - .17 * dot) * 2 * PI).toFloat() else .8f
            drawCircle(color.copy(alpha = opacity), radius, Offset(size.width * (.25f + .25f * dot), size.height * .5f))
        }
    }
}

/** Structured Markdown enters as a unit so code, table cells and list alignment stay intact. */
@Composable
internal fun ReplyStructuredBlock(content: @Composable () -> Unit) {
    val motion = LocalReplyMotion.current
    val opacity = remember { Animatable(if (motion == null) 1f else .58f) }
    LaunchedEffect(motion != null) {
        if (motion == null) opacity.snapTo(1f)
        else opacity.animateTo(1f, tween(RevealMillis.toInt()))
    }
    val modifier = if (motion == null) Modifier else Modifier.graphicsLayer { alpha = opacity.value }
    androidx.compose.foundation.layout.Box(modifier) {
        content()
    }
}