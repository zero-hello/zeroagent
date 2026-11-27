package me.rerere.zeroagent.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
private fun zeroTextFieldColors(): TextFieldColors {
    val c = ZeroTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
        focusedBorderColor = c.borderStrong, unfocusedBorderColor = c.borderSubtle,
        focusedLabelColor = c.textPrimary, unfocusedLabelColor = c.textSecondary,
        cursorColor = c.accentOlive,
    )
}

@Composable
fun ZeroTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, readOnly: Boolean = false, textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null, suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null, isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = MaterialTheme.shapes.medium, colors: TextFieldColors = zeroTextFieldColors(),
) = OutlinedTextField(value, onValueChange, modifier, enabled = enabled, readOnly = readOnly,
    textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
    prefix = prefix, suffix = suffix, supportingText = supportingText, isError = isError,
    visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
    singleLine = singleLine, maxLines = maxLines, minLines = minLines, interactionSource = interactionSource,
    shape = shape, colors = colors)

@Composable
fun ZeroTextField(
    value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, readOnly: Boolean = false, textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null, suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null, isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = MaterialTheme.shapes.medium, colors: TextFieldColors = zeroTextFieldColors(),
) = OutlinedTextField(value, onValueChange, modifier, enabled = enabled, readOnly = readOnly,
    textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
    prefix = prefix, suffix = suffix, supportingText = supportingText, isError = isError,
    visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
    singleLine = singleLine, maxLines = maxLines, minLines = minLines, interactionSource = interactionSource,
    shape = shape, colors = colors)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroSlider(
    value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0, onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(), interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val dragging by source.collectIsDraggedAsState()
    val scale by animateFloatAsState(if (dragging) 1.18f else 1f, ZeroMotion.bouncy(), label = "Slider thumb")
    val c = ZeroTheme.colors
    val feedback = rememberZeroFeedback()
    Slider(value = value, onValueChange = {
        if (steps > 0 && it != value) feedback(ZeroFeedbackEvent.Step)
        onValueChange(it)
    }, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, valueRange = valueRange,
        steps = steps, onValueChangeFinished = onValueChangeFinished, interactionSource = source, colors = colors,
        thumb = {
            Box(Modifier.size(16.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .background(c.surface, CircleShape).border(1.dp, c.borderStrong, CircleShape), contentAlignment = Alignment.Center) {
                ZeroStatusDot()
            }
        },
        track = { SliderDefaults.Track(sliderState = it, enabled = enabled, colors = SliderDefaults.colors(
            activeTrackColor = c.textPrimary, inactiveTrackColor = c.borderSubtle,
        ), modifier = Modifier.height(2.dp)) },
    )
}

@Composable
fun ZeroProgressIndicator(
    modifier: Modifier = Modifier, color: Color = ZeroTheme.colors.accentOlive,
    strokeWidth: Dp = 2.dp, trackColor: Color = ZeroTheme.colors.borderSubtle,
    strokeCap: StrokeCap = StrokeCap.Round,
) = CircularProgressIndicator(modifier, color, strokeWidth, trackColor, strokeCap)

@Composable
fun ZeroProgressIndicator(
    progress: () -> Float, modifier: Modifier = Modifier, color: Color = ZeroTheme.colors.accentOlive,
    strokeWidth: Dp = 2.dp, trackColor: Color = ZeroTheme.colors.borderSubtle,
    strokeCap: StrokeCap = StrokeCap.Round,
) = CircularProgressIndicator(progress, modifier, color, strokeWidth, trackColor, strokeCap)

@Composable
fun ZeroToast(data: SnackbarData, modifier: Modifier = Modifier) {
    val c = ZeroTheme.colors
    Snackbar(data, modifier.border(1.dp, c.borderSubtle, CircleShape), shape = CircleShape,
        containerColor = c.surface, contentColor = c.textPrimary, actionColor = c.textPrimary,
        actionContentColor = c.textPrimary, dismissActionContentColor = c.textSecondary)
}

@Composable
fun ZeroTextField(
    state: androidx.compose.foundation.text.input.TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: (@Composable () -> Unit)? = null,
    lineLimits: androidx.compose.foundation.text.input.TextFieldLineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.Default,
    textStyle: TextStyle = LocalTextStyle.current,
) = OutlinedTextField(state = state, modifier = modifier, enabled = enabled, readOnly = readOnly,
    placeholder = placeholder, lineLimits = lineLimits, textStyle = textStyle,
    shape = MaterialTheme.shapes.medium, colors = zeroTextFieldColors())

enum class ZeroToastTone { Neutral, Success, Error }

@Composable
fun ZeroToast(
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    tone: ZeroToastTone = ZeroToastTone.Neutral,
    content: @Composable () -> Unit,
) {
    val colors = ZeroTheme.colors
    Surface(modifier, shape = CircleShape, color = colors.surface, contentColor = colors.textPrimary,
        border = BorderStroke(1.dp, colors.borderSubtle), shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val dot = when (tone) {
                ZeroToastTone.Neutral -> colors.textSecondary
                ZeroToastTone.Success -> colors.accentOlive
                ZeroToastTone.Error -> colors.destructive
            }
            Box(Modifier.size(6.dp).background(dot, CircleShape))
            Box(Modifier.weight(1f)) { content() }
            action?.invoke()
        }
    }
}
