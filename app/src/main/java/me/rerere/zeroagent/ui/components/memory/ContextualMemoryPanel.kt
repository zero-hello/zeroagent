package me.rerere.zeroagent.ui.components.memory

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import kotlinx.coroutines.launch
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.memory.MemoryManager
import me.rerere.zeroagent.data.memory.RecalledMemory
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import kotlin.math.cos
import kotlin.math.sin
import me.rerere.zeroagent.ui.components.ui.Switch

@Composable
fun ContextualMemoryPanel() {
    val context = LocalContext.current
    val manager = remember { MemoryManager.get(context) }
    val state by manager.state.collectAsStateWithLifecycle()
    val jobs by remember(manager) { manager.observeInitialization() }.collectAsStateWithLifecycle(emptyList())
    val job = jobs.maxByOrNull { it.generation }
    val running = jobs.any { !it.state.isFinished }
    val percent = job?.progress?.getInt("percent", 0) ?: 0
    val scope = rememberCoroutineScope()
    var consent by remember { mutableStateOf(false) }
    var erase by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<RecalledMemory?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(job?.state) { manager.refresh() }
    fun act(block: suspend () -> Unit) { scope.launch { try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }; failed = false } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { failed = true } } }
    CardGroup(title = { Text(stringResource(R.string.memory12_title)) }) {
        item(headlineContent = { Text(stringResource(R.string.memory12_enable)) },
            supportingContent = { Text(stringResource(R.string.memory12_privacy)) },
            trailingContent = { Switch(checked = state.enabled, onCheckedChange = { if (it) consent = true else act { manager.setEnabled(false) } }) })
        if (state.enabled) item(headlineContent = { Text(stringResource(R.string.memory12_tone)) },
            supportingContent = { Text(stringResource(R.string.memory12_tone_hint)) },
            trailingContent = { Switch(state.tone, { act { manager.setTone(it) } }) })
    }
    if (running || state.working) {
        Text(stringResource(R.string.memory12_building), style = MaterialTheme.typography.bodyMedium)
        if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            Text("$percent%", style = MaterialTheme.typography.labelSmall)
        }
    } else if (state.enabled && !state.ready) {
        TextButton(onClick = { act { manager.initialize() } }) { Text(stringResource(R.string.memory12_initialize)) }
    }
    if (state.error || failed || job?.state == WorkInfo.State.FAILED) {
        Text(stringResource(R.string.memory12_error), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { act { manager.initialize() } }) { Text(stringResource(R.string.memory12_retry)) }
    }
    if (state.records.isNotEmpty()) {
        Text(stringResource(R.string.memory12_graph), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.memory12_graph_hint), style = MaterialTheme.typography.bodySmall)
        MemoryGraph(state.records.take(24), onSelect = { selected = it })
        Text(stringResource(R.string.memory12_recent_count, minOf(24, state.records.size)), style = MaterialTheme.typography.bodySmall)
    } else Text(stringResource(R.string.memory12_empty), style = MaterialTheme.typography.bodySmall)
    if (state.records.isNotEmpty() || state.error || failed) TextButton(onClick = { erase = true }) { Text(stringResource(R.string.memory12_erase)) }
    if (consent) ZeroAgentAlertDialog(onDismissRequest = { consent = false }, title = { Text(stringResource(R.string.memory12_title)) },
        text = { Text(stringResource(R.string.memory12_consent)) },
        confirmButton = { TextButton(onClick = { consent = false; act { manager.setEnabled(true); manager.initialize() } }) { Text(stringResource(R.string.memory12_accept)) } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text(stringResource(R.string.memory12_cancel)) } })
    if (erase) ZeroAgentAlertDialog(onDismissRequest = { erase = false }, title = { Text(stringResource(R.string.memory12_erase)) },
        text = { Text(stringResource(R.string.memory12_erase_hint)) },
        confirmButton = { TextButton(onClick = { erase = false; act { manager.erase() } }) { Text(stringResource(R.string.memory12_erase)) } },
        dismissButton = { TextButton(onClick = { erase = false }) { Text(stringResource(R.string.memory12_cancel)) } })
    selected?.let { entry -> ZeroAgentAlertDialog(onDismissRequest = { selected = null }, title = { Text(stringResource(R.string.memory12_record)) },
        text = { Text(entry.text, Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.memory12_close)) } },
        dismissButton = { TextButton(onClick = { selected = null; act { manager.remove(entry.id) } }) { Text(stringResource(R.string.memory12_delete)) } }) }
}

