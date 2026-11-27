package me.rerere.zeroagent.ui.pages.developer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import me.rerere.zeroagent.ui.components.ui.ZeroAgentModalBottomSheet
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold
import me.rerere.zeroagent.ui.designsystem.*
import me.rerere.zeroagent.ui.theme.LocalDarkMode
import me.rerere.zeroagent.ui.theme.JetbrainsMono

@Composable
internal fun ZeroUiTestScreen(onBack: () -> Unit) {
    val systemDark = LocalDarkMode.current
    var dark by rememberSaveable { mutableStateOf(systemDark) }
    var toggle by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var slider by rememberSaveable { mutableFloatStateOf(.5f) }
    var quote by rememberSaveable { mutableStateOf(true) }
    var dialog by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var sampleKey by remember { mutableStateOf("") }
    var hidden by rememberSaveable { mutableStateOf(true) }
    var toast by remember { mutableStateOf<ZeroToastTone?>(null) }
    var lastToast by remember { mutableStateOf(ZeroToastTone.Neutral) }
    val feedback = rememberZeroFeedback()
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }
    BackHandler(enabled = !dialog && !sheet, onBack = onBack)
    ZeroTheme.Provide(dark) {
        val colors = ZeroTheme.colors
        ZeroAgentScaffold(topBar = {
            ZeroTopFloatingBar(navigation = { ZeroIconButton(onBack) { Text("‹") } },
                title = { Text(stringResource(R.string.zero_v6_showcase), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = { ZeroAnimatedSwitch(dark, { dark = it }) })
        }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ZeroHairlineCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val samples = listOf("background" to colors.background, "surface" to colors.surface,
                                    "surfaceElevated" to colors.surfaceElevated, "textPrimary" to colors.textPrimary,
                                    "textSecondary" to colors.textSecondary, "textMuted" to colors.textMuted,
                                    "borderSubtle" to colors.borderSubtle, "borderStrong" to colors.borderStrong,
                                    "accentOlive" to colors.accentOlive, "accentOliveSubtle" to colors.accentOliveSubtle,
                                    "destructive" to colors.destructive, "destructiveSubtle" to colors.destructiveSubtle)
                                samples.forEach { (label, color) ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.size(16.dp).background(color, CircleShape))
                                        Text(label, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        ZeroHairlineCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ZeroAnimatedSwitch(toggle, { toggle = it })
                                    ZeroStatusDot(animated = toggle)
                                }
                                ZeroTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("繁體中文 · Type sample") })
                                ZeroTextField(sampleKey, { sampleKey = it }, Modifier.fillMaxWidth(), label = { Text("API Key (sample)") },
                                    singleLine = true, visualTransformation = if (hidden) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                                    trailingIcon = { ZeroIconButton({ hidden = !hidden }) { Text(if (hidden) "●" else "○") } })
                                val percentage = "${(slider * 100).toInt()}%"
                                Text(percentage, style = MaterialTheme.typography.labelMedium)
                                ZeroSlider(slider, { slider = it }, Modifier.fillMaxWidth(), steps = 19)
                                if (quote) ZeroQuotePreviewCard("ZeroAgent · 選取一段內容來追問。", stringResource(R.string.quick_chat_close), { quote = false })
                                Text("val answer = 42", fontFamily = JetbrainsMono)
                                me.rerere.zeroagent.ui.components.richtext.LatexText(
                                    latex = "\\frac{x^2}{2} + C", fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                )
                            }
                        }
                    }
                    item {
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ZeroCapsuleButton({ dialog = true }) { Text("Dialog") }
                            ZeroCapsuleButton({ sheet = true }) { Text("Sheet") }
                            ZeroToastTone.entries.forEach { tone ->
                                ZeroCapsuleButton({ lastToast = tone; toast = tone }) { Text(tone.name) }
                            }
                        }
                    }
                    items(ZeroFeedbackEvent.entries.size) { index ->
                        val event = ZeroFeedbackEvent.entries[index]
                        ZeroCapsuleButton({ feedback(event) }) { Text(event.name) }
                    }
                }
                AnimatedVisibility(
                    visible = toast != null,
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                    enter = fadeIn() + slideInVertically(ZeroMotion.snappy()) { -it / 3 },
                    exit = fadeOut() + slideOutVertically(ZeroMotion.fluid()) { -it / 3 },
                ) {
                    ZeroToast(tone = lastToast) { Text(lastToast.name) }
                }
            }
        }
        if (dialog) ZeroAgentAlertDialog(onDismissRequest = { dialog = false }, title = { Text("ZeroAgent") },
            confirmButton = { ZeroCapsuleButton({ dialog = false }) { Text(stringResource(android.R.string.ok)) } })
        if (sheet) ZeroAgentModalBottomSheet({ sheet = false }) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                ZeroQuotePreviewCard("問問ZeroAgent", stringResource(R.string.quick_chat_close), { sheet = false })
                ZeroTextField(text, { text = it }, Modifier.fillMaxWidth())
            }
        }
    }
}
