package me.rerere.zeroagent.ui.pages.setting

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.*
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.files.FilesManager
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.components.ui.ZeroAgentLanguagePreferences
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.theme.CustomColors
import me.rerere.zeroagent.ui.pages.gettingstarted.hasConfiguredModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold

@Composable
fun SettingPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val filesManager: FilesManager = koinInject()
    val storage by produceState(-1 to 0L) { value = filesManager.countChatFiles() }
    val entries = remember(settings.developerMode, settings.chatModelId) {
        settingsSearchIndex(settings.developerMode, settings.chatModelId.toString())
    }
    val groups = listOf(
        R.string.setting_page_group_appearance,
        R.string.setting_page_group_models,
        R.string.setting_page_group_assistant,
        R.string.zeroagent_settings_workspace,
        R.string.setting_page_group_device,
        R.string.setting_page_group_data,
    )
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { nav.navigate(Screen.SettingsSearch) }) {
                        Icon(HugeIcons.GlobalSearch, stringResource(R.string.accessibility_search))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = insets.calculateTopPadding() + 8.dp,
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item("setup") {
                CardGroup {
                    item(
                        onClick = { nav.navigate(Screen.SettingProvider) },
                        leadingContent = { Icon(HugeIcons.Brain02, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_providers)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_providers_desc)) },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                    )
                }
            }
            groups.forEach { group ->
                item(group) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CardGroup(title = { Text(stringResource(group)) }) {
                            entries.filter { it.groupRes == group && (group != R.string.setting_page_group_models || it.route != Screen.SettingProvider) }.forEach { entry ->
                                item(
                                    onClick = { nav.navigate(entry.route) },
                                    stackTrailing = false,
                                    leadingContent = { Icon(settingsEntryIcon(entry.route), null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    headlineContent = { Text(stringResource(entry.titleRes)) },
                                    supportingContent = entry.descriptionRes?.let { desc -> {
                                        if (entry.route == Screen.SettingFiles) {
                                            Text(if (storage.first < 0) stringResource(R.string.calculating)
                                                else stringResource(desc, storage.first, storage.second / 1024 / 1024.0))
                                        } else Text(stringResource(desc))
                                    } },
                                    trailingContent = { Icon(HugeIcons.ArrowRight01, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline) },
                                )
                            }
                        }
                        if (group == R.string.setting_page_group_appearance) ZeroAgentLanguagePreferences()
                    }
                }
            }
            item("share") {
                val shareText = stringResource(R.string.setting_page_share_text)
                val shareTitle = stringResource(R.string.setting_page_share)
                val noShareApp = stringResource(R.string.setting_page_no_share_app)
                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                    try {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }, shareTitle))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, noShareApp, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(HugeIcons.Share04, null, Modifier.padding(end = 8.dp).size(18.dp))
                    Text(shareTitle)
                }
            }
        }
    }
}

internal fun settingsEntryIcon(route: Screen) = when (route) {
    Screen.SettingPreferencesTheme -> HugeIcons.Sun01
    Screen.SettingPreferencesUI -> HugeIcons.View
    Screen.SettingPreferencesGeneral -> HugeIcons.CursorPointer01
    Screen.SettingPreferencesNotification, Screen.SettingNotifications -> HugeIcons.Notification01
    Screen.SettingModels -> HugeIcons.AiMagic
    Screen.SettingProvider -> HugeIcons.Brain02
    Screen.SettingSearch -> HugeIcons.GlobalSearch
    Screen.SettingVoiceAssistant -> HugeIcons.Mic01
    Screen.SettingSpeech -> HugeIcons.Megaphone01
    Screen.SettingPreferencesNetwork -> HugeIcons.Internet
    Screen.Assistant -> HugeIcons.LookTop
    is Screen.ModelMemory, is Screen.AssistantMemory -> HugeIcons.Brain02
    Screen.Extensions -> HugeIcons.Package
    Screen.SettingSubAgents -> HugeIcons.Robot01
    Screen.SettingMcp -> HugeIcons.McpServer
    Screen.SettingToolApprovals -> HugeIcons.Tick01
    Screen.SettingWorkflows -> HugeIcons.Connect
    Screen.SettingScheduledJobs, Screen.TaskCenter -> HugeIcons.Clock02
    Screen.Workspaces -> HugeIcons.Folder01
    Screen.Translator -> HugeIcons.LanguageCircle
    Screen.ImageGen -> HugeIcons.Image02
    Screen.VideoGen -> HugeIcons.Video01
    Screen.Favorite -> HugeIcons.InLove
    Screen.Stats -> HugeIcons.ChartColumn
    Screen.SettingAccessibility -> HugeIcons.SmartPhone01
    Screen.SettingFloatingBall -> HugeIcons.CursorPointer01
    Screen.SettingPermissions, Screen.SettingRoot -> HugeIcons.Shield01
    Screen.SettingShizuku, Screen.SettingTermux -> HugeIcons.Console
    Screen.SettingSsh -> HugeIcons.ComputerTerminal01
    Screen.SettingWeb -> HugeIcons.ServerStack01
    Screen.SettingBrowser -> HugeIcons.Earth
    Screen.Backup -> HugeIcons.Database02
    Screen.SettingFiles -> HugeIcons.ImageUpload
    Screen.SettingDoctor -> HugeIcons.Wrench01
    Screen.Log -> HugeIcons.Bookshelf01
    Screen.SettingAbout -> HugeIcons.Clapping01
    Screen.Developer -> HugeIcons.Developer
    else -> HugeIcons.Book01
}
