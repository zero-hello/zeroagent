package me.rerere.zeroagent.ui.pages.productivity

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.*
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.datastore.getCurrentChatModel
import me.rerere.zeroagent.service.VisualCaptureService
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import me.rerere.zeroagent.ui.context.LocalSettings
import org.koin.compose.koinInject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Composable
fun VisualAssistPage() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val settings = LocalSettings.current
    val manager: ProviderManager = koinInject()
    val capture by VisualCaptureService.state.collectAsStateWithLifecycle()
    val choices = settings.providers.filter { it.enabled }.flatMap { provider ->
        provider.models.filter { it.type == ModelType.CHAT && Modality.IMAGE in it.inputModalities && Modality.TEXT in it.outputModalities }
            .map { provider to it }
    }
    var selectedId by rememberSaveable { mutableStateOf(settings.getCurrentChatModel()?.id?.toString()) }
    val chosen = choices.firstOrNull { it.second.id.toString() == selectedId }
    var question by rememberSaveable { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var cameraEnabled by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val pageActive = remember { AtomicBoolean(true) }
    val cameraRequest = remember { AtomicInteger(0) }
    val controller = remember { LifecycleCameraController(context) }
    fun ask(file: File) {
        if (!pageActive.get()) { file.delete(); return }
        val selection = chosen ?: run { file.delete(); return }
        val prompt = question.trim()
        busy = true; error = null
        request = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val (provider, model) = selection
                val result = manager.getProviderByType(provider).generateText(provider,
                    listOf(UIMessage(role = MessageRole.USER, parts = listOf(
                        UIMessagePart.Text(prompt), UIMessagePart.Image(url = file.toUri().toString())))),
                    TextGenerationParams(model = model, customHeaders = model.customHeaders, customBody = model.customBodies))
                answer = result.message.toText().ifBlank { "模型沒有傳回文字，請換個問題或檢查供應商的安全阻擋訊息。" }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error = "圖片分析失敗，請確認模型支援圖片、帳戶額度及連線。" }
            finally { withContext(NonCancellable + Dispatchers.IO) { file.delete() }; busy = false }
        }
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (pageActive.get() && result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, VisualCaptureService::class.java)
                    .putExtra("capture_code", result.resultCode).putExtra("capture_data", result.data))
            } catch (_: Exception) { error = "無法啟動畫面協助，請重新授權。" }
        } else error = "未取得螢幕擷取同意，未開始分享。"
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraEnabled = granted
        if (!granted) error = "相機權限未開啟。"
    }
    DisposableEffect(cameraEnabled, owner) {
        if (cameraEnabled) {
            runCatching { controller.bindToLifecycle(owner) }.onFailure { error = "相機無法啟動，請檢查是否被其他 App 使用。" }
        }
        onDispose { controller.unbind() }
    }
    DisposableEffect(Unit) {
        pageActive.set(true)
        onDispose {
            pageActive.set(false)
            cameraRequest.incrementAndGet()
            request?.cancel()
            context.stopService(Intent(context, VisualCaptureService::class.java))
            VisualCaptureService.clearFrame()
        }
    }
    val bitmap = remember(capture.frame?.timestamp) {
        capture.frame?.jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("相機與螢幕協助") }, navigationIcon = { BackButton() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("相機與螢幕會在本機預覽，只有按下「詢問」時才把當下的一張圖片與問題傳給選定供應商。螢幕分享可從系統通知停止；受保護畫面可能無法擷取。")
            Text("這是畫面問答，尚不會自動操作其他 App。", style = MaterialTheme.typography.bodySmall)
            if (choices.isEmpty()) Text("沒有已啟用且標記支援圖片輸入的對話模型，請先到供應商設定加入。")
            choices.forEach { (provider, model) ->
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(selected = selectedId == model.id.toString(), enabled = !busy,
                        onClick = { selectedId = model.id.toString() })
                    Text("${provider.name} · ${model.displayName.ifBlank { model.modelId }}", Modifier.weight(1f).padding(top = 12.dp))
                }
            }
            OutlinedButton(onClick = {
                if (cameraEnabled) cameraEnabled = false
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraEnabled = true
                else cameraPermission.launch(Manifest.permission.CAMERA)
            }) { Text(if (cameraEnabled) "關閉相機" else "開啟即時相機預覽") }
            if (cameraEnabled) AndroidView(factory = { PreviewView(it).apply { this.controller = controller } },
                modifier = Modifier.fillMaxWidth().height(280.dp))
            OutlinedButton(enabled = !capture.active, onClick = {
                cameraEnabled = false; error = null
                val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
                consent.launch(projectionManager.createScreenCaptureIntent())
            }) { Text(if (capture.active) "螢幕擷取中 · 每秒更新" else "開始螢幕分享") }
            if (capture.active) TextButton(onClick = {
                context.startService(Intent(context, VisualCaptureService::class.java).setAction(VisualCaptureService.STOP))
            }) { Text("保留目前畫面並停止") }
            bitmap?.let { Image(it, contentDescription = "目前擷取畫面", modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            capture.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(question, { question = it }, label = { Text("想詢問畫面上的什麼？") },
                enabled = !busy, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(enabled = chosen != null && question.isNotBlank() && !busy && (cameraEnabled || capture.frame != null), onClick = {
                if (cameraEnabled) {
                    busy = true
                    val generation = cameraRequest.incrementAndGet()
                    val file = try { File.createTempFile("camera-assist-", ".jpg", context.cacheDir) }
                        catch (_: Exception) { busy = false; error = "儲存空間不足，無法拍攝。"; return@Button }
                    try {
                        controller.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    if (pageActive.get() && cameraRequest.get() == generation) ask(file) else file.delete()
                                }
                                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                    file.delete()
                                    if (pageActive.get() && cameraRequest.get() == generation) {
                                        busy = false; error = "拍攝失敗，請重試。"
                                    }
                                }
                            })
                    } catch (_: Exception) { file.delete(); busy = false; error = "無法拍攝相機畫面。" }
                } else {
                    capture.frame?.jpeg?.let { bytes ->
                        var file: File? = null
                        try {
                            val imageFile = File.createTempFile("screen-assist-", ".jpg", context.cacheDir)
                            file = imageFile
                            imageFile.writeBytes(bytes); ask(imageFile)
                        } catch (_: Exception) { file?.delete(); busy = false; error = "無法保存暫存畫面，請檢查儲存空間。" }
                    }
                }
            }) { Text(if (busy) "分析中…" else "詢問目前畫面") }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = { cameraRequest.incrementAndGet(); request?.cancel(); busy = false }) { Text("取消分析") }
            }
            if (answer.isNotBlank()) SelectionContainer { Text(answer) }
        }
    }
}
