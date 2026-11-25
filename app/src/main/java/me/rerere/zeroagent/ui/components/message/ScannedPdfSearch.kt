package me.rerere.zeroagent.ui.components.message

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import java.io.File

private data class PdfMatch(val page: Int, val text: String, val rectangle: Rect)
private const val RENDER_LIMIT = 1600

private fun PdfRenderer.Page.renderForSearch(): Bitmap {
    val ratio = RENDER_LIMIT.toFloat() / maxOf(width, height).coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap((width * ratio).toInt().coerceAtLeast(1), (height * ratio).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.WHITE)
    render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    return bitmap
}

private fun ownedPdf(context: Context, document: UIMessagePart.Document): File {
    val uri = document.url.toUri()
    check(uri.scheme == "file")
    val file = File(requireNotNull(uri.path)).canonicalFile
    check(file.toPath().startsWith(context.filesDir.canonicalFile.toPath()) && file.isFile)
    return file
}

private suspend fun searchPdf(file: File, query: String, progress: suspend (Int, Int) -> Unit): List<PdfMatch> =
    withContext(Dispatchers.IO) {
        val found = mutableListOf<PdfMatch>()
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()).use { recognizer ->
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { pdf ->
                    for (index in 0 until pdf.pageCount) {
                        ensureActive()
                        pdf.openPage(index).use { page ->
                            val bitmap = page.renderForSearch()
                            try {
                                val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                                for (line in result.textBlocks.flatMap { it.lines }) {
                                    val rectangle = line.boundingBox ?: continue
                                    if (line.text.contains(query, ignoreCase = true) && found.size < 200) {
                                        found += PdfMatch(index, line.text, Rect(rectangle))
                                    }
                                }
                            } finally { bitmap.recycle() }
                        }
                        progress(index + 1, pdf.pageCount)
                    }
                }
            }
        }
        found
    }

@Composable
internal fun ScannedPdfSearchButton(document: UIMessagePart.Document) {
    val context = LocalContext.current
    var open by remember(document.url) { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("搜尋掃描文字") }
    if (!open) return
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var result by remember { mutableStateOf<List<PdfMatch>?>(null) }
    var preview by remember { mutableStateOf<Pair<PdfMatch, Bitmap>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val close = { job?.cancel(); open = false }
    ZeroAgentAlertDialog(
        onDismissRequest = close,
        title = { Text("搜尋掃描 PDF") },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it.take(128) }, label = { Text("關鍵字") }, singleLine = true,
                    enabled = job == null)
                if (job != null) {
                    val counts = progress
                    if (counts == null || counts.second == 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else {
                        LinearProgressIndicator(progress = { counts.first.toFloat() / counts.second }, modifier = Modifier.fillMaxWidth())
                        Text("已辨識 ${counts.first}／${counts.second} 頁")
                    }
                }
                error?.let { Text(it) }
                result?.let { matches ->
                    if (matches.isEmpty()) Text("沒有找到相符的文字。掃描不清楚時可能會漏掉。")
                    if (matches.size == 200) Text("只顯示前 200 個結果，可用更精確的關鍵字。")
                    matches.forEach { hit ->
                        TextButton(enabled = job == null, onClick = {
                            job = scope.launch {
                                try {
                                    val page = withContext(Dispatchers.IO) {
                                        ParcelFileDescriptor.open(ownedPdf(context, document), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                                            PdfRenderer(descriptor).use { pdf -> pdf.openPage(hit.page).use { it.renderForSearch() } }
                                        }
                                    }
                                    preview = hit to page
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { error = "無法開啟這一頁。" }
                                finally { job = null }
                            }
                        }) { Text("第 ${hit.page + 1} 頁 · ${hit.text}") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = job != null || query.isNotBlank(), onClick = {
                if (job != null) { job?.cancel(); return@TextButton }
                val keyword = query.trim()
                result = null
                preview = null
                error = null
                progress = null
                job = scope.launch {
                    try {
                        result = searchPdf(ownedPdf(context, document), keyword) { count, total ->
                            withContext(Dispatchers.Main) { progress = count to total }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "無法辨識這份 PDF。請確認檔案仍在，且可以開啟。" }
                    finally { job = null }
                }
            }) { Text(if (job == null) "搜尋" else "停止") }
        },
        dismissButton = { TextButton(onClick = close) { Text("關閉") } },
    )
    preview?.let { (hit, bitmap) ->
        ZeroAgentAlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("第 ${hit.page + 1} 頁") },
            text = {
                Box(Modifier.fillMaxWidth()) {
                    Image(bitmap.asImageBitmap(), hit.text, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    Canvas(Modifier.matchParentSize()) {
                        val sx = size.width / bitmap.width
                        val sy = size.height / bitmap.height
                        val r = hit.rectangle
                        drawRect(androidx.compose.ui.graphics.Color.Yellow.copy(alpha = .38f),
                            Offset(r.left * sx, r.top * sy), Size(r.width() * sx, r.height() * sy))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("關閉") } },
        )
    }
}