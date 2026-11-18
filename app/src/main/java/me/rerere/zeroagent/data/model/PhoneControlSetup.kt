package me.rerere.zeroagent.data.model

import me.rerere.zeroagent.data.ai.tools.LocalToolOption

fun Assistant.hasPhoneControlTools(): Boolean =
    LocalToolOption.ScreenAutomation in localTools && LocalToolOption.AppLauncher in localTools

fun Assistant.withPhoneControlTools(): Assistant = copy(
    localTools = (localTools + LocalToolOption.ScreenAutomation + LocalToolOption.AppLauncher).distinct(),
    enabledSkills = enabledSkills + "agent-core",
)
