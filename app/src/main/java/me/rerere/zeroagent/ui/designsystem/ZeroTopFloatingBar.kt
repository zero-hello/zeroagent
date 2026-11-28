package me.rerere.zeroagent.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun ZeroTopFloatingBar(
    navigation: @Composable () -> Unit,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = ZeroTheme.colors.background
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(background).statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterStart) { navigation() }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { title() }
            Row(Modifier.width(48.dp), horizontalArrangement = Arrangement.End) { actions() }
        }
        Box(Modifier.fillMaxWidth().height(14.dp).background(
            Brush.verticalGradient(listOf(background, background.copy(alpha = 0f))),
        ))
    }
}
