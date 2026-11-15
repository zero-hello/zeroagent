package me.rerere.zeroagent.ui.pages.assistant.detail

/** Only changes assistant tool visibility; OS grants and execution approval are independent. */
internal data class LocalToolSelection<T>(val groups: List<T>, val disabledTools: Set<String>)

internal fun <T> selectLocalTools(
    currentGroups: List<T>,
    currentDisabledTools: Set<String>,
    groupTools: Map<T, List<String>>,
    availableGroups: List<T>,
    enabled: Boolean,
): LocalToolSelection<T> {
    val changedGroups = if (enabled) availableGroups.toSet() else groupTools.keys
    val changedTools = changedGroups.flatMap { groupTools[it].orEmpty() }.toSet()
    return LocalToolSelection(
        groups = if (enabled) (currentGroups + availableGroups).distinct() else currentGroups.filterNot { it in changedGroups },
        disabledTools = if (enabled) currentDisabledTools - changedTools else currentDisabledTools + changedTools,
    )
}
