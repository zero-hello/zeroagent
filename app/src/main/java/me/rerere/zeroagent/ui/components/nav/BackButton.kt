package me.rerere.zeroagent.ui.components.nav

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.theme.CustomColors

@Composable
fun BackButton(modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val navController = LocalNavController.current
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
