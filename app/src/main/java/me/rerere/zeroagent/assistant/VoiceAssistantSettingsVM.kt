package me.rerere.zeroagent.assistant

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.nativeVoiceSelection
import me.rerere.zeroagent.utils.localizedErrorMessage

internal data class AssistantSetupState(
    val busy: Boolean = false,
    val ready: Boolean = true,
    val issue: AssistantIssue? = null,
    val detail: String? = null,
    val saveFailed: Boolean = false,
)

internal class VoiceAssistantSettingsVM(application: Application, private val store: SettingsStore) : AndroidViewModel(application) {
    val settings = store.settingsFlow
    private val app = application
    private val mutable = MutableStateFlow(AssistantSetupState())
    val state = mutable.asStateFlow()
    private var settingWrite: Job? = null

    fun update(change: (VoiceAssistantSettings) -> VoiceAssistantSettings) {
        settingWrite = viewModelScope.launch {
            try {
                store.updateVoiceAssistant { change(it) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                report(error)
            }
        }
    }

    fun permissionDenied(issue: AssistantIssue) {
        mutable.update { it.copy(issue = issue, detail = null) }
    }

    fun clearIssue() {
        mutable.update { it.copy(issue = null, detail = null, saveFailed = false) }
    }

    fun enable(value: Boolean) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                if (!value) {
                    store.updateVoiceAssistant { it.copy(enabled = false, wakeEnabled = false) }
                } else {
                    settingWrite?.join()
                    if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                        throw AssistantFailure(AssistantIssue.MICROPHONE_PERMISSION)
                    if (store.settingsFlow.value.nativeVoiceSelection() == null) throw AssistantFailure(AssistantIssue.MODEL_UNAVAILABLE)
                    store.updateVoiceAssistant { it.copy(enabled = true) }
                    clearIssue()
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                report(error)
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    private fun report(error: Throwable, fallback: AssistantIssue? = null) {
        val issue = (error as? AssistantFailure)?.issue ?: fallback ?: AssistantIssue.SERVICE_STOPPED
        val detail = (error as? AssistantFailure)?.issue?.name ?: app.localizedErrorMessage(error)
        mutable.update { it.copy(issue = issue, detail = detail) }
    }
}
