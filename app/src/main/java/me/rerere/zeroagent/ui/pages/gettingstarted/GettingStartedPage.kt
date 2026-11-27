package me.rerere.zeroagent.ui.pages.gettingstarted

import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.background
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import me.rerere.zeroagent.ui.designsystem.ZeroCapsuleButton as Button
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import me.rerere.zeroagent.ui.components.ui.ZeroAgentLanguagePreferences
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.ui.ZeroAgentCard as Card
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.ai.tools.LocalToolOption
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.getCurrentAssistant
import me.rerere.zeroagent.data.permissions.PermissionInventory
import me.rerere.zeroagent.data.permissions.isAggressive
import me.rerere.zeroagent.data.permissions.keepAliveVendorOf
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.context.LocalSettings
import me.rerere.zeroagent.ui.hooks.writeBooleanPreference
import me.rerere.zeroagent.ui.theme.CustomColors
import me.rerere.zeroagent.utils.plus

/** Preference key: once the user has seen (or dismissed) the first-run guide, never show it again. */
const val GETTING_STARTED_SEEN_KEY = "getting_started_seen"

enum class GettingStartedStep { Model, Tools, Permissions }

/**
 * A model is "configured" when at least one enabled provider carries at least one model.
 * That is exactly what the user sees after pasting an API key: the provider light flips on
 * and its model list stops being empty.
 */
fun Settings.hasConfiguredModel(): Boolean =
    providers.any { provider -> provider.enabled && provider.models.any { it.type == me.rerere.ai.provider.ModelType.CHAT } }

/**
 * Pure Kotlin so it can be unit-tested on the JVM — the caller supplies [deniedPermissions]
 * from [PermissionInventory] (which needs a Context). False means "still to do".
 */
fun evaluateGettingStartedSteps(settings: Settings, deniedPermissions: Int): Map<GettingStartedStep, Boolean> =
    mapOf(
        GettingStartedStep.Model to settings.hasConfiguredModel(),
        GettingStartedStep.Tools to settings.assistants.any { a ->
            a.localTools.any { it != LocalToolOption.TimeInfo }
        },
        GettingStartedStep.Permissions to (deniedPermissions == 0),
    )

/**
 * First-run guide. The stock ZeroAgent opens straight into an empty chat with no model, no
 * tools and no permissions — so the single most common complaint is "I installed it and
 * don't know what to do". This page turns that dead end into an ordered, tappable checklist
 * that points at the exact setting each capability lives in.
 */
@Composable
fun GettingStartedPage() {
    val settings = LocalSettings.current
    val nav = LocalNavController.current
    val ctx = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Only the two service bindings (accessibility + notification listener) gate the app's
    // headline capabilities and are the ones users can never find on their own. The other
    // runtime permissions stay "ask when needed", so we don't count them here.
    var pendingServicePermissions by remember(ctx) { mutableIntStateOf(2) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(ctx, lifecycle) {
        fun refresh() {
            pendingServicePermissions = runCatching {
                PermissionInventory.build(ctx).count { row ->
                    row.group == PermissionInventory.Group.ServicesAndIntegrations &&
                        row.status == PermissionInventory.Status.DENIED
                }
            }.getOrDefault(2)
        }
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val steps = evaluateGettingStartedSteps(settings, pendingServicePermissions)
    val doneCount = steps.count { it.value }
    val assistantId = settings.getCurrentAssistant().id.toString()
    // Only aggressive OEM ROMs reap the accessibility service in the background; for everyone
    // else the tip is noise, so gate it on the vendor.
    val keepAliveVendor = keepAliveVendorOf(Build.MANUFACTURER, Build.BRAND)

    fun finish() {
        ctx.writeBooleanPreference(GETTING_STARTED_SEEN_KEY, true)
        nav.popBackStack()
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.getting_started_title)) },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                IntroCard(done = doneCount, total = steps.size)
            }

            item(key = "language") {
                ZeroAgentLanguagePreferences()
            }

            item(key = "step-model") {
                StepCard(
                    index = 1,
                    titleRes = R.string.getting_started_step_model_title,
                    descRes = R.string.getting_started_step_model_desc,
                    done = steps[GettingStartedStep.Model] == true,
                    onAction = { nav.navigate(Screen.SettingProvider) },
                )
            }

            item(key = "step-tools") {
                StepCard(
                    index = 2,
                    titleRes = R.string.getting_started_step_tools_title,
                    descRes = R.string.getting_started_step_tools_desc,
                    done = steps[GettingStartedStep.Tools] == true,
                    onAction = { nav.navigate(Screen.AssistantLocalTool(assistantId)) },
                )
            }

            item(key = "step-permissions") {
                StepCard(
                    index = 3,
                    titleRes = R.string.getting_started_step_permissions_title,
                    descRes = R.string.getting_started_step_permissions_desc,
                    done = steps[GettingStartedStep.Permissions] == true,
                    onAction = { nav.navigate(Screen.SettingPermissions) },
                )
            }

            if (keepAliveVendor.isAggressive) {
                item(key = "keepalive") {
                    KeepAliveTip(onAction = { nav.navigate(Screen.SettingAccessibility) })
                }
            }

            item(key = "actions") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(
                        onClick = { finish() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.getting_started_finish))
                    }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { finish() }) {
                        Text(stringResource(R.string.getting_started_later))
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroCard(done: Int, total: Int) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.getting_started_intro_title), style = MaterialTheme.typography.titleMediumEmphasized)
            Text(stringResource(R.string.getting_started_intro_desc), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.getting_started_progress, done, total),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StepCard(index: Int, titleRes: Int, descRes: Int, done: Boolean, onAction: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(32.dp).background(
                if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                CircleShape,
            ), contentAlignment = Alignment.Center) {
                Text(if (done) "✓" else index.toString(), style = MaterialTheme.typography.labelLarge,
                    color = if (done) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(descRes), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (done) Text(stringResource(R.string.getting_started_done),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                else TextButton(shapes = ButtonDefaults.shapes(), onClick = onAction) { Text(stringResource(R.string.getting_started_go)) }
            }
        }
    }
}

@Composable
private fun KeepAliveTip(onAction: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.getting_started_keepalive_title), style = MaterialTheme.typography.titleSmallEmphasized)
            Text(stringResource(R.string.getting_started_keepalive_desc), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.getting_started_keepalive_open))
            }
        }
    }
}
