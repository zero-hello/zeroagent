package me.rerere.zeroagent.ui.pages.chat

import me.rerere.zeroagent.ui.motion.*
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.zeroagent.R

@Composable
internal fun WorkspaceWelcome(
    modifier: Modifier = Modifier,
    onDraft: (String) -> Unit,
    taskOverview: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val compact = maxHeight < 360.dp
        Column(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = if (compact) 12.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            if (!compact) {
                me.rerere.zeroagent.ui.designsystem.ZeroFoldedLogo(Modifier.size(72.dp))
                Text("ZeroAgent", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.zeroagent6_welcome_title),
                style = (if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium)
                    .copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center)
            if (!compact) {
                Text(stringResource(R.string.zeroagent6_welcome_hint),
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = welcomeActionColumns(maxWidth.value, LocalDensity.current.fontScale).coerceAtMost(3)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = columns,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val actions = listOf(
                        Triple(R.string.zeroagent6_write, R.string.zeroagent6_draft_write, HugeIcons.PencilEdit01),
                        Triple(R.string.zeroagent6_code, R.string.zeroagent6_draft_code, HugeIcons.ComputerTerminal01),
                        Triple(R.string.zeroagent6_analyze, R.string.zeroagent6_draft_analyze, HugeIcons.ChartColumn),
                    )
                    actions.forEach { (title, draft, icon) ->
                        val draftText = stringResource(draft)
                        WelcomeActionButton(
                            label = stringResource(title),
                            icon = icon,
                            onClick = { onDraft(draftText) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            taskOverview()
        }
    }
}

@Composable
private fun WelcomeActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    me.rerere.zeroagent.ui.designsystem.ZeroCapsuleButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(icon, contentDescription = null, Modifier.padding(end = 8.dp).size(20.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center)
    }
}
