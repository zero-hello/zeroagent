package me.rerere.zeroagent.ui.pages.backup.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.sync.PortableTemplate
import me.rerere.zeroagent.data.sync.PortableTemplateCodec
import me.rerere.zeroagent.data.sync.encodeTemplate
import me.rerere.zeroagent.data.sync.toPortableTemplate
import me.rerere.zeroagent.data.sync.toPortableTemplateOrNull
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.components.ui.QRCode
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import me.rerere.zeroagent.ui.context.LocalToaster
import me.rerere.zeroagent.ui.pages.backup.BackupVM

private enum class TemplatePanel { Pick, Export, Import, Code }

@Composable
internal fun TemplateBackupActions(vm: BackupVM) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var panel by remember { mutableStateOf<TemplatePanel?>(null) }
    var template by remember { mutableStateOf<PortableTemplate?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    fun close() {
        if (importing) return
        panel = null
        template = null
        code = null
        error = null
    }

    fun readTemplate(raw: String) {
        runCatching { PortableTemplateCodec.decode(raw) }.onSuccess {
            template = it
            error = null
            panel = TemplatePanel.Import
        }.onFailure {
            toaster.show(resources.getString(R.string.template_invalid), type = ToastType.Error)
        }
    }

    val scan = rememberLauncherForActivityResult(ScanQRCode()) { result ->
        when (result) {
            is QRResult.QRSuccess -> readTemplate(result.content.rawValue.orEmpty())
            QRResult.QRUserCanceled -> Unit
            else -> toaster.show(resources.getString(R.string.template_scan_failed), type = ToastType.Error)
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val export = code ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Output unavailable")
                    output.use { it.write(export.toByteArray(Charsets.US_ASCII)) }
                }
                toaster.show(resources.getString(R.string.template_saved), type = ToastType.Success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toaster.show(resources.getString(R.string.template_save_failed), type = ToastType.Error)
            }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val value = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri) ?: error("Input unavailable")
                    input.bufferedReader(Charsets.US_ASCII).use { reader ->
                        buildString {
                            while (length <= PortableTemplateCodec.MAX_QR_LENGTH) {
                                val next = reader.read()
                                if (next == -1) break
                                append(next.toChar())
                            }
                        }
                    }
                }
                readTemplate(value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toaster.show(resources.getString(R.string.template_invalid), type = ToastType.Error)
            }
        }
    }

    CardGroup {
        item(
            headlineContent = { Text(stringResource(R.string.template_export)) },
            supportingContent = { Text(stringResource(R.string.template_summary)) },
            onClick = { panel = TemplatePanel.Pick },
        )
        item(headlineContent = { Text(stringResource(R.string.template_scan)) }, onClick = { scan.launch(null) })
        item(headlineContent = { Text(stringResource(R.string.template_open)) }, onClick = { open.launch(arrayOf("text/plain")) })
    }

    val current = template
    when (panel) {
        TemplatePanel.Pick -> {
            val sources = remember(settings.providers, settings.mcpServers, settings.assistants) {
                settings.providers.mapNotNull { it.toPortableTemplateOrNull() } +
                    settings.mcpServers.map { it.toPortableTemplate() } +
                    settings.assistants.filter { it.systemPrompt.isNotBlank() }.map {
                        PortableTemplate(PortableTemplate.Kind.PROMPT, it.name, prompt = it.systemPrompt)
                    }
            }
            ZeroAgentAlertDialog(
                onDismissRequest = ::close,
                title = { Text(stringResource(R.string.template_export)) },
                text = {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        if (sources.isEmpty()) Text(stringResource(R.string.template_empty))
                        sources.forEach { source ->
                            TemplateTextButton(
                                label = "${stringResource(source.kind.label())} · ${source.name}",
                                onClick = { template = source; panel = TemplatePanel.Export },
                            )
                        }
                    }
                },
                confirmButton = { TemplateTextButton(stringResource(android.R.string.cancel), onClick = ::close) },
            )
        }
        TemplatePanel.Export, TemplatePanel.Import -> if (current != null) {
            val importingTemplate = panel == TemplatePanel.Import
            ZeroAgentAlertDialog(
                onDismissRequest = ::close,
                title = { Text(stringResource(if (importingTemplate) R.string.template_preview_import else R.string.template_preview_export)) },
                text = {
                    Column(
                        Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("${stringResource(current.kind.label())} · ${current.name}")
                        current.format?.let { Text(it.label()) }
                        current.endpoint?.let { Text(it) }
                        current.requestPath?.let { Text(it) }
                        if (current.models.isNotEmpty()) Text(current.models.joinToString("\n"))
                        current.prompt?.let { Text(it) }
                        if (importingTemplate && current.kind != PortableTemplate.Kind.PROMPT) {
                            Text(stringResource(R.string.template_disabled_import))
                        }
                        if (importingTemplate && current.kind == PortableTemplate.Kind.PROMPT) {
                            Text(stringResource(R.string.template_prompt_review))
                        }
                        error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TemplateTextButton(
                        label = stringResource(if (importingTemplate) R.string.template_confirm_import else R.string.template_generate_qr),
                        enabled = !importing,
                        onClick = {
                            if (importingTemplate) {
                                importing = true
                                scope.launch {
                                    try {
                                        vm.importPortableTemplate(current)
                                        importing = false
                                        close()
                                        toaster.show(resources.getString(R.string.template_imported), type = ToastType.Success)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        error = resources.getString(R.string.template_import_failed)
                                    } finally {
                                        importing = false
                                    }
                                }
                            } else {
                                runCatching { settings.encodeTemplate(current) }.onSuccess {
                                    code = it
                                    panel = TemplatePanel.Code
                                }.onFailure { error = resources.getString(R.string.template_export_rejected) }
                            }
                        },
                    )
                },
                dismissButton = { TemplateTextButton(stringResource(android.R.string.cancel), enabled = !importing, onClick = ::close) },
            )
        }
        TemplatePanel.Code -> code?.let { payload ->
            ZeroAgentAlertDialog(
                onDismissRequest = ::close,
                title = { Text(stringResource(R.string.template_qr_title)) },
                text = {
                    QRCode(
                        value = payload,
                        modifier = Modifier.size(250.dp),
                        color = Color.Black,
                        backgroundColor = Color.White,
                        description = stringResource(R.string.template_qr_title),
                    )
                },
                confirmButton = { TemplateTextButton(stringResource(R.string.template_save)) { save.launch("ZeroAgent-template.txt") } },
                dismissButton = { TemplateTextButton(stringResource(android.R.string.ok), onClick = ::close) },
            )
        }
        null -> Unit
    }
}

@Composable
private fun TemplateTextButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(shapes = ButtonDefaults.shapes(), onClick = onClick, enabled = enabled) { Text(label) }
}

private fun PortableTemplate.Kind.label(): Int = when (this) {
    PortableTemplate.Kind.PROVIDER -> R.string.template_provider
    PortableTemplate.Kind.MCP -> R.string.template_mcp
    PortableTemplate.Kind.PROMPT -> R.string.template_prompt
}

private fun PortableTemplate.Format.label(): String = when (this) {
    PortableTemplate.Format.OPENAI_CHAT -> "Chat Completions"
    PortableTemplate.Format.OPENAI_RESPONSES -> "OpenAI Responses"
    PortableTemplate.Format.COHERE_V2 -> "Cohere V2"
    PortableTemplate.Format.GOOGLE -> "Gemini API"
    PortableTemplate.Format.ANTHROPIC -> "Anthropic Messages"
    PortableTemplate.Format.MCP_SSE -> "MCP SSE"
    PortableTemplate.Format.MCP_HTTP -> "MCP Streamable HTTP"
}
