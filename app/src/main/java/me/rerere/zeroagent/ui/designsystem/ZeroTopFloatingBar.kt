package me.rerere.zeroagent.ui.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ZeroTopFloatingBar(
    navigation: @Composable () -> Unit,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        navigation()
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { title() }
        actions()
    }
}
