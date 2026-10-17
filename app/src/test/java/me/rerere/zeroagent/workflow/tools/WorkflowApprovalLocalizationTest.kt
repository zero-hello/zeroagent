package me.rerere.zeroagent.workflow.tools

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import me.rerere.zeroagent.workflow.model.ConditionSpec
import me.rerere.zeroagent.workflow.model.TriggerSpec
import org.junit.Assert.*
import org.junit.Test

class WorkflowApprovalLocalizationTest {
    private val locales = mapOf("en" to "values", "zh-TW" to "values-zh-rTW", "zh" to "values-zh",
        "ja" to "values-ja", "ko" to "values-ko", "ru" to "values-ru", "ar" to "values-ar")

    private fun resources(tag: String): Map<String, String> {
        val base = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(base, "${locales.getValue(tag)}/zeroagent_language_v11.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val element = nodes.item(index) as org.w3c.dom.Element
            element.getAttribute("name") to element.textContent.replace("\\'", "'").replace("\\\"", "\"")
        }
    }

    private fun labels(tag: String): WorkflowApprovalLabels {
        val values = resources(tag)
        return WorkflowApprovalLabels { key, arguments ->
            String.format(Locale.forLanguageTag(tag), values.getValue(key.resourceName), *arguments)
        }
    }

    private val args = """{"definition":{"id":"fixture","name":"Create workflow always","trigger":{"type":"wifi_connected","ssid":"Create workflow WiFi"},"conditions":[{"type":"wifi_ssid_is","ssid":"always","invert":true}],"actions":[{"tool":"post_notification","args":{"title":"always","token":"fixture-secret"}}],"cooldown_seconds":30,"max_runs_per_day":5}}"""

    @Test fun defaultEnglishRemainsCompatibleWithTheOriginalApprovalText() {
        assertEquals("""
            Create workflow "Create workflow always"

            When: WiFi connects to Create workflow WiFi
            Only if: NOT (WiFi is always)
            Do:
              1. post_notification(title="always", token="***")

            Cooldown: 30s
            Daily cap: 5 runs
        """.trimIndent(), WorkflowApprovalRenderer.renderPlain("workflow_create", args))
    }

    @Test fun traditionalChineseTranslatesOnlyLabelsAndPreservesUserContentAndRedaction() {
        val rendered = WorkflowApprovalRenderer.renderPlain("workflow_create", args, labels("zh-TW"))
        assertTrue(rendered.startsWith("建立工作流程「Create workflow always」"))
        assertTrue(rendered.contains("觸發時機：連上 Wi-Fi：Create workflow WiFi"))
        assertTrue(rendered.contains("執行條件：不符合（Wi-Fi 為 always）"))
        assertTrue(rendered.contains("post_notification(title=\"always\", token=\"***\")"))
        assertTrue(rendered.contains("冷卻時間：30 秒"))
        assertTrue(rendered.contains("每日上限：5 次"))
        assertFalse(rendered.contains("fixture-secret"))
    }

    @Test fun deleteEnableDisableAndRunNowKeepTheirSemanticsInBothLanguages() {
        assertEquals("Delete workflow id=raw-Create", WorkflowApprovalRenderer.renderPlain("workflow_delete", """{"id":"raw-Create"}"""))
        val zh = labels("zh-TW")
        assertEquals("刪除工作流程 ID=raw-Create", WorkflowApprovalRenderer.renderPlain("workflow_delete", """{"id":"raw-Create"}""", zh))
        assertEquals("啟用工作流程 ID=x", WorkflowApprovalRenderer.renderPlain("workflow_set_enabled", """{"id":"x","enabled":true}""", zh))
        assertEquals("停用工作流程 ID=x", WorkflowApprovalRenderer.renderPlain("workflow_set_enabled", """{"id":"x","enabled":false}""", zh))
        assertEquals("立即執行工作流程 ID=x", WorkflowApprovalRenderer.renderPlain("workflow_run", """{"id":"x"}""", zh))
        assertEquals("Enable workflow id=x", WorkflowApprovalRenderer.renderPlain("workflow_set_enabled", """{"id":"x","enabled":true}"""))
        assertEquals("Run workflow now id=x", WorkflowApprovalRenderer.renderPlain("workflow_run", """{"id":"x"}"""))
    }

