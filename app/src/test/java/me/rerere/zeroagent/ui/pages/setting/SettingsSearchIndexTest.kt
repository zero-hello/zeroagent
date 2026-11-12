package me.rerere.zeroagent.ui.pages.setting

import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchIndexTest {

    @Test
    fun `voice conversation and read aloud both resolve to speech services`() {
        val entry = settingsSearchIndex(developerMode = false).single { it.route == Screen.SettingSpeech }

        assertEquals(R.string.zeroagent11_speech_services, entry.titleRes)
        assertTrue(entry.altTitles.contains(R.string.zeroagent11_voice_conversation))
        assertTrue(entry.altTitles.contains(R.string.zeroagent11_read_aloud))
    }

    @Test
    fun `Gemini account setup is discoverable through providers`() {
        val entry = settingsSearchIndex(developerMode = false).single { it.route == Screen.SettingProvider }

        assertTrue(entry.altTitles.contains(R.string.gemini_enable))
    }

    @Test
    fun `no duplicate routes with developer mode off`() {
        val entries = settingsSearchIndex(developerMode = false)
        val routes = entries.map { it.route }
        assertEquals(routes.size, routes.distinct().size)
    }

    @Test
    fun `no duplicate routes with developer mode on`() {
        val entries = settingsSearchIndex(developerMode = true)
        val routes = entries.map { it.route }
        assertEquals(routes.size, routes.distinct().size)
    }

    @Test
    fun `every entry has non-zero resource ids`() {
        val entries = settingsSearchIndex(developerMode = true)
        entries.forEach { entry ->
            assertTrue("titleRes must be non-zero for ${entry.route}", entry.titleRes != 0)
            assertTrue("groupRes must be non-zero for ${entry.route}", entry.groupRes != 0)
            entry.descriptionRes?.let {
                assertTrue("descriptionRes must be non-zero for ${entry.route}", it != 0)
            }
        }
    }

    @Test
    fun `developer row only present when the flag is set`() {
        val withoutDeveloper = settingsSearchIndex(developerMode = false)
        val withDeveloper = settingsSearchIndex(developerMode = true)

        assertFalse(withoutDeveloper.any { it.route == Screen.Developer })
        assertEquals(BuildConfig.DEBUG, withDeveloper.any { it.route == Screen.Developer })
        assertEquals(withoutDeveloper.size + if (BuildConfig.DEBUG) 1 else 0, withDeveloper.size)
    }

    @Test
    fun `every row belongs to a visible hub group`() {
        val groups = setOf(
            R.string.setting_page_group_appearance,
            R.string.setting_page_group_models,
            R.string.setting_page_group_assistant,
            R.string.zeroagent_settings_workspace,
            R.string.setting_page_group_device,
            R.string.setting_page_group_data,
        )
        settingsSearchIndex(developerMode = true).forEach { entry ->
            assertTrue("unexpected group for ${entry.route}", groups.contains(entry.groupRes))
        }
        assertEquals(groups, settingsSearchIndex(developerMode = false).map { it.groupRes }.toSet())
    }

    @Test
    fun `search-only aliases are non-zero`() {
        settingsSearchIndex(developerMode = false).forEach { entry ->
            entry.altTitles.forEach { alias ->
                assertTrue("alias must be non-zero for ${entry.route}", alias != 0)
            }
        }
    }

    @Test
    fun `the theme page answers to the colour-mode alias`() {
        val entry = settingsSearchIndex(developerMode = false)
            .single { it.route == Screen.SettingPreferencesTheme }
        assertTrue(entry.altTitles.contains(R.string.setting_page_color_mode))
    }

    @Test
    fun `tasks workflows and schedules are found together in the workspace group`() {
        val entries = settingsSearchIndex(developerMode = false)
        listOf(Screen.TaskCenter, Screen.SettingWorkflows, Screen.SettingScheduledJobs).forEach { route ->
            val entry = entries.single { it.route == route }
            assertEquals(R.string.zeroagent_settings_workspace, entry.groupRes)
        }
        assertEquals(R.string.zeroagent6_tasks, entries.single { it.route == Screen.TaskCenter }.titleRes)
    }

    @Test
    fun `connections and diagnostics retain their destinations in the consolidated groups`() {
        val entries = settingsSearchIndex(developerMode = false)
        listOf(Screen.SettingSsh, Screen.SettingBrowser).forEach { route ->
            assertEquals(R.string.setting_page_group_device, entries.single { it.route == route }.groupRes)
        }
        listOf(Screen.SettingDoctor, Screen.Log, Screen.SettingAbout).forEach { route ->
            assertEquals(R.string.setting_page_group_data, entries.single { it.route == route }.groupRes)
        }
    }

    @Test
    fun `model memory keeps the exact selected model id after regrouping`() {
        val modelId = "6ea47a4b-33db-44fb-a001-c27b72396d7a"
        val entries = settingsSearchIndex(developerMode = false, modelId = modelId)
        val memory = entries.single { it.route is Screen.ModelMemory }

        assertEquals(Screen.ModelMemory(modelId), memory.route)
        assertEquals(R.string.zeroagent11_model_memory, memory.titleRes)
        assertEquals(R.string.setting_page_group_assistant, memory.groupRes)
        assertTrue(memory.altTitles.contains(R.string.memory12_title))
        assertTrue(memory.altTitles.contains(R.string.assistant_page_memory))
        assertEquals(entries.size, entries.map { it.route }.distinct().size)
    }

    @Test
    fun `no memory destination is invented without a selected model`() {
        assertFalse(settingsSearchIndex(developerMode = false).any { it.route is Screen.ModelMemory })
    }
}
