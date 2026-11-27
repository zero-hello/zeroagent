package me.rerere.zeroagent.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ZeroInlineBanner(message: String, modifier: Modifier = Modifier, error: Boolean = false, busy: Boolean = false) {
    val colors = ZeroTheme.colors
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (error) colors.destructiveSubtle else colors.surface,
        contentColor = if (error) colors.destructive else colors.textPrimary,
        border = BorderStroke(1.dp, if (error) colors.destructive.copy(alpha = .5f) else colors.borderSubtle),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) ZeroStatusDot(animated = true)
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable
internal fun ZeroEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    ZeroHairlineCard(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = ZeroTheme.colors.textPrimary, style = MaterialTheme.typography.titleSmall)
            detail?.let { Text(it, color = ZeroTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium) }
            actions()
        }
    }
}
