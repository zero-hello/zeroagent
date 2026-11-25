package me.rerere.zeroagent.ui.pages.setting

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import coil3.SingletonImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.locallm.ModelInstall
import me.rerere.zeroagent.data.files.FileFolders
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

private data class StorageBreakdown(val models: Long, val partial: Long, val attachments: Long, val cache: Long, val free: Long)

private suspend fun inspectStorage(context: Context): StorageBreakdown = withContext(Dispatchers.IO) {
    val modelRoot = ModelInstall.localModelsDir(context)
    StorageBreakdown(
        partial = partialDownloads(modelRoot).sumOf { it.length() },
        models = directoryBytes(modelRoot),
        attachments = listOf(FileFolders.UPLOAD, FileFolders.IMAGES, FileFolders.VIDEOS)
            .sumOf { directoryBytes(context.filesDir.resolve(it)) },
        cache = directoryBytes(context.cacheDir),
        free = context.filesDir.usableSpace,
    )
}

private fun directoryBytes(root: File): Long {
    if (!Files.isDirectory(root.toPath(), NOFOLLOW_LINKS)) return 0L
    return Files.walk(root.toPath()).use { entries ->
        entries.filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }
            .mapToLong { path -> runCatching { Files.size(path) }.getOrDefault(0L) }.sum()
    }
}

private fun partialDownloads(root: File): List<File> = listOf("litert", "llamacpp")
    .map(root::resolve)
    .filter { Files.isDirectory(it.toPath(), NOFOLLOW_LINKS) }
    .flatMap { folder -> folder.listFiles().orEmpty().filter { file ->
        file.name.endsWith(".partial") && Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)
    } }

@Composable
internal fun StorageOverview(onDismiss: () -> Unit, onManageModels: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var clearing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmPartial by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val usage by produceState<StorageBreakdown?>(null, context, refresh) {
        value = try {
            inspectStorage(context)
        } catch (e: CancellationException) {
            throw e
        } catch (_: java.io.IOException) {
            result = "無法讀取儲存空間。"
            null
        } catch (_: java.io.UncheckedIOException) {
            result = "無法讀取儲存空間。"
            null
        }
    }
    ZeroAgentAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("儲存空間總覽") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val breakdown = usage
                if (breakdown == null && result == null) Text("正在計算…") else if (breakdown != null) {
                    Text("可用空間　${Formatter.formatFileSize(context, breakdown.free)}")
                    Text("本機模型與相關檔案　${Formatter.formatFileSize(context, (breakdown.models - breakdown.partial).coerceAtLeast(0L))}")
                    Text("模型下載暫存　${Formatter.formatFileSize(context, breakdown.partial)}")
                    Text("聊天附件　${Formatter.formatFileSize(context, breakdown.attachments)}")
                    Text("App 快取　${Formatter.formatFileSize(context, breakdown.cache)}")

                }
                TextButton(onClick = { confirmClear = true }, enabled = !clearing) {
                    Text(if (clearing) "正在清理…" else "清除圖片快取")
                }
                TextButton(onClick = { confirmPartial = true }, enabled = !clearing && (breakdown?.partial ?: 0L) > 0) {
                    Text("清除模型下載暫存")
                }
                result?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
        dismissButton = { TextButton(onClick = onManageModels) { Text("管理模型") } },
    )
    if (confirmPartial) ZeroAgentAlertDialog(
        onDismissRequest = { confirmPartial = false },
        title = { Text("清除模型下載暫存？") },
        text = { Text("清除後，尚未下載完的模型需要重新下載。下載中的檔案與已安裝模型會保留。") },
        dismissButton = { TextButton(onClick = { confirmPartial = false }) { Text("取消") } },
        confirmButton = {
            TextButton(onClick = {
                confirmPartial = false
                clearing = true
                result = null
                scope.launch {
                    try {
                        val outcomes = withContext(Dispatchers.IO) {
                            partialDownloads(ModelInstall.localModelsDir(context)).map { partial ->
                                val target = partial.resolveSibling(partial.name.removeSuffix(".partial"))
                                runCatching { ModelInstall.discardPartialDownload(target) }.getOrDefault(false)
                            }
                        }
                        val cleared = outcomes.count { it }
                        val remaining = outcomes.size - cleared
                        result = if (remaining == 0) "已清除 $cleared 份模型下載暫存。"
                        else "已清除 $cleared 份。另有 $remaining 份正在下載或無法清除。"
                        refresh++
                    } finally {
                        clearing = false
                    }
                }
            }) { Text("清除") }
        },
    )
    if (confirmClear) {
        ZeroAgentAlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除圖片快取？") },
            text = { Text("清除網路圖片的暫存副本，之後開啟時會重新載入。對話、附件和本機模型都會保留。") },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    clearing = true
                    result = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val loader = SingletonImageLoader.get(context.applicationContext)
                                loader.diskCache?.clear()
                                loader.memoryCache?.clear()
                            }
                            result = "已清除圖片快取。"
                            refresh++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: java.io.IOException) {
                            result = "無法清除圖片快取，請稍後再試。"
                        } finally {
                            clearing = false
                        }
                    }
                }) { Text("清除") }
            },
        )
    }
}