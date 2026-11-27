package me.rerere.zeroagent.ui.components.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dokar.sonner.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Mic01
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.context.LocalToaster

@Composable
internal fun HoldToDictate(onText: (String) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val textCallback by rememberUpdatedState(onText)
    val toaster = LocalToaster.current
    var listening by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toaster.show(resources.getString(R.string.dictate_permission), type = ToastType.Warning)
    }
    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                processing = false
                if (error != SpeechRecognizer.ERROR_CLIENT) {
                    toaster.show(resources.getString(R.string.dictate_no_result), type = ToastType.Warning)
                }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                processing = false
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let(textCallback)
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { recognizer?.cancel(); recognizer?.destroy() }
    }
    fun start(): Boolean {
        if (recognizer == null) {
            toaster.show(resources.getString(R.string.dictate_unavailable), type = ToastType.Warning)
            return false
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }
        if (processing) return false
        return try {
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1))
            listening = true
            processing = true
            true
        } catch (_: RuntimeException) {
            toaster.show(resources.getString(R.string.dictate_unavailable), type = ToastType.Warning)
            false
        }
    }
    val label = stringResource(if (listening) R.string.dictate_recording else R.string.dictate_hold)
    Surface(modifier = Modifier.size(48.dp).semantics {
        role = Role.Button
        contentDescription = label
        onClick {
            if (listening) recognizer?.stopListening() else start()
            true
        }
    }.pointerInput(recognizer) {
        detectTapGestures(onPress = {
            if (start()) {
                if (tryAwaitRelease()) recognizer?.stopListening() else {
                    recognizer?.cancel()
                    listening = false
                    processing = false
                }
            }
        })
    }, shape = CircleShape, color = if (listening) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment = Alignment.Center) { Icon(HugeIcons.Mic01, contentDescription = null) }
    }
}
