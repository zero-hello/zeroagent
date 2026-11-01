package me.rerere.zeroagent.ui.components.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.ui.context.LocalSettings
import me.rerere.zeroagent.ui.theme.LocalDarkMode

enum class SwitchSize {
    Small,
    Medium,
    Large
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: SwitchSize = SwitchSize.Medium,
    enabled: Boolean = true,
    trackColor: Color = MaterialTheme.colorScheme.primary,
    trackColorUnchecked: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    thumbColor: Color = MaterialTheme.colorScheme.onPrimary,
    thumbColorUnchecked: Color = MaterialTheme.colorScheme.outline,
    interactionSource: MutableInteractionSource? = null,
) {
    val displaySetting = LocalSettings.current.displaySetting
    val isLiquid = displaySetting.advancedMaterialEnabled
    val isDark = LocalDarkMode.current

    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by actualInteractionSource.collectIsPressedAsState()

    val dimensions = when (size) {
        SwitchSize.Small -> SwitchDimensions(
            trackWidth = if (isLiquid) 42.dp else 40.dp,
            trackHeight = if (isLiquid) 26.dp else 24.dp,
            thumbSize = if (isLiquid) 22.dp else (if (checked) 18.dp else 12.dp),
            thumbPadding = if (isLiquid) 2.dp else (if (checked) 3.dp else 6.dp),
        )

        SwitchSize.Medium -> SwitchDimensions(
            trackWidth = if (isLiquid) 51.dp else 52.dp,
            trackHeight = if (isLiquid) 31.dp else 32.dp,
            thumbSize = if (isLiquid) 27.dp else (if (checked) 24.dp else 16.dp),
            thumbPadding = if (isLiquid) 2.dp else (if (checked) 4.dp else 8.dp),
        )

        SwitchSize.Large -> SwitchDimensions(
            trackWidth = if (isLiquid) 60.dp else 60.dp,
            trackHeight = if (isLiquid) 36.dp else 36.dp,
            thumbSize = if (isLiquid) 32.dp else (if (checked) 28.dp else 20.dp),
            thumbPadding = if (isLiquid) 2.dp else (if (checked) 4.dp else 8.dp),
        )
    }

    val thumbStretch by animateDpAsState(
        targetValue = if (isLiquid && isPressed) 5.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "thumbStretch"
    )

    val currentThumbWidth = dimensions.thumbSize + thumbStretch

    val targetOffset = if (checked) {
        dimensions.trackWidth - currentThumbWidth - dimensions.thumbPadding
    } else {
        dimensions.thumbPadding
    }

    val thumbOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = if (isLiquid) spring(dampingRatio = 0.82f, stiffness = 480f) else tween(durationMillis = 150),
        label = "thumbOffset"
    )

    val trackShape = RoundedCornerShape(percent = 50)

    val currentTrackColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        isLiquid -> {
            if (checked) trackColor
            else (if (isDark) Color(0x35787880) else Color(0x22787880))
        }
        checked -> trackColor
        else -> trackColorUnchecked
    }

    val currentThumbColor = when {
        !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
        isLiquid -> Color.White
        checked -> thumbColor
        else -> thumbColorUnchecked
    }

    val trackBorderModifier = if (isLiquid) {
        Modifier.border(
            width = 0.75.dp,
            brush = Brush.linearGradient(
                colors = if (checked) {
                    listOf(
                        Color.White.copy(alpha = 0.40f),
                        Color.White.copy(alpha = 0.10f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.15f)
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.30f else 0.45f),
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent,
                        Color.White.copy(alpha = if (isDark) 0.15f else 0.25f)
                    )
                },
                start = Offset(0f, 0f),
                end = Offset.Infinite,
            ),
            shape = trackShape
        )
    } else if (!checked) {
        Modifier.border(
            width = 2.dp,
            color = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
            shape = trackShape
        )
    } else Modifier

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(
                if (onCheckedChange != null) Modifier.toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    interactionSource = actualInteractionSource,
                    indication = null,
                    onValueChange = onCheckedChange,
                ) else Modifier.semantics { toggleableState = ToggleableState(checked) }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = dimensions.trackWidth, height = dimensions.trackHeight)
                .then(trackBorderModifier)
                .clip(trackShape)
                .background(currentTrackColor)
                .then(
                    if (isLiquid && checked) {
                        Modifier.drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.20f),
                                        Color.Transparent,
                                    ),
                                    startY = 0f,
                                    endY = this.size.height * 0.5f,
                                )
                            )
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(width = currentThumbWidth, height = dimensions.thumbSize)
                    .shadow(
                        elevation = if (enabled) (if (isLiquid) 3.dp else 2.dp) else 0.dp,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .background(currentThumbColor)
                    .then(
                        if (isLiquid) {
                            Modifier.drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.55f),
                                            Color.Transparent
                                        ),
                                        startY = 0f,
                                        endY = this.size.height * 0.6f
                                    )
                                )
                            }
                        } else Modifier
                    )
            )
        }
    }
}

private data class SwitchDimensions(
    val trackWidth: Dp,
    val trackHeight: Dp,
    val thumbSize: Dp,
    val thumbPadding: Dp
)

@Composable
@Preview(showBackground = true)
private fun SwitchPreview() {
    Column(
        modifier = Modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var checkedSmall by remember { mutableStateOf(true) }
        var checkedMedium by remember { mutableStateOf(true) }
        var checkedLarge by remember { mutableStateOf(false) }

        Switch(
            checked = checkedSmall,
            onCheckedChange = { checkedSmall = it },
            size = SwitchSize.Small
        )

        Switch(
            checked = checkedMedium,
            onCheckedChange = { checkedMedium = it },
            size = SwitchSize.Medium
        )

        Switch(
            checked = checkedLarge,
            onCheckedChange = { checkedLarge = it },
            size = SwitchSize.Large
        )
    }
}
