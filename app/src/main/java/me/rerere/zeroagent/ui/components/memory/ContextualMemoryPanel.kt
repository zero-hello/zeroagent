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
import me.rerere.zeroagent.data.model.AssistantMemory
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import me.rerere.zeroagent.ui.components.ui.Switch

@Composable
fun ContextualMemoryPanel(
    memories: List<AssistantMemory>,
    onSelectMemory: (AssistantMemory) -> Unit,
) {
    val context = LocalContext.current
    val manager = remember { MemoryManager.get(context) }
    val state by manager.state.collectAsStateWithLifecycle()
    val jobs by remember(manager) { manager.observeInitialization() }.collectAsStateWithLifecycle(emptyList())
    val job = jobs.maxByOrNull { it.generation }
    val running = jobs.any { !it.state.isFinished }

    val scope = rememberCoroutineScope()
    var consent by remember { mutableStateOf(false) }
    var erase by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(job?.state) { manager.refresh() }
    fun act(block: suspend () -> Unit) { scope.launch { try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }; failed = false } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { failed = true } } }
    CardGroup(title = { Text(stringResource(R.string.memory12_title)) }) {
        item(headlineContent = { Text(stringResource(R.string.memory_mood_enable)) },
            supportingContent = { Text(stringResource(R.string.memory_mood_privacy)) },
            trailingContent = { Switch(checked = state.enabled, onCheckedChange = { if (it) consent = true else act { manager.setEnabled(false) } }) })
        if (state.enabled) item(headlineContent = { Text(stringResource(R.string.memory12_tone)) },
            supportingContent = { Text(stringResource(R.string.memory12_tone_hint)) },
            trailingContent = { Switch(state.tone, { act { manager.setTone(it) } }) })
    }
    if (running || state.working) {
        Text(stringResource(R.string.memory12_building), style = MaterialTheme.typography.bodyMedium)
        MemoryTransferProgress(job, state.working && !running)
    } else if (state.enabled && !state.ready) {
        TextButton(shapes = ButtonDefaults.shapes(), onClick = { act { manager.initialize() } }) { Text(stringResource(R.string.memory12_initialize)) }
    }
    if (state.error || failed || job?.state == WorkInfo.State.FAILED) {
        Text(stringResource(R.string.memory12_error), color = MaterialTheme.colorScheme.error)
        TextButton(shapes = ButtonDefaults.shapes(), onClick = { act { manager.initialize() } }) { Text(stringResource(R.string.memory12_retry)) }
    }
    if (memories.isNotEmpty()) {
        Text(stringResource(R.string.memory12_graph), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.memory12_graph_hint), style = MaterialTheme.typography.bodySmall)
        MemoryGraph(memories.take(24), onSelect = onSelectMemory)
        Text(stringResource(R.string.memory12_recent_count, minOf(24, memories.size)), style = MaterialTheme.typography.bodySmall)
    } else Text(stringResource(R.string.memory12_empty), style = MaterialTheme.typography.bodySmall)
    if (state.records.isNotEmpty() || state.error || failed) TextButton(shapes = ButtonDefaults.shapes(), onClick = { erase = true }) { Text(stringResource(R.string.memory12_erase)) }
    if (consent) ZeroAgentAlertDialog(onDismissRequest = { consent = false }, title = { Text(stringResource(R.string.memory12_title)) },
        text = { Text(stringResource(R.string.memory_mood_consent)) },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { consent = false; act { manager.setEnabled(true); manager.initialize() } }) { Text(stringResource(R.string.memory12_accept)) } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { consent = false }) { Text(stringResource(R.string.memory12_cancel)) } })
    if (erase) ZeroAgentAlertDialog(onDismissRequest = { erase = false }, title = { Text(stringResource(R.string.memory12_erase)) },
        text = { Text(stringResource(R.string.memory12_erase_hint)) },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { erase = false; act { manager.erase() } }) { Text(stringResource(R.string.memory12_erase)) } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { erase = false }) { Text(stringResource(R.string.memory12_cancel)) } })
}

