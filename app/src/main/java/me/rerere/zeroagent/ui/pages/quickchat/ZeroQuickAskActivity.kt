package me.rerere.zeroagent.ui.pages.quickchat

import android.content.Intent

/** Accepts only a bounded, plain text selection. Sending still requires an explicit chat action. */
class ZeroQuickAskActivity : QuickChatActivity() {
    override val isQuickAsk = true

    override fun externalQuote(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_PROCESS_TEXT || intent.type != "text/plain") return null
        val text = runCatching { intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT) }.getOrNull()
        return text?.let { it.subSequence(0, minOf(it.length, 16_000)).toString().trim().takeIf(String::isNotEmpty) }
    }
}
