package me.rerere.zeroagent.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.ai.ModelPersona
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.utils.plus

@Composable
internal fun ModelPersonaPage(settings: Settings, vm: SettingVM, contentPadding: PaddingValues) {
    val selected = ModelPersona.resolve(settings.modelPersonaId)
    LazyColumn(
        modifier = Modifier.fillMaxSize().selectableGroup(),
        contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(stringResource(R.string.model_persona_hint), style = MaterialTheme.typography.bodyMedium)
        }
        items(ModelPersona.entries, key = { it.id }) { persona ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(selected == persona, role = Role.RadioButton) {
                        vm.updateSettings { it.copy(modelPersonaId = persona.id) }
                    }.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == persona, onClick = null)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(persona.title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(persona.description), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
