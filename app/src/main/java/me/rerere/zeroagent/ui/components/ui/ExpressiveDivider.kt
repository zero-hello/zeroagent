package me.rerere.zeroagent.ui.components.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A shared, visible boundary for option groups in both light and dark themes. */
@Composable
fun ExpressiveDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = MaterialTheme.colorScheme.outline,
    horizontalInset: Dp = 16.dp,
) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = horizontalInset),
        thickness = thickness,
        color = color,
    )
}
