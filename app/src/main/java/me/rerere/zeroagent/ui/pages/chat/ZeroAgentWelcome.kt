package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import me.rerere.zeroagent.R

@Composable
fun ZeroAgentWelcome(modifier: Modifier = Modifier, onDraft: (String) -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.zeroagent6_welcome_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.zeroagent6_welcome_hint),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                R.string.zeroagent6_write to R.string.zeroagent6_draft_write,
                R.string.zeroagent6_code to R.string.zeroagent6_draft_code,
                R.string.zeroagent6_analyze to R.string.zeroagent6_draft_analyze,
                R.string.zeroagent6_plan to R.string.zeroagent6_draft_plan,
            ).forEach { (title, draft) ->
                val draftText = stringResource(draft)
                FilledTonalButton(onClick = { onDraft(draftText) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 10.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ZeroAgentTaskOverview()
    }
}
