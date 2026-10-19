package me.rerere.zeroagent.di

import me.rerere.zeroagent.assistant.VoiceAssistantSettingsVM
import me.rerere.zeroagent.ui.pages.assistant.detail.AssistantDetailVM
import me.rerere.zeroagent.ui.pages.backup.BackupVM
import me.rerere.zeroagent.ui.pages.chat.ChatDrawerVM
import me.rerere.zeroagent.ui.pages.chat.ChatVM
import me.rerere.zeroagent.ui.pages.debug.DebugVM
import me.rerere.zeroagent.ui.pages.developer.DeveloperVM
import me.rerere.zeroagent.ui.pages.favorite.FavoriteVM
import me.rerere.zeroagent.ui.pages.search.SearchVM
import me.rerere.zeroagent.ui.pages.history.HistoryVM
import me.rerere.zeroagent.ui.pages.stats.StatsVM
import me.rerere.zeroagent.ui.pages.imggen.ImgGenVM
import me.rerere.zeroagent.ui.pages.videogen.VideoGenVM
import me.rerere.zeroagent.ui.pages.extensions.PromptVM
import me.rerere.zeroagent.ui.pages.extensions.QuickMessagesVM
import me.rerere.zeroagent.ui.pages.extensions.skills.SkillDetailVM
import me.rerere.zeroagent.ui.pages.extensions.skills.SkillsVM
import me.rerere.zeroagent.ui.pages.extensions.workspace.WorkspaceDetailVM
import me.rerere.zeroagent.ui.pages.extensions.workspace.WorkspaceVM
import me.rerere.zeroagent.ui.pages.setting.SettingVM
// P2-32 ssh hosts page
import me.rerere.zeroagent.ui.pages.setting.SettingSshViewModel
import me.rerere.zeroagent.ui.pages.setting.browser.SettingBrowserViewModel
import me.rerere.zeroagent.ui.pages.setting.termux.SettingTermuxViewModel
import me.rerere.zeroagent.ui.pages.setting.locallm.SettingLocalLlmViewModel
import me.rerere.zeroagent.ui.pages.share.handler.ShareHandlerVM
import me.rerere.zeroagent.ui.pages.translator.TranslatorVM
import me.rerere.zeroagent.ui.pages.setting.doctor.DoctorViewModel
import me.rerere.zeroagent.ui.pages.setting.scheduledjobs.ScheduledJobsViewModel
import me.rerere.zeroagent.workflow.ui.WorkflowsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModel<ChatVM> { params ->
        ChatVM(
            id = params.get(),
            context = get(),
            settingsStore = get(),
            conversationRepo = get(),
            chatService = get(),
            updateChecker = get(),
            filesManager = get(),
            favoriteRepository = get(),
            usageLedger = get(),
        )
    }
    viewModelOf(::ChatDrawerVM)
    viewModelOf(::VoiceAssistantSettingsVM)
    viewModelOf(::SettingVM)
    viewModelOf(::DebugVM)
    viewModelOf(::DeveloperVM)
    viewModelOf(::HistoryVM)
    viewModel<AssistantDetailVM> {
        AssistantDetailVM(
            id = it.get(),
            settingsStore = get(),
            memoryRepository = get(),
            filesManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
            // P2-24 — read-only: the sub-agent archive folder picker.
            folderRepository = get(),
        )
    }
    viewModelOf(::TranslatorVM)
    viewModel<ShareHandlerVM> {
        ShareHandlerVM(
            text = it.get(),
            settingsStore = get(),
        )
    }
    viewModelOf(::BackupVM)
    viewModelOf(::ImgGenVM)
    viewModelOf(::VideoGenVM)
    viewModelOf(::PromptVM)
    viewModelOf(::QuickMessagesVM)
    viewModel<SkillsVM> {
        SkillsVM(
            context = get(),
            skillManager = get(),
            urlImporter = get(),
        )
    }
    viewModelOf(::SkillDetailVM)
    viewModelOf(::WorkspaceVM)
    viewModel<WorkspaceDetailVM> {
        WorkspaceDetailVM(
            id = it.get(),
            repository = get(),
            terminalSessionManager = get(),
        )
    }
    viewModelOf(::FavoriteVM)
    viewModelOf(::SearchVM)
    viewModelOf(::StatsVM)
    viewModelOf(::WorkflowsViewModel)
    viewModelOf(::ScheduledJobsViewModel)
    viewModelOf(::DoctorViewModel)
    viewModelOf(::SettingBrowserViewModel)
    viewModelOf(::SettingTermuxViewModel)
    // P2-32 ssh hosts page
    viewModelOf(::SettingSshViewModel)

    // Phase 22A: parameterised by LocalRuntime — one VM instance per provider tile.
    viewModel<SettingLocalLlmViewModel> { params ->
        SettingLocalLlmViewModel(
            runtime = params.get(),
            context = get(),
            prefs = get(),
            httpClient = get(),
            settingsStore = get(),
            hfApi = get(),
        )
    }
}
