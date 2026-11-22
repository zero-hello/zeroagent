package me.rerere.zeroagent.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.data.lab.LabPreferences
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.components.ui.ChatPrivacy
import me.rerere.zeroagent.ui.components.ui.Switch

@Composable
internal fun ChatAppearancePreferences() {
    val context = LocalContext.current
    var locked by remember(context) { mutableStateOf(ChatPrivacy.appLocked(context)) }
    var name by remember(context) { mutableStateOf(LabPreferences.get(context, "reply_name")) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("回覆偏好", style = MaterialTheme.typography.titleLarge)
        Text("對話當次的要求會優先。", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(32); LabPreferences.set(context, "reply_name", name) },
            label = { Text("稱呼（可留空）") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        ChoiceGroup("語氣", "reply_tone", "natural", listOf("natural" to "自然", "friendly" to "親切", "professional" to "專業"))
        ChoiceGroup("預設長短", "reply_length", "normal", listOf("short" to "簡短", "normal" to "適中", "detailed" to "詳細"))
        ChoiceGroup("按住說話", "hold_dictation", "true", listOf("true" to "開啟", "false" to "關閉"))
        ChoiceGroup("朗讀回覆", "read_reply", "false", listOf("true" to "開啟", "false" to "關閉"))
        CardGroup {
            item(
                headlineContent = { Text("App 隱私鎖") },
                supportingContent = { Text(if (ChatPrivacy.canLock(context)) "回到 ZeroAgent 時，使用裝置的生物辨識或解鎖憑證。" else "請先在 Android 設定螢幕解鎖方式。") },
                trailingContent = { Switch(checked = locked, enabled = locked || ChatPrivacy.canLock(context), onCheckedChange = { ChatPrivacy.setAppLock(context, it); locked = it }) },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChoiceGroup(title: String, key: String, default: String, values: List<Pair<String, String>>) {
    val context = LocalContext.current
    var selected by remember(context, key) { mutableStateOf(LabPreferences.get(context, key, default)) }
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { (value, label) ->
                FilterChip(selected = selected == value, onClick = { LabPreferences.set(context, key, value); selected = value }, label = { Text(label) })
            }
        }
    }
}
