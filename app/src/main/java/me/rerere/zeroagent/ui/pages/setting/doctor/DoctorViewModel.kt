package me.rerere.zeroagent.ui.pages.setting.doctor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.zeroagent.R
import me.rerere.zeroagent.utils.localizedErrorMessage
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext

class DoctorViewModel(
    private val app: Context,
    private val checks: DoctorChecks,
) : ViewModel() {
    data class State(
        val results: List<DoctorCheck> = emptyList(),
        val running: Boolean = false,
        val lastRunAtMs: Long? = null,
    )

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()
    private var runJob: Job? = null
    private var locale = app.zeroAgentLocalizedContext().resources.configuration.locales.toLanguageTags()

    init { runAll() }

    fun onLocaleChanged(tag: String) {
        if (tag == locale) return
        locale = tag
        runJob?.cancel()
        // Stored results contain formatted text. Never keep old-language rows on screen.
        mutable.value = State()
        runAll()
    }

    fun runAll() {
        if (mutable.value.running) return
        runJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(running = true)
            val results = try {
                checks.runAll()
            } catch (cancel: CancellationException) { throw cancel
            } catch (failure: Throwable) {
                val localized = app.zeroAgentLocalizedContext()
                listOf(DoctorCheck(
                    id = "doctor.error", category = DoctorCategory.Diagnostics,
                    label = localized.getString(R.string.doctor_error_internal),
                    detail = localized.localizedErrorMessage(failure), severity = Severity.FAIL,
                ))
            }
            mutable.value = State(results, running = false, lastRunAtMs = System.currentTimeMillis())
        }
    }

    suspend fun applyAutoFix(fix: FixAction.AutoFix): AutoFixResult {
        val result = try { fix.run()
        } catch (cancel: CancellationException) { throw cancel
        } catch (failure: Throwable) { AutoFixResult(false, app.localizedErrorMessage(failure)) }
        runAll()
        return result
    }

    fun buildReport(): String = DoctorReport.format(app.zeroAgentLocalizedContext(), state.value.results)
}
