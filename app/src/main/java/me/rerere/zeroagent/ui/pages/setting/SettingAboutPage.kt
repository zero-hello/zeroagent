package me.rerere.zeroagent.ui.pages.setting

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.SmartPhone01
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
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
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.IOException
import kotlinx.coroutines.launch
import me.rerere.zeroagent.AppScope
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import me.rerere.zeroagent.ui.theme.LocalDarkMode
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
    val store: me.rerere.zeroagent.data.datastore.SettingsStore = org.koin.compose.koinInject()
    val appScope: AppScope = koinInject()
    var taps by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var lastTap by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
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
                    Icon(
                        painter = painterResource(R.drawable.zeroagent_monochrome),
                        contentDescription = stringResource(R.string.accessibility_app_logo),
                        tint = if (LocalDarkMode.current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(104.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )

                    Text(
                        text = "ZeroAgent",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.zero_ui_version_name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        onClick = {
                            if (!store.settingsFlow.value.developerMode) {
                                val now = android.os.SystemClock.elapsedRealtime()
                                taps = if (now - lastTap > 1800L) 1 else taps + 1
                                lastTap = now
                                if (taps == 10) {
                                    taps = 0
                                    appScope.launch {
                                        val message = try {
                                            store.update { it.copy(developerMode = true) }
                                            R.string.developer_enabled_confirmation
                                        } catch (_: IOException) {
                                            R.string.developer_save_failed
                                        }
                                        android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
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
