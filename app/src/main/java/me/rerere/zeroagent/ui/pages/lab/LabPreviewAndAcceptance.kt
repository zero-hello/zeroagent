package me.rerere.zeroagent.ui.pages.lab

import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.findModelById
import me.rerere.zeroagent.data.datastore.findProvider
import me.rerere.zeroagent.data.lab.AcceptanceRow
import me.rerere.zeroagent.data.lab.LabArtifact
import me.rerere.zeroagent.data.lab.artifactEvidenceDigest
import me.rerere.zeroagent.data.lab.LabPreferences
import me.rerere.zeroagent.data.lab.PreviewAsset
import me.rerere.zeroagent.data.lab.fetchApprovedPreviewAsset
import me.rerere.zeroagent.data.lab.isolatePreview
import me.rerere.zeroagent.data.lab.previewAssetUrl
import okhttp3.HttpUrl
import me.rerere.zeroagent.ui.pages.chat.TemporaryChatDialog
import org.koin.compose.koinInject
import java.io.ByteArrayInputStream

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PreviewWorkspace() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var html by remember { mutableStateOf("") }
    var css by remember { mutableStateOf("") }
    var js by remember { mutableStateOf("") }
    var sources by remember { mutableStateOf("") }
    var pendingRun by remember { mutableStateOf<PreviewRun?>(null) }
    var downloading by remember { mutableStateOf<Job?>(null) }
    var widthMode by rememberSaveable { mutableStateOf("full") }
    var notice by remember { mutableStateOf("") }
    var document by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    val console = remember { mutableStateListOf<String>() }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        val value = document
        if (uri != null && value != null) scope.launch {
            notice = if (writeLabText(context, uri, value)) "已匯出這次執行的版本。" else "無法寫入檔案。"
        }
    }
    fun showPreview(run: PreviewRun, assets: List<PreviewAsset> = emptyList()) {
        document = isolatePreview(run.html, run.css, run.js, assets)
        generation++
        console.clear()
        notice = if (assets.isEmpty()) "" else "已載入 ${assets.size} 張圖片。"
    }
    fun tryPreview() {
        notice = ""
        val entries = sources.lines().map(String::trim).filter(String::isNotEmpty)
        if (entries.size > 3) { notice = "最多可載入三張圖片。"; return }
        val urls = entries.map { url -> previewAssetUrl(url) ?: run {
            notice = "請輸入公開的 HTTPS 圖片網址，每行一個。網址須使用標準連接埠且不可含登入資訊、查詢參數或片段。"
            return
        } }
        val joined = html + css + js
        if (urls.indices.any { "{{asset${it + 1}}}" !in joined }) {
            notice = "請在程式中引用每張圖片，例如 {{asset1}}、{{asset2}}。"
            return
        }
        val next = PreviewRun(html, css, js, urls)
        if (urls.isNotEmpty()) pendingRun = next
        else try { showPreview(next) } catch (failure: IllegalArgumentException) { notice = failure.message.orEmpty() }
    }
    Text("支援單頁 HTML、CSS 和原生 JavaScript。需要伺服器、編譯工具或外部套件的專案無法在這裡執行。")
    WorkspaceField(html, { html = it }, "HTML", 4, cap = 65_000)
    WorkspaceField(css, { css = it }, "CSS", 3, cap = 40_000)
    WorkspaceField(js, { js = it }, "JavaScript", 3, cap = 65_000)
    WorkspaceField(sources, { sources = it }, "公開圖片網址（選填，每行一個）", 2, cap = 4000)
    Text("最多三張 PNG、JPEG 或 WebP，每張 384 KiB。用 {{asset1}} 等代號引用，執行前會顯示這次要下載的網址。", style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = downloading == null && (html.isNotBlank() || css.isNotBlank() || js.isNotBlank()),
            onClick = ::tryPreview) { Text("執行預覽") }
        TextButton(enabled = document != null || downloading != null, onClick = {
            downloading?.cancel()
            document = null
            console.clear()
            notice = "已停止。"
        }) { Text("停止") }
        TextButton(enabled = document != null, onClick = { exporter.launch("zeroagent-preview.html") }) { Text("匯出 HTML") }
    }
    pendingRun?.let { run ->
        AlertDialog(
            onDismissRequest = { pendingRun = null },
            title = { Text("下載這次預覽的公開圖片？") },
            text = {
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    Text("App 只會下載下列網址的圖片，並將圖片交給離線預覽。網址的網站可能記錄這次連線。")
                    run.urls.forEach { Text(it.toString(), style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pendingRun !== run || downloading != null) return@TextButton
                    pendingRun = null
                    downloading = scope.launch {
                        try {
                            notice = "正在下載已確認的圖片…"
                            val assets = run.urls.map { fetchApprovedPreviewAsset(it) }
                            showPreview(run, assets)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            notice = failure.message ?: "無法使用外部圖片。"
                        } finally {
                            downloading = null
                        }
                    }
                }) { Text("同意下載並執行") }
            },
            dismissButton = { TextButton(onClick = { pendingRun = null }) { Text("取消") } },
        )
    }
    if (notice.isNotBlank()) Text(notice)
    val snapshot = document
    if (snapshot != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(widthMode == "full", { widthMode = "full" }, { Text("可用寬度") })
            FilterChip(widthMode == "phone", { widthMode = "phone" }, { Text("窄手機") })
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.TopCenter) {
                key(generation) {
                    IsolatedPreview(snapshot, Modifier.then(if (widthMode == "phone") Modifier.widthIn(max = 320.dp).fillMaxWidth() else Modifier.fillMaxWidth()).height(380.dp)) {
                        if (console.size >= 40) console.removeAt(0)
                        console.add(it.take(500))
                    }
                }
            }
        }
        WorkspaceResult("執行訊息", console.joinToString("\n").ifBlank { "目前沒有回報的錯誤或輸出。" })
    }
}

