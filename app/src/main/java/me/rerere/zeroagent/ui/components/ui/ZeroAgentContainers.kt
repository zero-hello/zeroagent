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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Material owns layout, gestures and semantics; these surfaces share the application's glass. */
@Composable
fun ZeroAgentScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    val glass = LocalZeroAgentGlassState.current != null
    val body = rememberHazeState()
    MaterialScaffold(
        modifier = modifier,
        topBar = { Box(Modifier.fillMaxWidth().then(if (glass) Modifier.zeroAgentGlass(body, radius = 0) else Modifier)) { topBar() } },
        bottomBar = { Box(Modifier.fillMaxWidth().then(if (glass) Modifier.zeroAgentGlass(body, radius = 0) else Modifier)) { bottomBar() } },
        snackbarHost = snackbarHost, floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = if (glass) Color.Transparent else containerColor,
        contentColor = if (glass) MaterialTheme.colorScheme.onSurface else contentColor,
        contentWindowInsets = contentWindowInsets,
    ) { padding ->
        Box(Modifier.fillMaxSize().then(if (glass) Modifier.hazeSource(body) else Modifier)) { content(padding) }
    }
}

@Composable
private fun CardColors.forGlass(): CardColors = if (LocalZeroAgentGlassState.current != null)
    copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent) else this

@Composable
fun ZeroAgentCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) = MaterialCard(
    modifier = modifier.zeroAgentGlass(backgroundColor = colors.containerColor, shape = shape),
    shape = shape, colors = colors.forGlass(), elevation = elevation, border = border, content = content,
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
) = MaterialCard(
    onClick = onClick,
    modifier = modifier.zeroAgentGlass(backgroundColor = if (enabled) colors.containerColor else colors.disabledContainerColor, shape = shape),
    enabled = enabled, shape = shape, colors = colors.forGlass(), elevation = elevation,
    border = border, interactionSource = interactionSource, content = content,
)

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
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalZeroAgentOverlayGlassState.current != null
    MaterialBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.zeroAgentGlass(backgroundColor = containerColor, shape = shape, overlay = true),
        sheetState = sheetState, sheetMaxWidth = sheetMaxWidth, sheetGesturesEnabled = sheetGesturesEnabled,
        shape = shape, containerColor = if (glass) Color.Transparent else containerColor,
        contentColor = if (contentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else contentColor,
        tonalElevation = if (glass) 0.dp else tonalElevation, scrimColor = scrimColor,
        dragHandle = dragHandle, contentWindowInsets = contentWindowInsets, properties = properties, content = content,
    )
}
