package me.rerere.zeroagent.ui.components.nav

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.ui.LiquidGlassIconButton
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.context.LocalSettings
import me.rerere.zeroagent.ui.theme.CustomColors

@Composable
fun BackButton(modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val navController = LocalNavController.current
    val displaySetting = LocalSettings.current.displaySetting
    if (displaySetting.advancedMaterialEnabled) {
        LiquidGlassIconButton(
            onClick = {
                if (onBack != null) onBack() else navController.popBackStack()
            },
            modifier = modifier.size(48.dp),
        ) {
            Icon(
                imageVector = HugeIcons.ArrowLeft01,
                contentDescription = stringResource(R.string.back)
            )
        }
    } else {
        FilledTonalIconButton(
            onClick = {
                if (onBack != null) onBack() else navController.popBackStack()
            },
            modifier = modifier,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = CustomColors.listItemColors.containerColor),
        ) {
            Icon(
                imageVector = HugeIcons.ArrowLeft01,
                contentDescription = stringResource(R.string.back)
            )
        }
    }
}
