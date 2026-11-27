package me.rerere.zeroagent.ui.designsystem

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.zeroagent.ui.theme.ZeroAgentFontFamily
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private const val InterSha256 = "29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031"
private const val MaxFontBytes = 16L * 1024L * 1024L
private const val InterSource = "https://raw.githubusercontent.com/google/fonts/main/ofl/inter/Inter%5Bopsz%2Cwght%5D.ttf"

@Stable
internal class ZeroAppearance private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("zero_ui_appearance", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "fonts")
    private val changes = Mutex()
    var soundEnabled by mutableStateOf(prefs.getBoolean("sound", true))
        private set
    var selectedId by mutableStateOf(prefs.getString("font", "system") ?: "system")
        private set
    var fontFamily by mutableStateOf(if (selectedId == "manrope") ZeroAgentFontFamily else FontFamily.SansSerif)
        private set
    var cachedFonts by mutableStateOf<Set<String>>(emptySet())
        private set

    fun updateSoundPreference(enabled: Boolean) {
        soundEnabled = enabled
        prefs.edit().putBoolean("sound", enabled).apply()
    }

    suspend fun restore() = changes.withLock {
        val id = selectedId
        val (available, family) = withContext(Dispatchers.IO) {
            cachedOnDisk() to runCatching { load(id) }.getOrDefault(FontFamily.SansSerif)
        }
        cachedFonts = available
        fontFamily = family
    }

    suspend fun select(id: String) = changes.withLock {
        val family = withContext(Dispatchers.IO) { load(id) }
        applySelection(id, family)
    }

    suspend fun installLocal(uri: Uri, progress: (Float?) -> Unit) {
        install("local", progress) { target ->
            context.contentResolver.openInputStream(uri)?.use { copyLimited(it, target, null, progress) }
                ?: error("Unable to open font")
        }
    }

    suspend fun installInter(progress: (Float?) -> Unit) {
        install("inter", progress) { target ->
            val connection = URL(InterSource).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                check(connection.responseCode == 200) { "Font download failed" }
                val size = connection.contentLengthLong.takeIf { it > 0L }
                check(size == null || size <= MaxFontBytes) { "Font is too large" }
                connection.inputStream.use { copyLimited(it, target, size, progress) }
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                target.inputStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                val expected = InterSha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                check(java.security.MessageDigest.isEqual(expected, digest.digest())) { "Font checksum mismatch" }
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun clearCache() = changes.withLock {
        withContext(Dispatchers.IO) {
            listOf("inter", "local").forEach { id ->
                val file = fileFor(id)
                check(!file.exists() || file.delete()) { "Unable to clear fonts" }
            }
        }
        cachedFonts = emptySet()
        if (selectedId == "inter" || selectedId == "local") applySelection("system", FontFamily.SansSerif)
    }

    private suspend fun install(id: String, progress: (Float?) -> Unit, copy: (File) -> Unit) = changes.withLock {
        progress(null)
        val (available, family) = withContext(Dispatchers.IO) {
            check(directory.isDirectory || directory.mkdirs()) { "Unable to create font cache" }
            val temp = File.createTempFile("font_", ".tmp", directory)
            try {
                copy(temp)
                readTypeface(temp)
                android.system.Os.rename(temp.absolutePath, fileFor(id).absolutePath)
                cachedOnDisk() to load(id)
            } finally {
                temp.delete()
            }
        }
        cachedFonts = available
        applySelection(id, family)
    }

    private fun applySelection(id: String, family: FontFamily) {
        prefs.edit().putString("font", id).apply()
        selectedId = id
        fontFamily = family
    }

    private fun cachedOnDisk() = setOf("inter", "local").filter { fileFor(it).isFile }.toSet()
    private fun fileFor(id: String) = File(directory, "$id.ttf")

    private fun load(id: String): FontFamily = when (id) {
        "system" -> FontFamily.SansSerif
        "manrope" -> ZeroAgentFontFamily
        "inter", "local" -> FontFamily(readTypeface(fileFor(id)))
        else -> error("Unknown font")
    }

    private fun readTypeface(file: File): Typeface {
        check(file.isFile && file.length() in 4L..MaxFontBytes) { "Invalid font file" }
        val signature = ByteArray(4)
        val bytes = file.inputStream().use { it.read(signature) }
        val supported = signature.contentEquals(byteArrayOf(0, 1, 0, 0)) || signature.contentEquals("OTTO".toByteArray())
        check(bytes == signature.size && supported) { "Expected a TTF or OTF font" }
        return Typeface.Builder(file).build() ?: error("Font could not be read")
    }

    private fun copyLimited(input: InputStream, target: File, size: Long?, progress: (Float?) -> Unit) {
        target.outputStream().use { output ->
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val length = input.read(buffer)
                if (length < 0) break
                total += length
                check(total <= MaxFontBytes) { "Font is too large" }
                output.write(buffer, 0, length)
                if (size != null) progress((total.toFloat() / size).coerceIn(0f, 1f))
            }
            check(total > 0L && (size == null || total == size)) { "Incomplete font" }
        }
    }

    companion object {
        private var instance: ZeroAppearance? = null
        @Synchronized fun get(context: Context): ZeroAppearance = instance ?: ZeroAppearance(context.applicationContext)
            .also { instance = it }
    }
}

@Composable
internal fun rememberZeroAppearance(): ZeroAppearance {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { ZeroAppearance.get(context) }
    androidx.compose.runtime.LaunchedEffect(preferences) { preferences.restore() }
    return preferences
}
