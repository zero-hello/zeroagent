package me.rerere.zeroagent.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.ui.rememberMotionEnabled

enum class MotionArtwork { Empty, Success, Selection }

/** 小型原創 Lottie 向量資源；遵守系統移除動畫、離開前景暫停。 */
@Composable
fun MotionIllustration(artwork: MotionArtwork, modifier: Modifier = Modifier, playing: Boolean = true,
    description: String? = null) {
    val motion = rememberMotionEnabled()
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(when (artwork) {
        MotionArtwork.Empty -> R.raw.motion_empty
        MotionArtwork.Success -> R.raw.motion_success
        MotionArtwork.Selection -> R.raw.motion_selection
    }))
    val progress by animateLottieCompositionAsState(composition, isPlaying = playing && motion,
        iterations = if (artwork == MotionArtwork.Empty) LottieConstants.IterateForever else 1,
        restartOnPlay = true)
    LottieAnimation(composition, progress = { if (motion) progress else .99f },
        modifier = modifier.semantics { if (description != null) contentDescription = description })
}

@Composable
fun MotionNavigationIcon(selected: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.size(28.dp).statePulse(selected), contentAlignment = Alignment.Center) {
        content()
        if (selected) MotionIllustration(MotionArtwork.Selection, Modifier.size(28.dp))
    }
}
