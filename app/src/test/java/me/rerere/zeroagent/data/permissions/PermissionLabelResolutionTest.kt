package me.rerere.zeroagent.data.permissions

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionLabelResolutionTest {
    @Test fun curatedAppTranslationWins() {
        assertEquals("日曆", PermissionLabelResolution.label("日曆", "Calendar", "其他權限"))
    }
    @Test fun platformTextIsTrimmed() {
        assertEquals("日曆", PermissionLabelResolution.label(" ", " 日曆 ", "其他權限"))
    }
    @Test fun rawOrMissingPlatformNamesUseLocalizedFallback() {
        listOf(null, " ", "android.permission.READ_CALENDAR", "com.example.permission").forEach {
            assertEquals("其他權限", PermissionLabelResolution.label(null, it, "其他權限"))
        }
    }
    @Test fun descriptionPrefersAppThenPlatformThenLocalizedFallback() {
        assertEquals("App", PermissionLabelResolution.description("App", "系統", "說明"))
        assertEquals("系統", PermissionLabelResolution.description(null, " 系統 ", "說明"))
        assertEquals("說明", PermissionLabelResolution.description(" ", null, "說明"))
    }
}
