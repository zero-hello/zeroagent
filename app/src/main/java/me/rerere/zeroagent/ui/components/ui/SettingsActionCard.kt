package me.rerere.zeroagent.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class SettingsAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/** Keeps explanatory content and actionable controls in distinct visual and focus regions. */
@Composable
fun SettingsActionCard(
    title: String,
    description: String,
    actions: List<SettingsAction>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    announceChanges: Boolean = false,
    active: Boolean? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        me.rerere.zeroagent.ui.designsystem.ZeroHairlineCard(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                if (announceChanges) liveRegion = LiveRegionMode.Polite
            },
        ) {
            BoxWithConstraints(Modifier.padding(20.dp)) {
                val stacked = stackSettingsDescription(maxWidth.value, LocalDensity.current.fontScale)
                val illustration: @Composable () -> Unit = {
                if (icon != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(icon, contentDescription = null, Modifier.padding(12.dp).size(24.dp))
                    }
                }
                }
                val descriptionContent: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (active != null) {
                            val colors = me.rerere.zeroagent.ui.designsystem.ZeroTheme.colors
                            androidx.compose.foundation.Canvas(Modifier.size(7.dp)) {
                                drawCircle(if (active) colors.accentOlive else colors.destructive)
                            }
                        }
                        Text(title, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() })
                    }
                    Text(description, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                }
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { illustration(); descriptionContent() }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                        illustration()
                        Column(Modifier.weight(1f)) { descriptionContent() }
                    }
                }
            }
        }
        if (actions.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions.forEach { action ->
                    me.rerere.zeroagent.ui.designsystem.ZeroCapsuleButton(
                        onClick = action.onClick,
                        enabled = action.enabled,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(action.label) }
                }
            }
        }
    }
}

internal fun stackSettingsDescription(widthDp: Float, fontScale: Float): Boolean =
    widthDp < 280f || fontScale >= 1.5f
