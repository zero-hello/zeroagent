package me.rerere.zeroagent.ui.components.richtext

import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.JavascriptInterface
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.View
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.ui.components.webview.WEB_VIEW_ASSET_URL
import me.rerere.zeroagent.ui.components.webview.WEB_VIEW_BASE_URL
import me.rerere.zeroagent.ui.components.webview.WebView
import me.rerere.zeroagent.ui.components.webview.WebViewContentCache
import me.rerere.zeroagent.ui.components.webview.rememberWebViewState
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.context.LocalToaster
import me.rerere.zeroagent.ui.theme.LocalDarkMode
import me.rerere.zeroagent.utils.escapeHtml
import me.rerere.zeroagent.utils.exportImage
import me.rerere.zeroagent.utils.toCssHex

@Composable
fun Mermaid(
    code: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val darkMode = LocalDarkMode.current
    val context = LocalContext.current
    val localizedResources = androidx.compose.ui.platform.LocalResources.current
    val activity = LocalActivity.current
    val toaster = LocalToaster.current
    val navController = LocalNavController.current

    val exportScope = rememberCoroutineScope()
    val jsInterface = remember(activity, context, localizedResources, toaster, exportScope) {
        MermaidInterface { base64Image ->
            exportScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val host = checkNotNull(activity)
                        check(base64Image.length in 1..MAX_MERMAID_EXPORT_CHARS)
                        val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
                        check(bounds.outMimeType == "image/png" && bounds.outWidth > 0 &&
                            bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000L)
                        val bitmap = checkNotNull(BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size))
                        try {
                            context.exportImage(host, bitmap, "mermaid_${System.currentTimeMillis()}.png")
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
                toaster.show(
                    localizedResources.getString(
                        if (result.isSuccess) R.string.mermaid_export_success else R.string.mermaid_export_failed
                    ),
                    type = if (result.isSuccess) ToastType.Success else ToastType.Error,
                )
            }
        }
    }

    val html = remember(code, colorScheme, darkMode) {
        buildMermaidHtml(
            code = code,
            colorScheme = colorScheme,
        )
    }

    val webViewState = rememberWebViewState(
        data = html,
        baseUrl = WEB_VIEW_BASE_URL,
        mimeType = "text/html",
        encoding = "UTF-8",
        interfaces = mapOf(
            "AndroidInterface" to jsInterface
        ),
        settings = {
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
    )

    Column(
        modifier = modifier
    ) {
        WebView(
            state = webViewState,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .height(200.dp),
        )

        if (activity != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = {
                        val contentId = WebViewContentCache.store(context.cacheDir, html)
                        navController.navigate(Screen.WebView(contentId = contentId))
                    },
                ) {
                    Icon(
                        HugeIcons.View,
                        contentDescription = stringResource(R.string.code_block_preview)
                    )
                }
                IconButton(
                    onClick = {
                        val webView = webViewState.webView
                        if (webView != null && !webViewState.isLoading && jsInterface.requestExport()) {
                            webView.evaluateJavascript("typeof exportSvgToPng === 'function' && (exportSvgToPng(), true);") {
                                if (it != "true") jsInterface.cancelExport()
                            }
                        }
                    },
                ) {
                    Icon(
                        HugeIcons.Download01,
                        contentDescription = stringResource(R.string.mermaid_export)
                    )
                }
            }
        }
    }
}

private const val MAX_MERMAID_EXPORT_CHARS = 16 * 1024 * 1024

private class MermaidInterface(
    private val onExportImage: (String) -> Unit
) {
    private val exportRequested = AtomicBoolean(false)

    fun requestExport() = exportRequested.compareAndSet(false, true)

    fun cancelExport() {
        exportRequested.set(false)
    }

    @JavascriptInterface
    fun exportImage(base64Image: String) {
        if (exportRequested.compareAndSet(true, false)) onExportImage(base64Image)
    }
}