private data class PreviewRun(val html: String, val css: String, val js: String, val urls: List<HttpUrl>)

@Suppress("SetJavaScriptEnabled")
@Composable
private fun IsolatedPreview(source: String, modifier: Modifier, log: (String) -> Unit) {
    val latestLog by rememberUpdatedState(log)
    val seedUrl = "https://zeroagent-preview.invalid/"
    var rendererGone by remember { mutableStateOf(false) }
    if (rendererGone) return
    AndroidView(modifier = modifier, factory = { context ->
        WebView(context).apply {
            // No platform bridges. No network, local URI, permissions or external navigation.
            settings.javaScriptEnabled = true
            settings.blockNetworkLoads = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = false
            settings.domStorageEnabled = false
            settings.setGeolocationEnabled(false)
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.mediaPlaybackRequiresUserGesture = true
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            setDownloadListener { _, _, _, _, _ -> latestLog("預覽已阻擋頁面下載。請使用上方匯出按鈕。") }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    latestLog("${message.messageLevel().name} · 第 ${message.lineNumber()} 行 · ${message.message()}")
                    return true
                }
                override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                    callback.invoke(origin, false, false)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.toString().substringBefore('#') == seedUrl && request.url.fragment != null) return false
                    latestLog("預覽已阻擋頁面跳轉。")
                    return true
                }
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                    WebResourceResponse("text/plain", "utf-8", 403, "Preview is offline", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    if (url?.substringBefore('#') != seedUrl && url != "about:blank") view.stopLoading()
                }
                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                    latestLog("預覽已停止。請檢查無限迴圈或記憶體用量，再重新執行。")
                    rendererGone = true
                    return true
                }
            }
            loadDataWithBaseURL(seedUrl, source, "text/html", "UTF-8", null)
        }
    }, onRelease = { view ->
        if (!rendererGone) view.stopLoading()
        view.destroy()
    }, update = {})
}


