package me.rerere.zeroagent.utils

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.flow.asStateFlow

/** Empty selection follows the system. Android 13+ also exposes it in system App languages. */
object ZeroAgentLocale {
    private val mutableChanges = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes = mutableChanges.asStateFlow()
    private fun changed() { mutableChanges.value = mutableChanges.value + 1 }
    private const val PREFS = "zeroagent_app_language"
    private const val KEY = "language_tag"

    fun selectedTag(context: Context): String = ZeroAgentLanguagePolicy.normalize(
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags().orEmpty()
        } else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
    )

    fun wrap(context: Context): Context {
        // The application resources may still carry the previous explicit choice. Always derive
        // the empty choice from SYSTEM resources, including during Activity.attachBaseContext.
        val locales = selectedTag(context).let { tag ->
            if (tag.isEmpty()) Resources.getSystem().configuration.locales else LocaleList.forLanguageTags(tag)
        }
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(locales)
            if (!locales.isEmpty) setLayoutDirection(locales[0])
        })
    }

    /** A failed preference/service update is reported to the page instead of crashing it. */
    fun select(context: Context, requestedTag: String): Boolean {
        val tag = ZeroAgentLanguagePolicy.normalize(requestedTag)
        if (tag == selectedTag(context)) return true
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return false
            return try {
                manager.applicationLocales = LocaleList.forLanguageTags(tag)
                changed()
                true
            } catch (_: IllegalArgumentException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()) return false
        val locales = if (tag.isEmpty()) Resources.getSystem().configuration.locales else LocaleList.forLanguageTags(tag)
        val app = context.applicationContext
        val config = Configuration(app.resources.configuration).apply {
            setLocales(locales)
            if (!locales.isEmpty) setLayoutDirection(locales[0])
        }
        @Suppress("DEPRECATION")
        app.resources.updateConfiguration(config, app.resources.displayMetrics)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        if (!locales.isEmpty) LocaleList.setDefault(locales)
        changed()
        return true
    }
}

fun Context.zeroAgentLocalizedContext(): Context = ZeroAgentLocale.wrap(this)
