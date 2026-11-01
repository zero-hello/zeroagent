package me.rerere.zeroagent.data.permissions

import android.Manifest
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionInventoryIntentTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun backgroundLocationUsesAppDetailsInsteadOfRuntimeRequest() {
        val row = PermissionInventory.build(context).single { it.id == Manifest.permission.ACCESS_BACKGROUND_LOCATION }
        assertTrue(row.grant is PermissionInventory.GrantAction.SystemSettings)
        val intent = (row.grant as PermissionInventory.GrantAction.SystemSettings).intent
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
    }

    @Test fun allFilesAndUsageAccessAreNeverMarkedAsAutomaticallyGranted() {
        val rows = PermissionInventory.build(context)
        listOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE, Manifest.permission.PACKAGE_USAGE_STATS).forEach { permission ->
            val row = rows.single { it.id == permission }
            assertEquals(PermissionInventory.Group.SpecialAccess, row.group)
            assertTrue(row.status != PermissionInventory.Status.AUTO_GRANTED)
            assertTrue(row.grant is PermissionInventory.GrantAction.SystemSettings)
        }
    }
    @Test fun platformLabelsUseTheAppsLocaleInsteadOfTheDeviceLocale() {
        val info = context.packageManager.getPermissionInfo(Manifest.permission.READ_CALENDAR, 0)
        fun label(tag: String): String? {
            val config = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag(tag)))
            }
            return PermissionInventory.platformText(context.createConfigurationContext(config), info, info.labelRes)
        }
        val english = label("en")
        val chinese = label("zh-TW")
        assertNotNull(english)
        assertNotNull(chinese)
        assertNotEquals(english, chinese)
        assertTrue(chinese!!.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN })
    }

}
