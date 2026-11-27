package me.rerere.zeroagent.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ZeroCapsuleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    pulse: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = ZeroTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .98f else 1f, ZeroMotion.snappy(), label = "Capsule press")
    Row(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 48.dp).clip(CircleShape)
            .background(if (pressed) colors.surfaceElevated else colors.surface)
            .border(1.dp, if (pressed || selected) colors.borderStrong else colors.borderSubtle, CircleShape)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) colors.textPrimary else colors.textMuted) {
            if (selected) ZeroStatusDot(animated = pulse)
            content()
        }
    }
}

@Composable
fun ZeroIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val colors = ZeroTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .92f else 1f, ZeroMotion.snappy(), label = "Icon press")
    Box(modifier.minimumInteractiveComponentSize().size(48.dp).clip(CircleShape).clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(38.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (pressed) colors.surfaceElevated else colors.surface, CircleShape)
            .border(1.dp, if (pressed) colors.borderStrong else colors.borderSubtle, CircleShape), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides if (enabled) colors.textPrimary else colors.textMuted, content = content)
        }
    }
}

@Composable
fun ZeroHairlineCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = ZeroTheme.colors
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = colors.surface,
        contentColor = colors.textPrimary, border = BorderStroke(1.dp, colors.borderSubtle)) {
        Column(content = content)
    }
}

@Composable
fun ZeroHairlineCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ZeroTheme.colors
    val shape = MaterialTheme.shapes.large
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .98f else 1f, ZeroMotion.snappy(), label = "Card press")
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape)
            .background(if (pressed) colors.surfaceElevated else colors.surface)
            .border(1.dp, if (pressed) colors.borderStrong else colors.borderSubtle, shape)
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick),
        content = content,
    )
}

@Composable
fun ZeroAnimatedSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    touchTarget: Dp = 48.dp,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = ZeroTheme.colors
    val feedback = rememberZeroFeedback()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val position by animateFloatAsState(if (checked) 1f else 0f, ZeroMotion.bouncy(), label = "Switch position")
    val press by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (press) 1.19f else 1f, ZeroMotion.snappy(), label = "Switch thumb")
    Box(
        modifier.sizeIn(minWidth = touchTarget, minHeight = touchTarget)
            .then(if (onCheckedChange != null) Modifier.toggleable(checked, interaction, null, enabled, Role.Switch) {
                feedback(ZeroFeedbackEvent.Toggle)
                onCheckedChange(it)
            } else Modifier.semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                if (!enabled) disabled()
            }),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(44.dp, 24.dp).graphicsLayer { alpha = if (enabled) 1f else .5f }
            .background(colors.surfaceElevated, CircleShape)
            .border(1.dp, if (checked) colors.borderStrong else colors.borderSubtle, CircleShape)) {
            Box(Modifier.align(Alignment.CenterStart).size(16.dp).graphicsLayer {
                translationX = (4.dp.toPx() + 20.dp.toPx() * position.coerceIn(-.05f, 1.05f)) *
                    if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) -1 else 1
                scaleX = scale
            }.background(if (checked) colors.accentOlive else colors.textMuted, CircleShape))
        }
    }
}

@Composable
fun ZeroStatusDot(modifier: Modifier = Modifier, animated: Boolean = false) {
    val colors = ZeroTheme.colors
    val pulse = if (animated) rememberInfiniteTransition(label = "Status").animateFloat(
        initialValue = .50f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "Pulse",
    ) else null
    Box(modifier.size(7.dp).graphicsLayer { alpha = pulse?.value ?: 1f }.background(colors.accentOlive, CircleShape))
}

@Composable
fun ZeroFoldedLogo(modifier: Modifier = Modifier, animate: Boolean = true) {
    val colors = ZeroTheme.colors
    val pulse = if (animate) rememberInfiniteTransition(label = "Brand mark").animateFloat(
        initialValue = .56f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "Olive dot",
    ) else null
    Box(modifier.drawWithCache {
        val u = size.minDimension / 100f
        val x = (size.width - 100 * u) / 2
        val y = (size.height - 100 * u) / 2
        val line = Path().apply {
            moveTo(x + 13 * u, y + 31 * u)
            lineTo(x + 13 * u, y + 18 * u)
            quadraticTo(x + 13 * u, y + 12 * u, x + 20 * u, y + 12 * u)
            lineTo(x + 57 * u, y + 12 * u)
            lineTo(x + 15 * u, y + 56 * u)
            quadraticTo(x + 13 * u, y + 59 * u, x + 13 * u, y + 62 * u)
            lineTo(x + 13 * u, y + 86 * u)
            lineTo(x + 85 * u, y + 14 * u)
            lineTo(x + 85 * u, y + 34 * u)
            quadraticTo(x + 85 * u, y + 39 * u, x + 81 * u, y + 43 * u)
            lineTo(x + 41 * u, y + 84 * u)
            quadraticTo(x + 37 * u, y + 88 * u, x + 31 * u, y + 88 * u)
            lineTo(x + 16 * u, y + 88 * u)
        }
        val stroke = Stroke(6 * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind {
            drawPath(line, colors.textPrimary, style = stroke)
            drawCircle(colors.accentOlive, 4.1f * u, Offset(x + 85 * u, y + 87 * u), alpha = pulse?.value ?: 1f)
        }
    })
}

@Composable
fun ZeroQuotePreviewCard(quote: String, closeDescription: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZeroTheme.colors
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface)
        .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp)).height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(colors.accentOlive))
        Text(quote, Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
            color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        ZeroIconButton(onClose, modifier = Modifier.padding(end = 4.dp)) {
            Text("×", Modifier.semantics { contentDescription = closeDescription }, style = MaterialTheme.typography.titleLarge)
        }
    }
}