@Composable
private fun MemoryGraph(records: List<RecalledMemory>, onSelect: (RecalledMemory) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outlineVariant
    fun positions(width: Float, height: Float): List<Offset> {
        val center = Offset(width / 2, height / 2)
        val radius = minOf(width, height) * .38f
        return records.indices.map { i -> val a = i * 2 * Math.PI / records.size
            Offset(center.x + cos(a).toFloat() * radius, center.y + sin(a).toFloat() * radius) }
    }
    val edges = remember(records) { records.indices.flatMap { i -> records.indices.filter { j ->
        j > i && me.rerere.zeroagent.data.memory.memorySimilarity(records[i], records[j]) > .8
    }.map { i to it } } }
    val graphLabel = stringResource(R.string.memory12_graph)
    Canvas(Modifier.fillMaxWidth().height(220.dp).semantics {
        contentDescription = graphLabel
        customActions = records.map { entry -> CustomAccessibilityAction(entry.text.take(48)) { onSelect(entry); true } }
    }.pointerInput(records) {
        detectTapGestures { tap -> val points = positions(size.width.toFloat(), size.height.toFloat()); points.indices.minByOrNull { (points[it] - tap).getDistance() }?.takeIf { (points[it] - tap).getDistance() < 28.dp.toPx() }?.let { onSelect(records[it]) } }
    }) {
        val center = Offset(size.width / 2, size.height / 2)
        val locations = positions(size.width, size.height)
        edges.forEach { (i, j) -> drawLine(outline.copy(alpha = .55f), locations[i], locations[j], 1.dp.toPx()) }
        locations.forEachIndexed { i, p -> drawCircle(if (records[i].role == "USER") primary else secondary, 7.dp.toPx(), p) }
        drawCircle(primary.copy(alpha = .12f), 22.dp.toPx(), center)
        drawCircle(primary, 5.dp.toPx(), center)
    }
}

/** Offered once after normal setup; downloading is an explicit choice, not a splash prerequisite. */
@Composable
fun ContextualMemoryWelcome() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("contextual-memory-v1", android.content.Context.MODE_PRIVATE) }
    var offered by remember { mutableStateOf(preferences.getBoolean("offered", false)) }
    val scope = rememberCoroutineScope()
    var initializing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val manager = remember { MemoryManager.get(context) }
    val jobs by remember { manager.observeInitialization() }.collectAsStateWithLifecycle(emptyList())
    val progress = jobs.firstOrNull { !it.state.isFinished }?.progress?.getInt("percent", 0) ?: 0
    val state by manager.state.collectAsStateWithLifecycle()
    fun dismiss() { preferences.edit().putBoolean("offered", true).apply(); offered = true; initializing = false }
    if (!offered || initializing) ZeroAgentAlertDialog(onDismissRequest = ::dismiss,
        title = { Text(stringResource(if (initializing) R.string.memory12_building else R.string.memory12_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (failed) R.string.memory12_error else R.string.memory12_consent))
            if (initializing) {
                if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        } },
        confirmButton = { if (initializing) TextButton(onClick = ::dismiss) { Text(stringResource(R.string.memory12_close)) }
            else TextButton(onClick = { scope.launch { try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { manager.setEnabled(true); manager.initialize() }; initializing = true
                preferences.edit().putBoolean("offered", true).apply(); offered = true
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { failed = true } } }) { Text(stringResource(R.string.memory12_accept)) } },
        dismissButton = { if (!initializing) TextButton(onClick = ::dismiss) { Text(stringResource(R.string.memory12_cancel)) } })
    LaunchedEffect(jobs.map { it.state }) { if (initializing && jobs.any { it.state.isFinished }) initializing = false }
}
