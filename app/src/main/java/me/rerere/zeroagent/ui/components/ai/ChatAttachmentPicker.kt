package me.rerere.zeroagent.ui.components.ai

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.dokar.sonner.ToastType
import me.rerere.common.android.appTempFolder
import me.rerere.zeroagent.R
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
    fun addAttachments(uris: List<Uri>, expectedType: String? = null) {
        val parts = uris.mapNotNull { uri ->
            filesManager.importComposerAttachment(uri, expectedType) { reason ->
                toaster.show(reason, type = ToastType.Error)
            }
        }
        if (parts.isNotEmpty()) {
            inputState.messageContent += parts
            onAttachmentAdded()
        }
    }

    val cameraPermission = rememberPermissionState(PermissionCamera)
    PermissionManager(permissionState = cameraPermission)

    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
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
                addAttachments(listOf(cameraOutputUri!!), "image")
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
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

    var preCropTempFile by remember { mutableStateOf<File?>(null) }
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
            if (selectedUris.isNotEmpty()) {
                Log.d("ImagePickButton", "Selected URIs: $selectedUris")
                if (setting.displaySetting.skipCropImage) {
                    addAttachments(selectedUris, "image")
                } else if (selectedUris.size == 1) {
                    val tempFile = File(context.appTempFolder, "pick_temp_${System.currentTimeMillis()}.jpg")
                    val source = selectedUris.first()
                    if (runCatching { filesManager.getFileMimeType(source)?.startsWith("image/") }.getOrNull() != true) {
                        toaster.show("不支援這個圖片格式", type = ToastType.Error)
                        return@rememberLauncherForActivityResult
                    }
                    val limitBytes = 50L * 1024 * 1024
                    if ((filesManager.getFileSize(source) ?: 0L) > limitBytes) {
                        toaster.show("圖片超過 50 MB 上限", type = ToastType.Error)
                        return@rememberLauncherForActivityResult
                    }
                    runCatching {
                        // HEIF/HEIC（尤其 HDR HEIF）交给 UCrop 前先解码转为 JPEG，规避裁剪解码失败
                        val converted = ImageUtils.isHeifImage(context, source) &&
                            ImageUtils.convertHeifToJpeg(context, source, tempFile)
                        if (!converted) {
                            val sourceStream = context.contentResolver.openInputStream(source) ?: error("Unreadable image")
                            sourceStream.use { input ->
                                tempFile.outputStream().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var total = 0L
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        check(total <= limitBytes) { "圖片超過 50 MB 上限" }
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                        }
                        check(tempFile.isFile && tempFile.length() > 0L) { "空白圖片" }
                        preCropTempFile = tempFile
                        launchImageCrop(tempFile.toUri())
                    }.onFailure {
                        Log.e("ImagePickButton", "Failed to prepare cropped image", it)
                        tempFile.delete()
                        toaster.show(it.message?.takeIf { message -> message == "圖片超過 50 MB 上限" } ?: "無法讀取圖片，請重新選擇", type = ToastType.Error)
                    }
                } else {
                    addAttachments(selectedUris, "image")
                }
            } else {
                Log.d("ImagePickButton", "No images selected")
            }
        }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            addAttachments(selectedUris, "video")
        }
    val audioPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            addAttachments(selectedUris, "audio")
        }
    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selectedUris ->
            addAttachments(selectedUris)
        }

    return ChatAttachmentPickerActions(
        onTakePicture = onTakePicture,
        onPickImage = { imagePickerLauncher.launch("image/*") },
        onPickVideo = { videoPickerLauncher.launch("video/*") },
        onPickAudio = { audioPickerLauncher.launch("audio/*") },
        onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
    )
}
