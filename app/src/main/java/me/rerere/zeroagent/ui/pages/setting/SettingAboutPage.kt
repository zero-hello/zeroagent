package me.rerere.zeroagent.ui.pages.setting

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.SmartPhone01
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.rerere.zeroagent.BuildConfig
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.theme.CustomColors
import me.rerere.zeroagent.utils.openUrl
import me.rerere.zeroagent.utils.plus

@Composable
fun SettingAboutPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val navController = LocalNavController.current
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.about_page_title))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AsyncImage(
                        model = R.mipmap.ic_launcher,
                        contentDescription = stringResource(R.string.accessibility_app_logo),
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                            .size(104.dp)

                    )

                    Text(
                        text = "ZeroAgent",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { if (BuildConfig.DEBUG) navController.navigate(Screen.Debug) },
                        ),
                        leadingContent = { Icon(HugeIcons.Code, null) },
                        supportingContent = {
                            Text("${BuildConfig.VERSION_NAME} / ${BuildConfig.VERSION_CODE}")
                        },
                        headlineContent = { Text(stringResource(R.string.about_page_version)) },
                    )
                    item(
                        leadingContent = { Icon(HugeIcons.SmartPhone01, null) },
                        supportingContent = {
                            Text("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE} / SDK ${android.os.Build.VERSION.SDK_INT}")
                        },
                        headlineContent = { Text(stringResource(R.string.about_page_system)) },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        leadingContent = { Icon(HugeIcons.Code, null) },
                        supportingContent = { Text("Zero") },
                        headlineContent = { Text(stringResource(R.string.zeroagent_maintainer)) },
                    )
                    item(
                        onClick = { context.openUrl("https://www.gnu.org/licenses/agpl-3.0.html") },
                        leadingContent = { Icon(HugeIcons.File02, null) },
                        supportingContent = { Text(stringResource(R.string.zeroagent6_license_notice)) },
                        headlineContent = { Text(stringResource(R.string.about_page_license)) },
                    )
                }
            }
        }
    }
}