    @Test fun unsupportedOrInvalidPayloadsRemainUntouched() {
        val raw = "raw user content: Create workflow always"
        assertEquals(raw, WorkflowApprovalRenderer.renderPlain("other_tool", raw, labels("zh-TW")))
        assertEquals(raw, WorkflowApprovalRenderer.renderPlain("workflow_create", raw, labels("zh-TW")))
    }

    @Test fun allSevenResourceSetsHaveEveryPhraseAndMatchingFormatArguments() {
        val english = resources("en")
        val pattern = Regex("%([0-9]+)\\$([sd])")
        locales.keys.forEach { tag ->
            val translated = resources(tag)
            assertEquals("resource keys: $tag", english.keys, translated.keys)
            english.forEach { (key, value) ->
                fun signature(text: String) = pattern.findAll(text).map { it.groupValues.drop(1) }.toList().sortedBy { it.first() }
                assertEquals("format arguments: $tag/$key", signature(value), signature(translated.getValue(key)))
            }
        }
        WorkflowApprovalText.entries.forEach { assertEquals(it.english, english.getValue(it.resourceName)) }
    }

    @Test fun everyTriggerAndConditionCanRenderUsingTheSevenRealResourceSets() {
        val triggers = listOf(
            TriggerSpec.TimeCron(timeOfDay = "09:00"), TriggerSpec.TimeCron(timeOfDay = "09:00", daysOfWeek = listOf(1, 3)),
            TriggerSpec.TimeCron(cron = "0 9 * * *"), TriggerSpec.TimeCron(),
            TriggerSpec.WifiConnected(), TriggerSpec.WifiConnected("Raw SSID"), TriggerSpec.WifiDisconnected(), TriggerSpec.WifiDisconnected("Raw SSID"),
            TriggerSpec.BluetoothDeviceConnected(), TriggerSpec.BluetoothDeviceConnected("01:02"), TriggerSpec.BluetoothDeviceDisconnected("01:02"),
            TriggerSpec.HeadphonesPlugged, TriggerSpec.HeadphonesUnplugged, TriggerSpec.PowerConnected, TriggerSpec.PowerDisconnected,
            TriggerSpec.BatteryBelow(20), TriggerSpec.BatteryAbove(80), TriggerSpec.GeofenceEnter(25.0, 121.0, 100),
            TriggerSpec.GeofenceExit(25.0, 121.0, 100, "Raw Place"), TriggerSpec.AppLaunched("raw.app"), TriggerSpec.AppClosed("raw.app"),
            TriggerSpec.NotificationReceived(), TriggerSpec.NotificationReceived("raw.app", "Create", "always", "raw.*", "body.*"),
            TriggerSpec.BootCompleted, TriggerSpec.ScreenOn, TriggerSpec.ScreenOff, TriggerSpec.Manual,
        )
        val conditions = listOf(
            ConditionSpec.TimeBetween("22:00", "06:00"), ConditionSpec.TimeAfterSunset(), ConditionSpec.TimeAfterSunset(-15),
            ConditionSpec.TimeBeforeSunrise(10), ConditionSpec.DayOfWeekIn(listOf(1, 3)), ConditionSpec.WifiSsidIs("Raw SSID", invert = true),
            ConditionSpec.WifiSsidIn(listOf("one", "two")), ConditionSpec.BatteryAbove(10), ConditionSpec.BatteryBelow(90),
            ConditionSpec.IsCharging(), ConditionSpec.IsNotCharging(), ConditionSpec.ForegroundAppIs("raw.app"),
            ConditionSpec.ForegroundAppIn(listOf("raw.one", "raw.two")), ConditionSpec.ScreenIsOn(), ConditionSpec.ScreenIsOff(),
        )
        locales.keys.forEach { tag ->
            val labels = labels(tag)
            triggers.forEach { assertTrue(WorkflowApprovalRenderer.triggerSummary(it, labels).isNotBlank()) }
            conditions.forEach { assertTrue(WorkflowApprovalRenderer.conditionSummary(it, labels).isNotBlank()) }
            val rendered = WorkflowApprovalRenderer.renderPlain("workflow_update", args, labels)
            assertTrue(rendered.contains("Create workflow always"))
            assertFalse(rendered.contains("fixture-secret"))
        }
    }
}
