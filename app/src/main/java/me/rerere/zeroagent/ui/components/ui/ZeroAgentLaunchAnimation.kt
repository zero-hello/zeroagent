package me.rerere.zeroagent.ui.components.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.pages.chat.MeshGradientBackground

/** App content initializes underneath; the overlay never delays intent handling. */
@Composable
fun ZeroAgentLaunchAnimation(enabled: Boolean) {
    var visible by rememberSaveable { mutableStateOf(enabled) }
    if (!visible) return
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
        visible = false
    }
    val p = progress.value
    MeshGradientBackground(Modifier.fillMaxSize().graphicsLayer {
        alpha = if (p < .7f) 1f else ((1f - p) / .3f).coerceIn(0f, 1f)
    }.clickable { visible = false }) {
        Column(Modifier.align(Alignment.Center).graphicsLayer {
            scaleX = .82f + .18f * p; scaleY = scaleX
            translationY = 24f * (1f - p)
        }, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.zeroagent_foreground), contentDescription = null, modifier = Modifier.size(128.dp))
            Text("ZeroAgent", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