@Composable
private fun MemoryGraph(records: List<AssistantMemory>, onSelect: (AssistantMemory) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outlineVariant
    // A sunflower layout distributes nodes evenly without a dense ring or a fictitious hub.
    fun positions(width: Float, height: Float): List<Offset> {
        val center = Offset(width / 2, height / 2)
        val radius = minOf(width, height) * .38f
        return records.map { memory ->
            val terms = memoryGraphTerms(memory.content)
            val topic = terms.sorted().firstOrNull()?.hashCode() ?: memory.content.hashCode()
            val angle = (topic.toLong() and 0xffffffffL).toDouble() / 0xffffffffL * (2 * PI)
            val spread = ((memory.content.hashCode().ushr(1) % 1000) / 1000f - .5f) * .14f
            val distance = radius * (.68f + spread)
            Offset(center.x + cos(angle).toFloat() * distance, center.y + sin(angle).toFloat() * distance)
        }
    }
    val edges = remember(records) { assistantMemoryGraphEdges(records) }
    val graphLabel = stringResource(R.string.memory12_graph)
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Canvas(Modifier.fillMaxWidth().height(260.dp).semantics {
            contentDescription = graphLabel
            customActions = records.map { entry -> CustomAccessibilityAction(entry.content.take(48)) { onSelect(entry); true } }
        }.pointerInput(records) {
            detectTapGestures { tap ->
                val points = positions(size.width.toFloat(), size.height.toFloat())
                points.indices.minByOrNull { (points[it] - tap).getDistance() }
                    ?.takeIf { (points[it] - tap).getDistance() < 24.dp.toPx() }
                    ?.let { onSelect(records[it]) }
            }
        }) {
            val locations = positions(size.width, size.height)
            edges.forEach { (i, j) ->
                drawLine(outline.copy(alpha = .65f), locations[i], locations[j], 1.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            locations.forEachIndexed { i, point ->
                val color = if (i % 2 == 0) primary else secondary
                drawCircle(color.copy(alpha = .09f), 19.dp.toPx(), point)
                drawCircle(color.copy(alpha = .18f), 13.dp.toPx(), point)
                drawCircle(color, 7.dp.toPx(), point)
            }
        }
    }

}

internal fun memoryGraphTerms(content: String): Set<String> {
    val lower = content.lowercase()
    val terms = Regex("[a-z0-9]{2,}").findAll(lower).map { it.value }.toMutableSet()
    val cjk = lower.codePoints().toArray().filter { it in 0x3400..0x9fff }.toList()
    cjk.windowed(2).forEach { pair ->
        terms += String(pair.toIntArray(), 0, 2)
    }
    return terms
}

internal fun assistantMemoryGraphEdges(memories: List<AssistantMemory>): List<Pair<Int, Int>> =
    memories.indices.flatMap { index ->
        val left = memoryGraphTerms(memories[index].content)
        memories.indices.asSequence().filter { it != index }
            .map { other ->
                val right = memoryGraphTerms(memories[other].content)
                val union = left union right
                other to if (union.isEmpty()) 0.0 else (left intersect right).size.toDouble() / union.size
            }
            .filter { it.second >= .16 }
            .sortedByDescending { it.second }
            .take(2)
            .map { (other, _) -> minOf(index, other) to maxOf(index, other) }
            .toList()
    }.distinct()

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
    val activeJob = jobs.firstOrNull { !it.state.isFinished }
    val state by manager.state.collectAsStateWithLifecycle()
    fun dismiss() { preferences.edit().putBoolean("offered", true).apply(); offered = true; initializing = false }
    if (!offered || initializing) ZeroAgentAlertDialog(onDismissRequest = ::dismiss,
        title = { Text(stringResource(if (initializing) R.string.memory12_building else R.string.memory12_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (failed) R.string.memory12_error else R.string.memory_mood_consent))
            if (initializing) {
                MemoryTransferProgress(activeJob, state.working && activeJob == null)
            }
        } },
        confirmButton = { if (initializing) TextButton(shapes = ButtonDefaults.shapes(), onClick = ::dismiss) { Text(stringResource(R.string.memory12_close)) }
            else TextButton(shapes = ButtonDefaults.shapes(), onClick = { scope.launch { try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { manager.setEnabled(true); manager.initialize() }; initializing = true
                preferences.edit().putBoolean("offered", true).apply(); offered = true
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { failed = true } } }) { Text(stringResource(R.string.memory12_accept)) } },
        dismissButton = { if (!initializing) TextButton(shapes = ButtonDefaults.shapes(), onClick = ::dismiss) { Text(stringResource(R.string.memory12_cancel)) } })
    LaunchedEffect(jobs.map { it.state }) { if (initializing && jobs.any { it.state.isFinished }) initializing = false }
}


@Composable
private fun MemoryTransferProgress(job: WorkInfo?, preparing: Boolean) {
    if (preparing) {
        me.rerere.zeroagent.ui.components.ui.TransferProgress(null)
    } else {
        me.rerere.zeroagent.ui.components.ui.DownloadProgressIndicator(
            me.rerere.zeroagent.ui.components.ui.DownloadProgress(
                bytes = job?.progress?.getLong("bytes", 0L) ?: 0L,
                total = MemoryManager.MODEL_SIZE,
            ),
        )
    }
}