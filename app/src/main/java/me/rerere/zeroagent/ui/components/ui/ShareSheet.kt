package me.rerere.zeroagent.ui.components.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import me.rerere.zeroagent.ui.designsystem.ZeroIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import me.rerere.zeroagent.ui.components.ui.ZeroAgentModalBottomSheet as ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Share03
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.sync.PortableTemplateCodec
import me.rerere.zeroagent.data.sync.encodeTemplate
import me.rerere.zeroagent.data.sync.toDisabledProvider
import me.rerere.zeroagent.data.sync.toPortableTemplateOrNull
import me.rerere.zeroagent.utils.JsonInstant
import kotlin.io.encoding.Base64

@Composable
fun ShareSheet(
    state: ShareSheetState,
) {
    val context = LocalContext.current
    if (state.isShow) {
        val provider = state.currentProvider
        val payload = remember(provider) { runCatching { provider?.encodeForShare() }.getOrNull() }
        ModalBottomSheet(
            onDismissRequest = {
                state.dismiss()
            },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.share_sheet_title), style = MaterialTheme.typography.titleLarge)

                    IconButton(
                        enabled = payload != null,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND)
                            intent.type = "text/plain"
                            intent.putExtra(
                                Intent.EXTRA_TEXT,
                                payload ?: return@IconButton
                            )
                            try {
                                context.startActivity(Intent.createChooser(intent, null))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    ) {
                        Icon(HugeIcons.Share03, null)
                    }
                }

                if (payload == null) {
                    Text(stringResource(R.string.template_export_rejected))
                } else {
                    Text(stringResource(R.string.template_summary))
                    QRCode(
                        value = payload,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).fillMaxWidth().aspectRatio(1f),
                        color = me.rerere.zeroagent.ui.designsystem.ZeroTheme.qrInk,
                        backgroundColor = me.rerere.zeroagent.ui.designsystem.ZeroTheme.qrPaper,
                        description = stringResource(R.string.template_qr_title),
                    )
                }
            }
        }
    }
}

fun ProviderSetting.encodeForShare(): String {
    val template = requireNotNull(toPortableTemplateOrNull())
    return Settings.dummy().copy(providers = listOf(this)).encodeTemplate(template)
}

fun decodeProviderSetting(value: String): ProviderSetting {
    if (value.startsWith("ZA1:")) return PortableTemplateCodec.decode(value).toDisabledProvider()
    require(value.length <= 16_384 && value.startsWith("ai-provider:v1:")) { "Invalid provider setting string" }

    // 去掉前缀
    val base64Str = value.removePrefix("ai-provider:v1:")

    // Base64解码
    val jsonBytes = Base64.decode(base64Str)
    val jsonStr = jsonBytes.decodeToString()

    val legacy = JsonInstant.decodeFromString<ProviderSetting>(jsonStr)
    return PortableTemplateCodec.decode(legacy.encodeForShare()).toDisabledProvider()
}

class ShareSheetState {
    private var show by mutableStateOf(false)
    val isShow get() = show

    private var provider by mutableStateOf<ProviderSetting?>(null)
    val currentProvider get() = provider

    fun show(provider: ProviderSetting) {
        this.show = true
        this.provider = provider
    }

    fun dismiss() {
        this.show = false
    }
}

@Composable
fun rememberShareSheetState(): ShareSheetState {
    return ShareSheetState()
}