private fun isAcceptanceUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value.trim())
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AcceptanceWorkspace(openArtifact: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val artifactAvailable by remember(context) { LabPreferences.flow(context, "artifact", "false") }
        .collectAsStateWithLifecycle(LabPreferences.get(context, "artifact", "false"))
    val artifactRaw by remember(context) { LabPreferences.flow(context, "artifact_data") }
        .collectAsStateWithLifecycle(LabPreferences.get(context, "artifact_data"))
    val artifact = remember(artifactRaw) { runCatching { Json.decodeFromString<LabArtifact>(artifactRaw) }.getOrNull() }
    val artifactVersions = remember(artifact) {
        artifact?.let { (it.history + it.content).associateBy(::artifactEvidenceDigest) }.orEmpty()
    }
    val currentDigest = remember(artifact) { artifact?.content?.let(::artifactEvidenceDigest) }
    var evidenceVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewedDigest by rememberSaveable { mutableStateOf<String?>(null) }
    val saved by remember(context) { LabPreferences.flow(context, "acceptance_data") }
        .collectAsStateWithLifecycle(LabPreferences.get(context, "acceptance_data"))
    val rows = remember(saved) { runCatching { Json.decodeFromString<List<AcceptanceRow>>(saved) }.getOrDefault(emptyList()) }
    var requirements by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var checkedRows by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var evidence by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("尚未驗證") }
    var sourceUrl by rememberSaveable { mutableStateOf("") }
    var linksArtifact by rememberSaveable { mutableStateOf(false) }
    var sourceNotice by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    fun save(value: List<AcceptanceRow>) = LabPreferences.set(context, "acceptance_data", Json.encodeToString(value))
    Text("每行一項需求。只閱讀過成品時選「內容已核對」，實際操作並記錄結果後再選「實測通過」。紀錄保存在此裝置。")
    WorkspaceField(requirements, { requirements = it }, "新增多項需求，每行一項", 3, cap = 10_000)
    val lines = requirements.lines().map(String::trim).filter(String::isNotBlank)
    Button(enabled = lines.isNotEmpty() && rows.size + lines.size <= 120, onClick = {
        save(rows + lines.map { AcceptanceRow(requirement = it) }); requirements = ""
    }) { Text("加入驗收表") }
    if (lines.size + rows.size > 120) Text("一份驗收表最多 120 項。", color = MaterialTheme.colorScheme.error)
    if (rows.isNotEmpty()) {
        val known = rows.count { it.effectiveStatus(currentDigest) == "內容已核對" }
        val tested = rows.count { it.effectiveStatus(currentDigest) == "實測通過" }
        Text("內容已核對 $known · 實測通過 $tested · 尚未驗證 ${rows.size - known - tested}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = checkedRows.isNotEmpty(), onClick = { pending = "reset" }) { Text("重設勾選項目的驗收狀態") }
            TextButton(onClick = { pending = "delete" }) { Text("清除整份表") }
        }
    }
    rows.forEach { row ->
        ListItem(
            headlineContent = { Text(row.requirement) },
            supportingContent = {
                Text(row.effectiveStatus(currentDigest))
                if (row.needsArtifactReview(currentDigest)) Text("成品版本已變更或缺少版本紀錄，請重新驗收。")
            },
            leadingContent = { Checkbox(row.id in checkedRows, { checkedRows = ArrayList(if (it) checkedRows + row.id else checkedRows - row.id) }) },
            trailingContent = { TextButton(onClick = {
                selected = if (selected == row.id) null else row.id
                evidence = row.evidence
                status = row.effectiveStatus(currentDigest)
                reviewedDigest = currentDigest
                sourceUrl = row.sourceUrl
                linksArtifact = row.linksArtifact
                sourceNotice = ""
            }) { Text(if (selected == row.id) "收起" else "紀錄") } },
        )
        if (selected == row.id) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("尚未驗證", "內容已核對", "實測通過").forEach { item -> FilterChip(status == item, { status = item }, { Text(item) }) }
            }
            WorkspaceField(evidence, { evidence = it }, "對應修改、驗證環境和結果", 3, cap = 2500)
            WorkspaceField(sourceUrl, { sourceUrl = it; sourceNotice = "" }, "檢查結果或修改的 HTTPS 連結（選填）", cap = 2000)
            if (sourceUrl.isNotBlank() && !isAcceptanceUrl(sourceUrl)) {
                Text("請輸入完整的 HTTPS 網址。", color = MaterialTheme.colorScheme.error)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(linksArtifact, { linksArtifact = it })
                Text("這一項可從成品工作區查看")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isAcceptanceUrl(sourceUrl)) TextButton(onClick = {
                    runCatching { uriHandler.openUri(sourceUrl.trim()) }
                        .onFailure { sourceNotice = "裝置目前無法開啟這個連結。" }
                }) { Text("開啟連結") }
                if (linksArtifact) {
                    TextButton(enabled = artifactAvailable == "true", onClick = openArtifact) { Text("查看目前成品") }
                    row.artifactDigest?.let { digest ->
                        TextButton(onClick = { evidenceVersion = digest }) { Text("查看驗收時的版本") }
                    }
                }
            }
            if (linksArtifact && artifactAvailable != "true") Text("請先在 Lab 啟用成品工作區。")
            if (sourceNotice.isNotEmpty()) Text(sourceNotice, color = MaterialTheme.colorScheme.error)
            FilledTonalButton(enabled = (status == "尚未驗證" || evidence.isNotBlank()) &&
                (sourceUrl.isBlank() || isAcceptanceUrl(sourceUrl)) &&
                (!linksArtifact || status == "尚未驗證" || (reviewedDigest != null && reviewedDigest == currentDigest)), onClick = {
                save(rows.map {
                    if (it.id == row.id) it.copy(status = status, evidence = evidence.trim(),
                        sourceUrl = sourceUrl.trim(), linksArtifact = linksArtifact,
                        artifactDigest = if (!linksArtifact) null else if (status == "尚未驗證") it.artifactDigest else reviewedDigest) else it
                })
                selected = null
            }) { Text("保存驗收紀錄") }
        }
    }
    evidenceVersion?.let { digest ->
        AlertDialog(
            onDismissRequest = { evidenceVersion = null },
            title = { Text("驗收時的成品") },
            text = {
                Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(artifactVersions[digest] ?: "這個版本已不在成品歷史中。")
                }
            },
            confirmButton = { TextButton(onClick = { evidenceVersion = null }) { Text("關閉") } },
        )
    }
    pending?.let { change ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text(if (change == "reset") "重新驗收勾選的項目？" else "刪除全部驗收紀錄？") },
            text = { Text(if (change == "reset") "舊證據會保留為歷史文字，狀態改為尚未驗證。" else "需求、狀態與驗收證據將永久清除。") },
            confirmButton = { TextButton(onClick = {
                if (change == "reset") save(rows.map { if (it.id in checkedRows) it.copy(status = "尚未驗證", evidence = it.evidence.takeIf(String::isNotBlank)?.let { prior -> "上次紀錄：\n$prior" }.orEmpty().take(2500)) else it })
                else LabPreferences.remove(context, "acceptance_data")
                checkedRows = arrayListOf(); pending = null; selected = null
            }) { Text("確認") } }, dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } })
    }
}

@Composable
internal fun PrivateWorkspace() {
    val store: SettingsStore = koinInject()
    val settings by store.settingsFlow.collectAsStateWithLifecycle()
    val model = settings.findModelById(settings.chatModelId)?.takeIf { it.findProvider(settings.providers)?.enabled == true }
    var open by remember { mutableStateOf(false) }
    Text("對話只留在這次畫面。關閉會清除文字和這次對話的回覆。若使用雲端模型，供應商端的資料則依其政策處理。")
    Button(enabled = model != null, onClick = { open = true }) { Text("開始臨時對話") }
    if (model == null) Text("請先設定可用的預設文字模型。")
    if (open) TemporaryChatDialog(settings, model) { open = false }
}
