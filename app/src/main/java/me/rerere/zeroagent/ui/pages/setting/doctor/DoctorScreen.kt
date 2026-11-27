package me.rerere.zeroagent.ui.pages.setting.doctor

import androidx.compose.material3.ButtonDefaults

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import me.rerere.zeroagent.ui.designsystem.ZeroCapsuleButton as Button
import me.rerere.zeroagent.ui.designsystem.ZeroProgressIndicator as CircularProgressIndicator
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.theme.CustomColors
import me.rerere.zeroagent.ui.theme.extendColors
import me.rerere.zeroagent.ui.components.ui.ZeroAgentCard as Card
import me.rerere.zeroagent.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun DoctorScreen(vm: DoctorViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val ctx = LocalContext.current
    val localeTag = LocalConfiguration.current.locales.toLanguageTags()
    LaunchedEffect(localeTag) { vm.onLocaleChanged(localeTag) }
    val localizedResources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }

    val severityCounts = remember(state.results) {
        mapOf(
            Severity.FAIL to state.results.count { it.severity == Severity.FAIL },
            Severity.WARN to state.results.count { it.severity == Severity.WARN },
            Severity.OK to state.results.count { it.severity == Severity.OK },
            Severity.INFO to state.results.count { it.severity == Severity.INFO },
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_doctor)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Summary card — counts + actions. Always-rendered so the layout doesn't jump
            // when running starts/stops; the "Run again" button shows a spinner inline.
            item(key = "summary") {
                SummaryCard(
                    counts = severityCounts,
                    running = state.running,
                    onRunAgain = { vm.runAll() },
                    onCopyReport = {
                        copyToClipboard(ctx, vm.buildReport())
                        scope.launch {
                            snackbar.showSnackbar(localizedResources.getString(R.string.setting_page_doctor_report_copied))
                        }
                    },
                )
            }

            // One CardGroup per category — matches the rest of the Settings UI exactly.
            DoctorCategory.entries.forEach { category ->
                val rows = state.results.filter { it.category == category }
                if (rows.isEmpty()) return@forEach
                item("group-${category.name}") {
                    CardGroup(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        title = { Text(stringResource(category.displayNameRes)) },
                    ) {
                        for (check in rows) {
                            item(
                                leadingContent = { SeverityDot(check.severity) },
                                headlineContent = { Text(check.label) },
                                supportingContent = {
                                    Text(
                                        check.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                trailingContent = {
                                    val fix = check.fix
                                    if (fix != null) FixButton(
                                        fix = fix,
                                        onAutoFix = { f ->
                                            scope.launch {
                                                val res = vm.applyAutoFix(f)
                                                snackbar.showSnackbar(res.message)
                                            }
                                        },
                                        onOpenIntent = { intent ->
                                            runCatching {
                                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                ctx.startActivity(intent)
                                            }.onFailure {
                                                scope.launch {
                                                    snackbar.showSnackbar(
                                                        localizedResources.getString(R.string.doctor_could_not_open, "${it.message ?: it::class.simpleName}")
                                                    )
                                                }
                                            }
                                        },
                                        onOpenAppRoute = { key -> nav.navigate(routeFor(key)) },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    counts: Map<Severity, Int>,
    running: Boolean,
    onRunAgain: () -> Unit,
    onCopyReport: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 320.dp || LocalDensity.current.fontScale > 1.2f) 2 else 4
            FlowRow(maxItemsInEachRow = columns, horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CountPill(stringResource(R.string.doctor_pill_failures), counts[Severity.FAIL] ?: 0, Severity.FAIL, Modifier.weight(1f))
            CountPill(stringResource(R.string.doctor_pill_warnings), counts[Severity.WARN] ?: 0, Severity.WARN, Modifier.weight(1f))
            CountPill(stringResource(R.string.doctor_pill_ok), counts[Severity.OK] ?: 0, Severity.OK, Modifier.weight(1f))
            CountPill(stringResource(R.string.doctor_pill_info), counts[Severity.INFO] ?: 0, Severity.INFO, Modifier.weight(1f))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRunAgain,
                enabled = !running,
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 0.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        stringResource(R.string.setting_page_doctor_running),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    Text(stringResource(R.string.setting_page_doctor_run_again))
                }
            }
            OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onCopyReport, enabled = !running) {
                Text(stringResource(R.string.setting_page_doctor_copy_report))
            }
        }
    }
    }
}

@Composable
private fun CountPill(label: String, count: Int, severity: Severity, modifier: Modifier = Modifier) {
    val color = severityColor(severity)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            count.toString(),
            color = color,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun SeverityDot(severity: Severity) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(severityColor(severity), CircleShape),
    )
}

@Composable
private fun FixButton(
    fix: FixAction,
    onAutoFix: (FixAction.AutoFix) -> Unit,
    onOpenIntent: (android.content.Intent) -> Unit,
    onOpenAppRoute: (AppRouteKey) -> Unit,
) {
    val (label, click) = when (fix) {
        is FixAction.AutoFix -> fix.label to { onAutoFix(fix) }
        is FixAction.OpenIntent -> fix.label to { onOpenIntent(fix.intent) }
        is FixAction.OpenAppRoute -> fix.label to { onOpenAppRoute(fix.routeKey) }
    }
    OutlinedButton(
        shapes = ButtonDefaults.shapes(), onClick = click,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun severityColor(severity: Severity): Color = when (severity) {
    Severity.OK -> MaterialTheme.extendColors.green7
    Severity.INFO -> MaterialTheme.colorScheme.tertiary
    Severity.WARN -> MaterialTheme.extendColors.orange7
    Severity.FAIL -> MaterialTheme.colorScheme.error
}

private fun routeFor(key: AppRouteKey): Screen = when (key) {
    AppRouteKey.SettingScheduledJobs -> Screen.SettingScheduledJobs
    AppRouteKey.SettingWorkflows -> Screen.SettingWorkflows
    AppRouteKey.SettingPermissions -> Screen.SettingPermissions
    AppRouteKey.SettingProvider -> Screen.SettingProvider
    AppRouteKey.Assistant -> Screen.Assistant
    AppRouteKey.SettingShizuku -> Screen.SettingShizuku
    AppRouteKey.SettingMcp -> Screen.SettingMcp
    AppRouteKey.Skills -> Screen.Skills
}

private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(ctx.getString(R.string.doctor_clipboard_label), text))
}
