package me.rerere.zeroagent.ui.pages.setting

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import me.rerere.zeroagent.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizedResourcesTest {
    @Test
    fun permissionReviewCopyIsLocalizedAndModelNamesRemainIntact() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        })
        val model = "Example Model / 123"
        for (tag in listOf("en", "zh-TW", "zh-CN", "ja", "ko", "ru", "ar")) {
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            })
            assertTrue(localized.getString(R.string.access_review_model, model).contains(model))
            for (resource in listOf(R.string.access_choose_title, R.string.access_assisted_description,
                R.string.access_review_detail, R.string.access_review_allowed, R.string.access_review_ask,
                R.string.access_review_unavailable, R.string.access_review_changed, R.string.access_manual_required)) {
                val text = localized.getString(resource)
                assertTrue("$tag has permission copy", text.isNotBlank())
                if (tag != "en") assertFalse("$tag must not fall back to English", text == english.getString(resource))
            }
        }
    }

    @Test
    fun diagnosticsAreLocalizedAndFormattedForEverySupportedLanguage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (tag in listOf("en", "zh-TW", "zh-CN", "ja", "ko", "ru", "ar")) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
            val localized = context.createConfigurationContext(configuration)
            assertFalse("$tag advertises a retired feature", localized.getString(
                R.string.setting_page_notifications_desc).contains("Telegram", ignoreCase = true))
            val permissions = localized.getString(R.string.doctor_cat_permissions)
            if (tag != "en") assertFalse("$tag fell back to English", permissions == "Permissions")
            val proxy = localized.getString(R.string.doctor_detail_tg_proxy_configured, "HTTP", "localhost", "8080")
            assertTrue("$tag proxy lost its arguments", proxy.contains("localhost:8080"))
            val build = localized.getString(R.string.doctor_detail_app_build, "1.0.9", "10", "true")
            assertTrue("$tag build lost its version", build.contains("1.0.9"))
            assertTrue("$tag build lost its debug flag", build.contains("true"))
            assertTrue(localized.getString(R.string.setting_provider_page_discount_badge).contains("10"))
        }
    }

    @Test
    fun traditionalChineseDoesNotDependOnSimplifiedScriptFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("zh-TW"))
        })
        org.junit.Assert.assertEquals("附件", localized.getString(R.string.quick_chat_segment_attachment))
        org.junit.Assert.assertEquals("語音對話", localized.getString(R.string.quick_chat_voice))
        org.junit.Assert.assertEquals("資料夾名稱", localized.getString(R.string.workspace_cwd_new_folder_hint))
    }
}
