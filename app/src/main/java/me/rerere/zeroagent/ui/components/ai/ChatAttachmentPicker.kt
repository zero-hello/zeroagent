package me.rerere.zeroagent.ui.components.ai

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.dokar.sonner.ToastType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.appTempFolder
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.files.FilesManager
import me.rerere.zeroagent.data.files.importComposerAttachment
import me.rerere.zeroagent.ui.components.ui.permission.PermissionCamera
import me.rerere.zeroagent.ui.components.ui.permission.PermissionManager
import me.rerere.zeroagent.ui.components.ui.permission.rememberPermissionState
import me.rerere.zeroagent.ui.context.LocalToaster
import me.rerere.zeroagent.ui.hooks.ChatInputState
import me.rerere.zeroagent.utils.ImageUtils
import org.koin.compose.koinInject
import java.io.File
import kotlin.uuid.Uuid

internal data class ChatAttachmentPickerActions(
    val onTakePicture: () -> Unit,
    val onPickImage: () -> Unit,
    val onPickVideo: () -> Unit,
    val onPickAudio: () -> Unit,
    val onPickFile: () -> Unit,
)

@Composable
internal fun rememberChatAttachmentPickerActions(
    inputState: ChatInputState,
    setting: Settings,
    onAttachmentAdded: () -> Unit,
): ChatAttachmentPickerActions {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val filesManager: FilesManager = koinInject()
    val scope = rememberCoroutineScope()
    suspend fun addAttachments(uris: List<Uri>, expectedType: String? = null) {
        val issues = mutableListOf<String>()
        val parts = mutableListOf<UIMessagePart>()
        try {
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    ensureActive()
                    filesManager.importComposerAttachment(uri, expectedType) { issues.add(it) }
                        ?.let(parts::add)
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                val localUris = parts.mapNotNull {
                    when (it) {
                        is UIMessagePart.Image -> it.url.toUri()
                        is UIMessagePart.Audio -> it.url.toUri()
                        is UIMessagePart.Video -> it.url.toUri()
                        is UIMessagePart.Document -> it.url.toUri()
                        else -> null
                    }
                }
                filesManager.deleteChatFiles(localUris)
            }
            throw e
        }
        issues.forEach { toaster.show(it, type = ToastType.Error) }
        if (parts.isNotEmpty()) {
            inputState.messageContent += parts
            onAttachmentAdded()
        }
    }

    fun enqueueAttachments(uris: List<Uri>, expectedType: String? = null) {
        if (uris.isNotEmpty()) scope.launch { addAttachments(uris, expectedType) }
    }

    val cameraPermission = rememberPermissionState(PermissionCamera)
    PermissionManager(permissionState = cameraPermission)

    var cameraOutputUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by rememberSaveable { mutableStateOf<File?>(null) }
    val (_, launchCameraCrop) = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            addAttachments(listOf(croppedUri), "image")
        },
        onCleanup = {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    )
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captureSuccessful ->
        if (captureSuccessful && cameraOutputUri != null) {
            if (setting.displaySetting.skipCropImage) {
                val uri = requireNotNull(cameraOutputUri)
                val file = cameraOutputFile
                cameraOutputFile = null
                cameraOutputUri = null
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        addAttachments(listOf(uri), "image")
                    } finally {
                        file?.delete()
                    }
                }
            } else {
                launchCameraCrop(cameraOutputUri!!)
            }
        } else {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    }
    val onTakePicture: () -> Unit = {
        if (cameraPermission.allRequiredPermissionsGranted) {
            cameraOutputFile = context.cacheDir.resolve("camera_${Uuid.random()}.jpg")
            cameraOutputUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", cameraOutputFile!!
            )
            cameraLauncher.launch(cameraOutputUri!!)
        } else {
            cameraPermission.requestPermissions()
        }
    }

    var preCropTempFile by rememberSaveable { mutableStateOf<File?>(null) }
    var preparingCrop by remember { mutableStateOf(false) }
    val (_, launchImageCrop) = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            addAttachments(listOf(croppedUri), "image")
        },
        onCleanup = {
            preCropTempFile?.delete()
            preCropTempFile = null
        }
    )
    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            val source = selectedUris.singleOrNull()
            if (source == null || setting.displaySetting.skipCropImage) {
                enqueueAttachments(selectedUris, "image")
            } else if (!preparingCrop && preCropTempFile == null) {
                preparingCrop = true
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    val tempFile = File(context.appTempFolder, "pick_temp_${Uuid.random()}.jpg")
                    try {
                        withContext(Dispatchers.IO) { prepareImageForCrop(context, filesManager, source, tempFile) }
                        preCropTempFile = tempFile
                        launchImageCrop(tempFile.toUri())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (preCropTempFile == tempFile) preCropTempFile = null
                        Log.w("ChatAttachmentPicker", "Image preparation failed", e)
                        val message = if (e is CropPreparationException) e.message else null
                        toaster.show(message ?: "無法讀取圖片，請重新選擇", type = ToastType.Error)
                    } finally {
                        if (preCropTempFile != tempFile) tempFile.delete()
                        preparingCrop = false
                    }
                }
            }
        }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            enqueueAttachments(selectedUris, "video")
        }
    val audioPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            enqueueAttachments(selectedUris, "audio")
        }
    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selectedUris ->
            enqueueAttachments(selectedUris)
        }

    return ChatAttachmentPickerActions(
        onTakePicture = onTakePicture,
        onPickImage = { if (!preparingCrop && preCropTempFile == null) imagePickerLauncher.launch("image/*") },
        onPickVideo = { videoPickerLauncher.launch("video/*") },
        onPickAudio = { audioPickerLauncher.launch("audio/*") },
        onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
    )
}

private class CropPreparationException(message: String) : java.io.IOException(message)

private fun prepareImageForCrop(context: android.content.Context, filesManager: FilesManager, source: Uri, target: File) {
    if (filesManager.getFileMimeType(source)?.startsWith("image/") != true) {
        throw CropPreparationException("不支援這個圖片格式")
    }
    val limit = 50L * 1024 * 1024
    fun tooLarge(): Nothing = throw CropPreparationException("圖片超過 50 MB 上限")
    val input = context.contentResolver.openInputStream(source) ?: error("無法讀取圖片")
    input.use {
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                total += count
                if (total > limit) tooLarge()
                output.write(buffer, 0, count)
            }
        }
    }
    if (target.length() == 0L) throw CropPreparationException("圖片是空的")
    // Decode the bounded local copy, so HEIF preparation reads the same input that was checked.
    val localSource = target.toUri()
    if (ImageUtils.isHeifImage(context, localSource)) {
        val converted = File(target.parentFile, "${target.nameWithoutExtension}_converted.jpg")
        try {
            if (ImageUtils.convertHeifToJpeg(context, localSource, converted)) {
                if (converted.length() > limit) tooLarge()
                converted.copyTo(target, overwrite = true)
            }
        } finally {
            converted.delete()
        }
    }
}
