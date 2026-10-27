package me.rerere.zeroagent.utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class ZeroAgentLanguagePolicyTest {
    @Test fun defaultAndUnknownSelectionFollowSystem() {
        assertEquals("", ZeroAgentLanguagePolicy.normalize(""))
        assertEquals("", ZeroAgentLanguagePolicy.normalize("unknown"))
    }
    @Test fun allPackagedLanguageChoicesRoundTrip() {
        for (tag in ZeroAgentLanguagePolicy.supportedTags) assertEquals(tag, ZeroAgentLanguagePolicy.normalize(tag))
        assertEquals("zh-TW", ZeroAgentLanguagePolicy.normalize("ZH-tw"))
        assertTrue(ZeroAgentLanguagePolicy.supportedTags.contains("ar"))
    }
    @Test fun platformScriptRegionListsSelectThePackagedLanguage() {
        assertEquals("zh-TW", ZeroAgentLanguagePolicy.normalize("zh-Hant-HK"))
        assertEquals("zh-CN", ZeroAgentLanguagePolicy.normalize("zh-Hans-CN"))
        assertEquals("en", ZeroAgentLanguagePolicy.normalize("en-GB"))
        assertEquals("ja", ZeroAgentLanguagePolicy.normalize("xx, ja-JP"))
        assertEquals("ko", ZeroAgentLanguagePolicy.normalize("ko_KR"))
    }
}
