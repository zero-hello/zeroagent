package me.rerere.zeroagent.ui.pages.setting.components

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ModelParameterNumberTest {
    @Test fun `localized decimal keyboards can tune model parameters`() {
        assertEquals(.5, parseModelParameterNumber("0,5", Locale.forLanguageTag("ru"))!!, .00001)
        assertEquals(.5, parseModelParameterNumber("٠٫٥", Locale.forLanguageTag("ar"))!!, .00001)
        assertEquals(.5, parseModelParameterNumber("0.5", Locale.forLanguageTag("ru"))!!, .00001)
    }
    @Test fun `invalid partial grouped and nonfinite values are not committed`() {
        assertNull(parseModelParameterNumber("1,234", Locale.US))
        assertNull(parseModelParameterNumber("0.7garbage", Locale.US))
        assertNull(parseModelParameterNumber("NaN", Locale.US))
        assertNull(parseModelParameterNumber("Infinity", Locale.US))
    }
    @Test fun `display uses the app decimal separator without grouping`() {
        assertEquals("0,5", formatModelParameterNumber("0.5", Locale.forLanguageTag("ru")))
        assertEquals("1024", formatModelParameterNumber("1024", Locale.US))
    }
}
