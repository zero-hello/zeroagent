package me.rerere.zeroagent.ui.designsystem

import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.rerere.zeroagent.R

internal data class ZeroAskDraft(val quote: String?, val question: String)

/** One-use handoff. The intent only carries a token, never the selected text. */
internal object ZeroAskHandoff {
    const val EXTRA = "zero_ask_handoff"
    private var pending: Triple<String, String, ZeroAskDraft>? = null

    fun offer(chatId: String, draft: ZeroAskDraft): String {
        val token = java.util.UUID.randomUUID().toString()
        pending = Triple(token, chatId, draft)
        return token
    }

    fun take(token: String?, chatId: String): ZeroAskDraft? {
        val current = pending ?: return null
        if (current.first != token || current.second != chatId) return null
        pending = null
        return current.third
    }
}

internal val LocalSelectedTextAsk = staticCompositionLocalOf<((String) -> Unit)?> { null }

internal fun quotedChatText(text: String): String = text.lineSequence().joinToString("\n") { "> $it" }

/** Uses Compose's selected text directly, including text rendered across Markdown spans. */
@Composable
internal fun ZeroMessageSelection(content: @Composable () -> Unit) {
    val ask by rememberUpdatedState(LocalSelectedTextAsk.current)
    val label = stringResource(R.string.zero_v6_quick_ask)
    val selection = rememberSelectionState()
    SelectionContainer(
        state = selection,
        modifier = Modifier.appendTextContextMenuComponents {
            if (ask != null && selection.selectedTexts.any { it.isNotBlank() }) {
                item(ZeroAskMenuKey, label) {
                    val excerpt = selection.selectedTexts.joinToString("\n").take(16_000).trim()
                    if (excerpt.isNotEmpty()) {
                        close()
                        selection.clear()
                        ask?.invoke(excerpt)
                    }
                }
            }
        },
        content = content,
    )
}

private object ZeroAskMenuKey
