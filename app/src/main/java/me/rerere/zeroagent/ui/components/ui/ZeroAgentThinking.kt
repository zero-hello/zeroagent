package me.rerere.zeroagent.ui.components.ui

import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import me.rerere.zeroagent.R

/** The official Expressive indicator, with a static shape when system motion is disabled. */
@Composable
fun ZeroAgentThinkingIndicator(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.zeroagent6_working)
    val semantics = modifier.semantics { contentDescription = label }
    if (rememberMotionEnabled()) LoadingIndicator(modifier = semantics)
    else LoadingIndicator(progress = { 0f }, modifier = semantics)
}
