package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.ai.matchesPickerType
import me.rerere.zeroagent.ui.designsystem.*

@Composable
internal fun ZeroChatModelSelector(model: Model?, providers: List<ProviderSetting>, warming: Boolean = false, onSelect: (Model) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var showPopup by remember { mutableStateOf(false) }
    val feedback = rememberZeroFeedback()
    val groups = remember(providers) {
        providers.asSequence().filter { it.enabled }.flatMap { provider ->
            provider.models.asSequence().filter { it.matchesPickerType(ModelType.CHAT) }.map { provider to it }
        }.groupBy { (provider, _) -> provider is ProviderSetting.LlamaCppLocal || provider is ProviderSetting.LiteRtLocal }
    }
    Box {
        ZeroCapsuleButton(onClick = {
            open = true
            showPopup = true
        }, modifier = Modifier.widthIn(max = 330.dp), selected = model != null, pulse = warming) {
            Text(model?.let { chatModelLabel(it, providers) } ?: stringResource(R.string.model_list_select_model),
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        }
        if (showPopup) {
            val visible = remember { MutableTransitionState(false) }
            visible.targetState = open
            LaunchedEffect(visible.isIdle, visible.currentState, open) {
                if (visible.isIdle && !visible.currentState && !open) showPopup = false
            }
            Popup(popupPositionProvider = remember { BelowModelButton }, onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)) {
                AnimatedVisibility(visibleState = visible,
                    enter = fadeIn() + expandVertically(ZeroMotion.bouncy(), expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(ZeroMotion.fluid(), shrinkTowards = Alignment.Top)) {
                    val colors = ZeroTheme.colors
                    Surface(Modifier.widthIn(min = 200.dp, max = 320.dp), shape = MaterialTheme.shapes.large,
                        color = colors.surface, border = BorderStroke(1.dp, colors.borderSubtle), shadowElevation = 4.dp) {
                        LazyColumn(Modifier.heightIn(max = 360.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                            if (groups.isEmpty()) item {
                                Text(stringResource(R.string.model_list_select_model), Modifier.padding(16.dp),
                                    color = colors.textSecondary)
                            }
                            listOf(false, true).forEach { local ->
                                val models = groups[local].orEmpty()
                                if (models.isNotEmpty()) {
                                    item {
                                        Text(stringResource(if (local) R.string.zero_v6_model_local else R.string.zero_v6_model_cloud),
                                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                                    }
                                    items(models, key = { (provider, candidate) -> "${provider.id}/${candidate.id}" }) { (provider, candidate) ->
                                        DropdownMenuItem(text = {
                                            Column {
                                                Text(candidate.displayName.ifBlank { candidate.modelId }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(provider.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                            }
                                        }, trailingIcon = if (model?.id == candidate.id) ({ ZeroStatusDot() }) else null,
                                            onClick = { onSelect(candidate); feedback(ZeroFeedbackEvent.Select); open = false })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private object BelowModelButton : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val left = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val top = anchorBounds.bottom.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(left, top)
    }
}
internal fun chatModelLabel(model: Model, providers: List<ProviderSetting>): String {
    val provider = providers.firstOrNull { provider -> provider.models.any { it.id == model.id } }
    val name = model.displayName.ifBlank { model.modelId }
    return provider?.name?.takeIf { it.isNotBlank() }?.let { "$it: $name" } ?: name
}