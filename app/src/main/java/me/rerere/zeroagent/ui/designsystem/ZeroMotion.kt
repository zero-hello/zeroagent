package me.rerere.zeroagent.ui.designsystem

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

object ZeroMotion {
    fun <T> snappy(): FiniteAnimationSpec<T> = spring(dampingRatio = .84f, stiffness = 520f)
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = .71f, stiffness = 440f)
    fun <T> fluid(): FiniteAnimationSpec<T> = spring(dampingRatio = .91f, stiffness = 280f)
}
