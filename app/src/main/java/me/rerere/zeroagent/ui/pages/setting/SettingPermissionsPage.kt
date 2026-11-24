package me.rerere.zeroagent.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.permissions.PermissionInventory
import me.rerere.zeroagent.data.permissions.PermissionInventory.GrantAction
import me.rerere.zeroagent.data.permissions.PermissionInventory.Group
import me.rerere.zeroagent.data.permissions.PermissionInventory.Status
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.theme.CustomColors
import me.rerere.zeroagent.ui.context.LocalToaster
import com.dokar.sonner.ToastType
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent

/**
 * Auto-discovered permission overview. Reads every <uses-permission> the app declares plus
 * two virtual rows for the AccessibilityService / NotificationListener bindings, classifies
 * them by how the user grants each, and surfaces a tap-to-grant button per row.
 *
 * Future-proof: when a new <uses-permission> is added to the manifest it shows up here
 * automatically. The friendly-label map covers our currently-used dangerous perms; unmapped
 * ones use platform translations in the selected app language.
 */
@Composable
fun SettingPermissionsPage() {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val configuration = LocalConfiguration.current
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var rows by remember(context, configuration) { mutableStateOf(PermissionInventory.build(context)) }

    val refreshNow = {
        rows = PermissionInventory.build(context)
    }

    val runtimeRequester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshNow()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openSettings(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(PermissionInventory.appDetailsIntent(context))
            } catch (_: ActivityNotFoundException) {
                toaster.show(resources.getString(R.string.zeroagent_permission_open_failed), type = ToastType.Error)
            } catch (_: SecurityException) {
                toaster.show(resources.getString(R.string.zeroagent_permission_open_failed), type = ToastType.Error)
            }
        } catch (_: SecurityException) {
            toaster.show(resources.getString(R.string.zeroagent_permission_open_failed), type = ToastType.Error)
        }
    }

    val grouped = rows.groupBy { it.group }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_permissions)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.permission_privacy_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Group.entries.forEach { group ->
                val rowsForGroup = grouped[group] ?: return@forEach
                if (rowsForGroup.isEmpty()) return@forEach
                Text(
                    text = sectionTitle(group),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
                CardGroup {
                    rowsForGroup.forEach { row ->
                        val grantAction = row.grant
                        val click: (() -> Unit)? = when {
                            row.id in featureManagedPermissions && row.status == Status.DENIED -> null
                            grantAction is GrantAction.Runtime
                                && row.status != Status.GRANTED -> {
                                { runtimeRequester.launch(grantAction.permission) }
                            }
                            grantAction is GrantAction.SystemSettings -> {
                                { openSettings(grantAction.intent) }
                            }
                            else -> null
                        }
                        item(
                            onClick = click,
                            headlineContent = { Text(row.label) },
                            supportingContent = { Text(row.description) },
                            trailingContent = {
                                val statusText = when (row.status) {
                                    Status.GRANTED -> stringResource(R.string.setting_page_permissions_status_granted)
                                    Status.AUTO_GRANTED -> stringResource(R.string.setting_page_permissions_status_auto_granted)
                                    Status.SESSION_CONSENT -> stringResource(R.string.permission_privacy_each_session)
                                    Status.DENIED -> stringResource(if (grantAction is GrantAction.None) R.string.zeroagent_permission_unavailable else R.string.setting_page_permissions_status_denied)
                                }
                                val tint = when (row.status) {
                                    Status.GRANTED -> MaterialTheme.colorScheme.primary
                                    Status.AUTO_GRANTED, Status.SESSION_CONSENT -> MaterialTheme.colorScheme.onSurfaceVariant
                                    Status.DENIED -> MaterialTheme.colorScheme.error
                                }
                                androidx.compose.material3.Surface(
                                    color = tint.copy(alpha = .1f),
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                ) {
                                    Text(text = statusText, color = tint,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private val featureManagedPermissions = setOf(
    Manifest.permission.MANAGE_EXTERNAL_STORAGE,
    Manifest.permission.REQUEST_INSTALL_PACKAGES,
    Manifest.permission.SEND_SMS,
    Manifest.permission.READ_SMS,
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    Manifest.permission.SYSTEM_ALERT_WINDOW,
)

@Composable
private fun sectionTitle(group: Group): String = when (group) {
    Group.PrivacySensitive -> stringResource(R.string.permission_privacy_section)
    Group.ServicesAndIntegrations ->
        stringResource(R.string.setting_page_permissions_group_services)
    Group.SpecialAccess ->
        stringResource(R.string.setting_page_permissions_group_special)
    Group.Runtime ->
        stringResource(R.string.setting_page_permissions_group_runtime)
    Group.AutoGranted ->
        stringResource(R.string.setting_page_permissions_group_auto)
}
