package me.rerere.search

import kotlin.uuid.Uuid

/** Preserve the chosen service when the settings list is reordered, prepended or pruned. */
fun searchSelectionAfterEdit(previous: List<Uuid>, selectedIndex: Int, next: List<Uuid>): Int {
    val retained = previous.getOrNull(selectedIndex)?.let(next::indexOf) ?: -1
    return if (retained >= 0) retained else selectedIndex.coerceIn(0, (next.size - 1).coerceAtLeast(0))
}
