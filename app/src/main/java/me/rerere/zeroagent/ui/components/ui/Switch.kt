package me.rerere.zeroagent.ui.components.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import me.rerere.zeroagent.ui.motion.statePulse
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

enum class SwitchSize {
    Small,
    Medium,
    Large
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: SwitchSize = SwitchSize.Medium,
    enabled: Boolean = true,
    trackColor: Color = MaterialTheme.colorScheme.primary,
    trackColorUnchecked: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    thumbColor: Color = MaterialTheme.colorScheme.onPrimary,
    thumbColorUnchecked: Color = MaterialTheme.colorScheme.outline,
    interactionSource: MutableInteractionSource? = null,
) {
    // Keep the shared API while using Material's focus, ripple, semantics and motion behavior.
    // Compact settings still receive the same 48 dp minimum touch target.
    val touchTarget = if (size == SwitchSize.Large) 56.dp else 48.dp
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.statePulse(checked && enabled).sizeIn(minWidth = touchTarget, minHeight = touchTarget).then(
            if (onCheckedChange == null) Modifier.semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                if (!enabled) disabled()
            } else Modifier,
        ),
        enabled = enabled,
        interactionSource = interactionSource,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedTrackColor = trackColor,
            uncheckedTrackColor = trackColorUnchecked,
            checkedThumbColor = thumbColor,
            uncheckedThumbColor = thumbColorUnchecked,
        ),
    )
}

@Composable
@Preview(showBackground = true)
private fun SwitchPreview() {
    Column(
        modifier = Modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var checkedSmall by remember { mutableStateOf(true) }
        var checkedMedium by remember { mutableStateOf(true) }
        var checkedLarge by remember { mutableStateOf(true) }
        var unchecked by remember { mutableStateOf(false) }

        Text("Small", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = checkedSmall,
            onCheckedChange = { checkedSmall = it },
            size = SwitchSize.Small
        )

        Text("Medium (Default)", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = checkedMedium,
            onCheckedChange = { checkedMedium = it },
            size = SwitchSize.Medium
        )

        Text("Large", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = checkedLarge,
            onCheckedChange = { checkedLarge = it },
            size = SwitchSize.Large
        )

        Text("Unchecked", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = unchecked,
            onCheckedChange = { unchecked = it },
            size = SwitchSize.Medium
        )

        Text("Disabled", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = true,
            onCheckedChange = {},
            size = SwitchSize.Medium,
            enabled = false
        )
    }
}
