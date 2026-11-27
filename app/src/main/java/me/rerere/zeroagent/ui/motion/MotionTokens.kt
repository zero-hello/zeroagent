package me.rerere.zeroagent.ui.motion

import androidx.compose.animation.core.*
import me.rerere.zeroagent.ui.designsystem.ZeroMotion

/** 動畫集中設定：微互動保留彈性，頁面有較清楚的進出過程。 */
object MotionTokens {
    const val PressScale = .98f // 按下縮放比例
    const val Damping = .82f // 空間彈簧阻尼
    const val Stiffness = 180f // 剛性，越小越舒緩
    const val ImageFadeMillis = 200 // 含記憶體快取命中的純透明度淡入
    const val PageFadeMillis = 320
    const val RouteTravelMillis = 360 // 換頁位移與預測返回共用的時間軸
    const val RouteFrontFadeMillis = 280 // 上層輕柔淡入，背後頁面持續顯示
    const val RouteOffsetFraction = .085f // 相對於視窗寬度的位移
    const val RoutePredictiveBackScale = .96f // 返回手勢中目前頁面的縮放
    const val ContentChangeAlpha = .76f // 明確展開／模式切換的淡入起點
    const val BackgroundFrameMillis = 33L // 上限約 30fps；背景暫停
    fun <T> spatial(): FiniteAnimationSpec<T> = ZeroMotion.fluid()
    fun <T> effects(): FiniteAnimationSpec<T> = ZeroMotion.snappy()
}
