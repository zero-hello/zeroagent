package me.rerere.zeroagent.ui.components.ui

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import me.rerere.zeroagent.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.min

// Shared ZeroAgent indicator; respect the system animation setting.
@Composable
fun ZeroAgentThinkingIndicator(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val resolver = LocalContext.current.contentResolver
    val reduceMotion = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val angle = if (reduceMotion) 0f else {
        val transition = rememberInfiniteTransition(label = "ZeroAgent working")
        val rotation by transition.animateFloat(0f, 360f,
            infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "Rotating ZeroAgent ring")
        rotation
    }
    val workingDescription = stringResource(R.string.za6_working)
    Canvas(modifier.semantics { contentDescription = workingDescription }) {
        val side = min(size.width, size.height)
        val origin = Offset((size.width-side)/2, (size.height-side)/2)
        val stroke = side * 0.065f
        drawCircle(color.copy(alpha = 0.13f), side*0.44f, center, style = Stroke(stroke))
        rotate(angle, pivot = center) {
            drawArc(color, -90f, 105f, false, origin+Offset(side*.06f,side*.06f),
                Size(side*.88f,side*.88f), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val path = Path().apply {
            moveTo(origin.x+side*.33f,origin.y+side*.34f)
            lineTo(origin.x+side*.67f,origin.y+side*.34f)
            lineTo(origin.x+side*.33f,origin.y+side*.66f)
            lineTo(origin.x+side*.67f,origin.y+side*.66f)
        }
        drawPath(path,color,style = Stroke(stroke,cap = StrokeCap.Round))
    }
}
