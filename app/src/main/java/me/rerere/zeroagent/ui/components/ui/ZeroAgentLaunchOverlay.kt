package me.rerere.zeroagent.ui.components.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.R

/** Show once per cold launch, above the ready app; removal is owned by the caller. */
@Composable
fun ZeroAgentLaunchOverlay(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val finish = rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        finish.value()
    }
    Surface(Modifier.fillMaxSize().graphicsLayer {
        alpha = ((1f - progress.value) / .25f).coerceIn(0f, 1f)
    }, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = (progress.value / .35f).coerceIn(0f, 1f)
                scaleX = .94f + .06f * progress.value
                scaleY = scaleX
                translationY = (1f - progress.value) * 12.dp.toPx()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Image(painterResource(R.drawable.zeroagent_foreground), contentDescription = null, modifier = Modifier.size(112.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        }
    }
}