private fun buildMermaidHtml(
    code: String,
    colorScheme: ColorScheme,
): String {
    val primaryColor = colorScheme.primaryContainer.toCssHex()
    val secondaryColor = colorScheme.secondaryContainer.toCssHex()
    val tertiaryColor = colorScheme.tertiaryContainer.toCssHex()
    val background = colorScheme.background.toCssHex()
    val surface = colorScheme.surface.toCssHex()
    val onPrimary = colorScheme.onPrimaryContainer.toCssHex()
    val onSecondary = colorScheme.onSecondaryContainer.toCssHex()
    val onTertiary = colorScheme.onTertiaryContainer.toCssHex()
    val onBackground = colorScheme.onBackground.toCssHex()
    val errorColor = colorScheme.error.toCssHex()
    val onErrorColor = colorScheme.onError.toCssHex()

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=1024">
            <script src="${WEB_VIEW_ASSET_URL}/html/mermaid.min.js"></script>
            <style>
                body {
                    margin: 0;
                    padding: 0;
                    display: flex;
                    justify-content: center;
                    background-color: ${background};
                }
                .mermaid {
                    padding: 8px;
                    width: fit-content;
                    min-width: 100%;
                }
                .mermaid svg {
                    background: transparent !important;
                }
            </style>
        </head>
        <body>
            <pre class="mermaid">
                ${code.escapeHtml()}
            </pre>
            <script>
              mermaid.initialize({
                    startOnLoad: true,
                    securityLevel: 'strict',
                    theme: 'base',
                    themeVariables: {
                        primaryColor: '${primaryColor}',
                        primaryTextColor: '${onPrimary}',
                        primaryBorderColor: '${primaryColor}',

                        secondaryColor: '${secondaryColor}',
                        secondaryTextColor: '${onSecondary}',
                        secondaryBorderColor: '${secondaryColor}',

                        tertiaryColor: '${tertiaryColor}',
                        tertiaryTextColor: '${onTertiary}',
                        tertiaryBorderColor: '${tertiaryColor}',

                        background: '${background}',
                        mainBkg: '${primaryColor}',
                        secondBkg: '${secondaryColor}',

                        lineColor: '${onBackground}',
                        textColor: '${onBackground}',

                        nodeBkg: '${surface}',
                        nodeBorder: '${primaryColor}',
                        clusterBkg: '${surface}',
                        clusterBorder: '${primaryColor}',

                        actorBorder: '${primaryColor}',
                        actorBkg: '${surface}',
                        actorTextColor: '${onBackground}',
                        actorLineColor: '${primaryColor}',

                        taskBorderColor: '${primaryColor}',
                        taskBkgColor: '${primaryColor}',
                        taskTextLightColor: '${onPrimary}',
                        taskTextDarkColor: '${onBackground}',

                        labelColor: '${onBackground}',
                        errorBkgColor: '${errorColor}',
                        errorTextColor: '${onErrorColor}'
                    }
              });

              window.exportSvgToPng = function() {
                try {
                    const svgElement = document.querySelector('.mermaid svg');
                    if (!svgElement) {
                        AndroidInterface.exportImage('');
                        return;
                    }

                    const canvas = document.createElement('canvas');
                    const ctx = canvas.getContext('2d');

                    const svgRect = svgElement.getBoundingClientRect();
                    const width = svgRect.width;
                    const height = svgRect.height;

                    const scaleFactor = window.devicePixelRatio * 2;
                    canvas.width = width * scaleFactor;
                    canvas.height = height * scaleFactor;

                    const svgXml = new XMLSerializer().serializeToString(svgElement);
                    const svgBase64 = btoa(unescape(encodeURIComponent(svgXml)));

                    const img = new Image();
                    img.onload = function() {
                        ctx.fillStyle = '${background}';
                        ctx.fillRect(0, 0, canvas.width, canvas.height);
                        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);

                        ctx.font = '14px Arial';
                        ctx.fillStyle = '${onBackground}';
                        ctx.fillText('ZeroAgent', 20, canvas.height - 10);

                        const pngBase64 = canvas.toDataURL('image/png').split(',')[1];
                        AndroidInterface.exportImage(pngBase64);
                    };
                    img.onerror = function(e) {
                        AndroidInterface.exportImage('');
                    }
                    img.src = 'data:image/svg+xml;base64,' + svgBase64;
                } catch (e) {
                    AndroidInterface.exportImage('');
                }
              };
            </script>
        </body>
        </html>
    """.trimIndent()
}
