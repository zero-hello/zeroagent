package me.rerere.zeroagent.ui.components.ai

import me.rerere.zeroagent.utils.localizedErrorMessage
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toFile
import com.dokar.sonner.ToastType
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.rerere.common.android.Logging
import me.rerere.common.android.appTempFolder
import me.rerere.zeroagent.ui.context.LocalToaster
import java.io.File

@Composable
internal fun useCropLauncher(
    onCroppedImageReady: suspend (Uri) -> Unit,
    onCleanup: (() -> Unit)? = null,
    aspectRatio: Pair<Float, Float>? = null,
    freeStyleCropEnabled: Boolean = true
): Pair<ActivityResultLauncher<Intent>, (Uri) -> Unit> {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var cropOutputUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val cropActivityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val output = cropOutputUri
        cropOutputUri = null
        when (result.resultCode) {
            android.app.Activity.RESULT_OK -> {
                if (output != null) {
                    // The receiver may copy on Dispatchers.IO. Keep its source alive until it returns.
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            onCroppedImageReady(output)
                        } finally {
                            output.toFile().delete()
                        }
                    }
                }
            }

            UCrop.RESULT_ERROR -> {
                val error = result.data?.let { UCrop.getError(it) }
                Logging.log(
                    "CropLauncher",
                    "crop failed: ${error?.message} | ${error?.stackTraceToString()}"
                )
                toaster.show(
                    context.localizedErrorMessage(error),
                    type = ToastType.Error
                )
            }
        }
        if (result.resultCode != android.app.Activity.RESULT_OK) output?.toFile()?.delete()
        onCleanup?.invoke()
    }

    val launchCrop: (Uri) -> Unit = { sourceUri ->
        val outputFile = File(context.appTempFolder, "crop_output_${System.currentTimeMillis()}.jpg")
        cropOutputUri = Uri.fromFile(outputFile)

        var crop = UCrop.of(sourceUri, cropOutputUri!!).withOptions(UCrop.Options().apply {
            setFreeStyleCropEnabled(freeStyleCropEnabled)
            setAllowedGestures(
                UCropActivity.SCALE, UCropActivity.ROTATE, UCropActivity.NONE
            )
            setCompressionFormat(Bitmap.CompressFormat.PNG)
        }).withMaxResultSize(4096, 4096)
        aspectRatio?.let { (x, y) ->
            crop = crop.withAspectRatio(x, y)
        }
        val cropIntent = crop.getIntent(context)

        cropActivityLauncher.launch(cropIntent)
    }

    return Pair(cropActivityLauncher, launchCrop)
}
