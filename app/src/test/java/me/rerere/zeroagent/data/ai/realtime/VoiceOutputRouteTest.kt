package me.rerere.zeroagent.data.ai.realtime
import android.media.AudioDeviceInfo
import org.junit.Assert.*
import org.junit.Test
class VoiceOutputRouteTest {
    private val speaker = VoiceOutputRoute(1, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
    private val earpiece = VoiceOutputRoute(2, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
    private val headphones = VoiceOutputRoute(3, AudioDeviceInfo.TYPE_WIRED_HEADSET)
    @Test fun `preserve connected headphones instead of exposing private speech on speaker`() {
        assertEquals(3, defaultVoiceOutputRoute(listOf(speaker, earpiece, headphones), 3))
        assertEquals(3, defaultVoiceOutputRoute(listOf(speaker, headphones), 1))
    }
    @Test fun `speaker defaults without inventing an earpiece on tablets`() {
        assertEquals(1, defaultVoiceOutputRoute(listOf(speaker), null))
        assertEquals(1, defaultVoiceOutputRoute(listOf(earpiece, speaker), 2))
        assertNull(defaultVoiceOutputRoute(emptyList(), null))
    }
}
