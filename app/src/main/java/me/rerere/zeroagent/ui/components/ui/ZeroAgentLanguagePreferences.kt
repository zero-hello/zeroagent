package me.rerere.zeroagent.ui.components.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.rerere.zeroagent.R
import me.rerere.zeroagent.utils.ZeroAgentLanguagePolicy
import me.rerere.zeroagent.utils.ZeroAgentLocale

@Composable
fun ZeroAgentLanguagePreferences() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var selected by remember(context, configuration) { mutableStateOf(ZeroAgentLocale.selectedTag(context)) }
    var failed by remember { mutableStateOf(false) }
    // System App languages can be changed while this page is in the background.
    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) selected = ZeroAgentLocale.selectedTag(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Language autonyms are intentional: a user must be able to recover from a wrong language.
    val names = mapOf("en" to "English", "zh-TW" to "繁體中文", "zh-CN" to "简体中文", "ja" to "日本語", "ko" to "한국어", "ru" to "Русский", "ar" to "العربية")
    val systemName = stringResource(R.string.zeroagent_language_system)
    CardGroup() {
        item(
            headlineContent = { Text(stringResource(R.string.zeroagent_language)) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.zeroagent_language_hint))
                    // Reuse the bounded plain DropdownMenu implementation. An exposed menu in a
                    // ListItem trailing slot competes with the headline and small-screen bounds.
                    SelectTextField(
                        value = names[selected] ?: systemName,
                        options = ZeroAgentLanguagePolicy.supportedTags,
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        onOptionSelected = { tag ->
                            failed = !ZeroAgentLocale.select(context, tag)
                            if (!failed) selected = tag
                        },
                        optionToString = { names[it] ?: systemName },
                    )
                    if (failed) Text(stringResource(R.string.zeroagent6_language_failed), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}
