package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import me.rerere.zeroagent.ui.components.ui.liquidGlassOverlay

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
                Image(painterResource(R.drawable.zeroagent_foreground), null, Modifier.size(72.dp))
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
            val columns = if (maxWidth < 520.dp || LocalDensity.current.fontScale >= 1.3f) 2 else 4
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
                    Triple(R.string.zeroagent6_plan, R.string.zeroagent6_draft_plan, HugeIcons.Clock02),
                )
                actions.forEach { (title, draft, icon) ->
                    val draftText = stringResource(draft)
                    OutlinedButton(
                        onClick = { onDraft(draftText) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).liquidGlassOverlay(shape = MaterialTheme.shapes.small),
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Icon(icon, null, Modifier.padding(end = 8.dp).size(18.dp))
                        Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            }
            Spacer(Modifier.height(20.dp))
            taskOverview()
        }
    }
}
