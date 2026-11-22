package me.rerere.zeroagent.ui.components.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.view.Window
import android.view.WindowManager
import java.util.WeakHashMap
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.rerere.zeroagent.R
import kotlin.uuid.Uuid

internal object ChatPrivacy {
    fun prefs(context: Context) = context.getSharedPreferences("chat_privacy", Context.MODE_PRIVATE)
    fun canLock(context: Context) = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    fun appLocked(context: Context) = prefs(context).getBoolean("app", false)
    fun chatLocked(context: Context, id: Uuid) = prefs(context).getBoolean("chat:$id", false)
    fun setAppLock(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("app", enabled).apply()
    fun setChatLock(context: Context, id: Uuid, enabled: Boolean) = prefs(context).edit().putBoolean("chat:$id", enabled).apply()
}

@Composable
internal fun rememberPrivacyLock(conversation: Uuid? = null): Boolean {
    val context = LocalContext.current
    val preferences = remember(context) { ChatPrivacy.prefs(context) }
    val key = conversation?.let { "chat:$it" } ?: "app"
    var locked by remember(preferences, key) { mutableStateOf(preferences.getBoolean(key, false)) }
    DisposableEffect(preferences, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == null || changed == key) locked = preferences.getBoolean(key, false)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        locked = preferences.getBoolean(key, false)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return locked
}

private object PrivacyWindowProtection {
    private data class Use(var count: Int, val originallySecure: Boolean)
    private val windows = WeakHashMap<Window, Use>()
    private const val FLAG = WindowManager.LayoutParams.FLAG_SECURE

    fun acquire(window: Window) {
        val use = windows.getOrPut(window) { Use(0, window.attributes.flags and FLAG != 0) }
        if (use.count++ == 0) window.addFlags(FLAG)
    }

    fun release(window: Window) {
        val use = windows[window] ?: return
        if (--use.count == 0) {
            windows.remove(window)
            if (!use.originallySecure) window.clearFlags(FLAG)
        }
    }
}

@Composable
internal fun ChatPrivacyGate(required: Boolean, key: Any, content: @Composable () -> Unit) {
    if (!required) {
        content()
        return
    }
    val activity = LocalActivity.current as? FragmentActivity
    val window = activity?.window
    DisposableEffect(window) {
        window?.let(PrivacyWindowProtection::acquire)
        onDispose { window?.let(PrivacyWindowProtection::release) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var unlocked by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycle, key) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && unlocked) unlocked = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (unlocked) {
        content()
        return
    }
    val prompt = remember(activity, key) {
        activity?.let {
            BiometricPrompt(it, ContextCompat.getMainExecutor(it), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                    error = null
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    error = errString.toString()
                }
            })
        }
    }
    val title = stringResource(R.string.chat_locked_title)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            error?.let { Text(it) }
            Button(enabled = prompt != null, onClick = {
                prompt?.authenticate(BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build())
            }) { Text(stringResource(R.string.chat_unlock_action)) }
        }
    }
}
