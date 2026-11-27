package me.rerere.zeroagent.ui.pages.lab

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.lab.LabPreferences
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold
import me.rerere.zeroagent.ui.components.ui.rememberMotionEnabled
import me.rerere.zeroagent.ui.context.LocalNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroAgentLabPage() {
    val context = LocalContext.current
    val enabled by remember(context) { LabPreferences.flow(context, "memory_drawer", "false") }
        .collectAsStateWithLifecycle(LabPreferences.get(context, "memory_drawer", "false"))
    var opened by rememberSaveable { mutableStateOf(false) }
    val showDrawer = opened && enabled == "true"
    val motion = rememberMotionEnabled()
    BackHandler(showDrawer) { opened = false }
    ZeroAgentScaffold(topBar = {
        TopAppBar(
            title = { Text(if (showDrawer) "記憶抽屜" else "ZeroAgent Lab") },
            navigationIcon = { if (showDrawer) BackButton(onBack = { opened = false }) else BackButton() },
        )
    }) { padding ->
        androidx.compose.animation.AnimatedContent(
            targetState = showDrawer,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = { me.rerere.zeroagent.ui.motion.sharedAxisTransition(targetState, motion) },
            label = "Lab",
        ) { drawer ->
            if (drawer) {
                MemoryDrawerWorkspace(Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                LabFlask()
                                Text("在這裡管理 ZeroAgent 記住的資料。", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        me.rerere.zeroagent.ui.components.ui.CardGroup {
                            item(
                                headlineContent = { Text("記憶抽屜") },
                                supportingContent = { Text("查看、修改或刪除記憶。關閉抽屜會保留已保存的記憶。") },
                                trailingContent = {
                                    Switch(checked = enabled == "true", onCheckedChange = {
                                        LabPreferences.set(context, "memory_drawer", it.toString())
                                        if (!it) {
                                            opened = false
                                            me.rerere.zeroagent.data.lab.LabMemoryReview.clearOneTurnChanges()
                                        }
                                    }, modifier = Modifier.semantics { contentDescription = "記憶抽屜" })
                                },
                            )
                        }
                        AnimatedVisibility(enabled == "true") {
                            FilledTonalButton(onClick = { opened = true }) { Text("開啟記憶抽屜") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabFlask() {
    val motion = rememberMotionEnabled()
    val phase = if (motion) rememberInfiniteTransition(label = "lab bubbles").animateFloat(
        initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(2500), RepeatMode.Reverse), label = "bubbles",
    ) else null
    val outline = MaterialTheme.colorScheme.onSecondaryContainer
    val liquid = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.size(96.dp).semantics { contentDescription = "實驗燒瓶" }) {
        val unit = size.width / 100f
        val flask = Path().apply {
            moveTo(38 * unit, 12 * unit); lineTo(62 * unit, 12 * unit)
            moveTo(42 * unit, 13 * unit); lineTo(42 * unit, 38 * unit)
            lineTo(20 * unit, 75 * unit); quadraticTo(12 * unit, 88 * unit, 27 * unit, 88 * unit)
            lineTo(73 * unit, 88 * unit); quadraticTo(88 * unit, 88 * unit, 80 * unit, 75 * unit)
            lineTo(58 * unit, 38 * unit); lineTo(58 * unit, 13 * unit)
        }
        val fill = Path().apply {
            moveTo(34 * unit, 58 * unit); quadraticTo(49 * unit, (64 - 4 * (phase?.value ?: 0f)) * unit, 66 * unit, 58 * unit)
            lineTo(78 * unit, 78 * unit); quadraticTo(80 * unit, 84 * unit, 73 * unit, 84 * unit)
            lineTo(27 * unit, 84 * unit); quadraticTo(20 * unit, 84 * unit, 22 * unit, 78 * unit); close()
        }
        drawPath(fill, liquid.copy(alpha = .5f))
        drawPath(flask, outline, style = Stroke(4 * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(liquid, 3.2f * unit, Offset(51 * unit, (53 - 8 * (phase?.value ?: 0f)) * unit))
        drawCircle(liquid.copy(alpha = .7f), 2f * unit, Offset(44 * unit, (69 - 6 * (phase?.value ?: 0f)) * unit))
    }
}
