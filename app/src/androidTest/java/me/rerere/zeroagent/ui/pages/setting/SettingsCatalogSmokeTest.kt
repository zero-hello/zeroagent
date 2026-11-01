package me.rerere.zeroagent.ui.pages.setting

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.zeroagent.R
import me.rerere.zeroagent.RouteActivity
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.getCurrentChatModel
import me.rerere.zeroagent.ui.hooks.writeBooleanPreference
import me.rerere.zeroagent.ui.hooks.writeStringPreference
import me.rerere.zeroagent.utils.ZeroAgentLocale
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import me.rerere.zeroagent.ui.pages.gettingstarted.GETTING_STARTED_SEEN_KEY
import me.rerere.zeroagent.ui.pages.stats.StatsRange
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real-clock navigation complements the isolated Compose component tests. */
@RunWith(AndroidJUnit4::class)
class SettingsCatalogSmokeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    @Test
    fun everyCatalogDestinationOpensAndReturnsToSearch() {
        val context = instrumentation.targetContext
        require(context.packageName == "app.zeroagent.debug")
        val arguments = InstrumentationRegistry.getArguments()
        arguments.getString("catalogLocale")?.let { assertTrue(ZeroAgentLocale.select(context, it)) }
        arguments.getString("catalogDark")?.let {
            context.writeStringPreference("colorMode", if (it.toBooleanStrict()) "DARK" else "LIGHT")
        }
        context.writeBooleanPreference(GETTING_STARTED_SEEN_KEY, true)
        val directory = File(context.getExternalFilesDir(null), "ui-polish/pages-final").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val manifest = File(directory, "verified-titles.tsv").apply {
            writeText("route\tsearch label\tvisible page title\n")
        }
        ActivityScenario.launch(RouteActivity::class.java).use { scenario ->
            var resources = context.resources
            scenario.onActivity { resources = it.resources }
            await("welcome") {
                // Decline the optional first-run download through its real UI; browsing settings
                // must not start a model download or a native voice session.
                if (visibleText(resources.getString(R.string.memory12_title)) != null) {
                    visibleText(resources.getString(R.string.memory12_cancel))?.let(::click)
                }
                visibleText(resources.getString(R.string.zeroagent6_welcome_title)) != null
            }
            clickDescription(resources.getString(R.string.menu))
            clickText(resources.getString(R.string.settings))
            clickDescription(resources.getString(R.string.accessibility_search))
            awaitFullSearch()

            val store = org.koin.java.KoinJavaComponent.getKoin().get<SettingsStore>()
            await("loaded model selection") { !store.settingsFlow.value.init }
            val settings = store.settingsFlow.value
            settingsSearchIndex(developerMode = false, modelId = settings.chatModelId.toString()).forEach { entry ->
                val label = resources.getString(entry.titleRes)
                val pageTitle = resources.getString(when (entry.route) {
                    Screen.Log -> R.string.zeroagent6s_logs
                    Screen.SettingRoot -> R.string.zeroagent6s_root_title
                    Screen.ImageGen -> R.string.imggen_page_title
                    Screen.VideoGen -> R.string.videogen_page_title
                    Screen.GettingStarted -> R.string.getting_started_title
                    is Screen.ModelMemory -> if (settings.getCurrentChatModel() == null) {
                        // ModelMemory intentionally opens model settings when no chat model exists.
                        R.string.setting_model_page_title
                    } else R.string.assistant_page_tab_memory
                    else -> entry.titleRes
                })
                try {
                    val field = checkNotNull(searchField())
                    assertTrue("Search accepts the query", field.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, label) },
                    ))
                    await("updated search query $label") { searchField()?.text?.toString() == label }
                    scenario.onActivity { it.window.insetsController?.hide(WindowInsets.Type.ime()) }
                    clickText(label)
                    await("destination $pageTitle") { visibleText(pageTitle) != null && searchField() == null }
                    if (entry.route == Screen.SettingSubAgents) {
                        val addLabel = resources.getString(R.string.setting_sub_agents_page_add)
                        await("one primary add-profile action") {
                            nodes().count { it.text?.toString() == addLabel && it.isVisibleToUser } == 1
                        }
                    }
                    if (entry.route == Screen.Stats) {
                        await("readable date-range labels inside the viewport") {
                            val rootBounds = Rect().also(checkNotNull(automation.rootInActiveWindow)::getBoundsInScreen)
                            StatsRange.entries.all { range ->
                                visibleText(resources.getString(R.string.stats_page_daily_window, range.days))?.let {
                                    val bounds = Rect().also(it::getBoundsInScreen)
                                    bounds.left >= rootBounds.left && bounds.right <= rootBounds.right &&
                                        bounds.height() <= resources.displayMetrics.density * 64
                                } == true
                            }
                        }
                    }
                    capture(File(directory, "${entry.route.javaClass.simpleName}.png"))
                    manifest.appendText("${entry.route.javaClass.simpleName}\t$label\t$pageTitle\n")
                    if (entry.route == Screen.GettingStarted) {
                        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                    } else clickDescription(resources.getString(R.string.back))
                    awaitFullSearch()
                } catch (error: Throwable) {
                    File(directory, "failed-${entry.route.javaClass.simpleName}.txt").writeText(
                        "Expected: $pageTitle\n" + nodes().joinToString("\n") {
                            val bounds = Rect().also(it::getBoundsInScreen)
                            "${it.className}: text=${it.text} description=${it.contentDescription} visible=${it.isVisibleToUser} bounds=$bounds"
                        },
                    )
                    capture(File(directory, "failed-${entry.route.javaClass.simpleName}.png"))
                    throw error
                }
            }
        }
    }

    private fun awaitFullSearch() = await("full-size returned search field") {
        val root = automation.rootInActiveWindow ?: return@await false
        val field = searchField() ?: return@await false
        val rootBounds = Rect().also(root::getBoundsInScreen)
        val fieldBounds = Rect().also(field::getBoundsInScreen)
        field.isVisibleToUser && fieldBounds.width() > rootBounds.width() * .85f
    }

    private fun searchField() = nodes().firstOrNull {
        it.viewIdResourceName == "settings_search_input" && it.isVisibleToUser
    }

    private fun visibleText(text: String) = nodes().firstOrNull {
        it.text?.toString() == text && !it.isEditable && it.isVisibleToUser
    }

    private fun clickText(text: String) {
        var nextScrollAt = 0L
        await("enabled clickable text $text") {
            val candidate = visibleText(text)
            if (candidate != null) click(candidate)
            else {
                if (SystemClock.uptimeMillis() >= nextScrollAt) {
                    nodes().firstOrNull { it.isScrollable && !it.isEditable && it.isVisibleToUser &&
                        it.actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }
                    }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    nextScrollAt = SystemClock.uptimeMillis() + 400
                }
                false
            }
        }
    }

    private fun clickDescription(description: String) {
        await("enabled action $description") {
            nodes().firstOrNull { it.contentDescription?.toString() == description && it.isVisibleToUser }?.let(::click) == true
        }
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        return target?.isEnabled == true && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        // Reapplying the same service config clears UiAutomation's stale window cache on Android 13.
        automation.serviceInfo?.let { automation.serviceInfo = it }
        val root = automation.rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            result.add(node)
            repeat(node.childCount) { index -> node.getChild(index)?.let(queue::addLast) }
        }
        return result
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        do {
            if (condition()) return
            SystemClock.sleep(50) // Poll actual UI state; do not control the app's animation clock.
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Timed out waiting for $description")
    }

    private fun capture(file: File) {
        var bitmap = checkNotNull(automation.takeScreenshot()) { "System screenshot unavailable" }
        var lastChange = SystemClock.uptimeMillis()
        val deadline = lastChange + 2_500
        // Accessibility can settle before the final frame (icons and system bars) is committed.
        // Poll for stable rendered pixels rather than accepting that first intermediate frame.
        while (SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
            val next = checkNotNull(automation.takeScreenshot()) { "System screenshot unavailable" }
            val unchanged = bitmap.sameAs(next)
            bitmap.recycle()
            bitmap = next
            if (!unchanged) lastChange = SystemClock.uptimeMillis()
            if (SystemClock.uptimeMillis() - lastChange >= 300) break
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
