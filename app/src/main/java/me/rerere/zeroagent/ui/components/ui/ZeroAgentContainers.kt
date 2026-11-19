package me.rerere.zeroagent.ui.components.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.ModalBottomSheet as MaterialBottomSheet
import androidx.compose.material3.Scaffold as MaterialScaffold
import me.rerere.zeroagent.ui.motion.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared Material surfaces preserve official layout, motion, gestures and accessibility. */
@Composable
fun ZeroAgentScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = if (containerColor == Color.Transparent) MaterialTheme.colorScheme.onSurface else contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {

    val route = if (LocalMotionContainerClaimed.current) null else LocalMotionRoute.current
    val surfaceModifier = modifier.motionBounds(route)
    CompositionLocalProvider(LocalMotionContainerClaimed provides true) {
    MaterialScaffold(
        modifier = surfaceModifier, topBar = topBar, bottomBar = bottomBar,
        snackbarHost = snackbarHost, floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor, contentColor = contentColor,
        contentWindowInsets = contentWindowInsets, content = content,
    )
    }
}

@Composable
fun ZeroAgentCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) = MaterialCard(
    modifier = modifier,
    shape = shape, colors = colors, elevation = elevation, border = border, content = content,
)

@Composable
fun ZeroAgentCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    MaterialCard(onClick = onClick, modifier = modifier.pressScale(interactions, enabled),
        enabled = enabled, shape = shape, colors = colors, elevation = elevation,
        border = border, interactionSource = interactions, content = content)
}

@Composable
fun ZeroAgentOutlinedCard(
    modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.extraLarge,
    colors: CardColors = CardDefaults.outlinedCardColors(), elevation: CardElevation = CardDefaults.outlinedCardElevation(),
    border: BorderStroke = CardDefaults.outlinedCardBorder(), content: @Composable ColumnScope.() -> Unit,
) = ZeroAgentCard(modifier, shape, colors, elevation, border, content)

@Composable
fun ZeroAgentOutlinedCard(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.extraLarge, colors: CardColors = CardDefaults.outlinedCardColors(),
    elevation: CardElevation = CardDefaults.outlinedCardElevation(), border: BorderStroke = CardDefaults.outlinedCardBorder(enabled),
    interactionSource: MutableInteractionSource? = null, content: @Composable ColumnScope.() -> Unit,
) = ZeroAgentCard(onClick, modifier, enabled, shape, colors, elevation, border, interactionSource, content)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroAgentModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = if (containerColor == Color.Transparent) MaterialTheme.colorScheme.onSurface else contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    MaterialBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState, sheetMaxWidth = sheetMaxWidth, sheetGesturesEnabled = sheetGesturesEnabled,
        shape = shape, containerColor = containerColor,
        contentColor = if (contentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else contentColor,
        tonalElevation = tonalElevation, scrimColor = scrimColor,
        dragHandle = dragHandle, contentWindowInsets = contentWindowInsets, properties = properties, content = content,
    )
}
