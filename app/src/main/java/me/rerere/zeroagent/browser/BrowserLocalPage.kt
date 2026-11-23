package me.rerere.zeroagent.browser

import android.content.Context
import android.net.Uri
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.security.MessageDigest
import me.rerere.zeroagent.data.files.FileFolders

/** Exposes only the selected skill directory or a single generated iframe wrapper. */
internal class BrowserLocalPage(val url: String, val assetLoader: WebViewAssetLoader) {
    companion object {
        private const val ROUTE = "/preview/"

        fun resolve(context: Context, url: String): BrowserLocalPage? {
            val uri = Uri.parse(url)
            if (uri.scheme != "file" || !uri.authority.isNullOrEmpty()) return null
            val path = uri.path ?: return null
            if (!path.startsWith('/')) return null
            return runCatching {
                val target = File(path).canonicalFile.takeIf { it.isFile } ?: return null
                val skills = context.filesDir.resolve(FileFolders.SKILLS).canonicalFile
                val wrappers = context.cacheDir.resolve("skill-webview").canonicalFile
                val (root, singleFile) = when {
                    target.parentFile == wrappers && target.name.matches(Regex("[a-f0-9]{64}\\.html")) -> wrappers to true
                    target.toPath().startsWith(skills.toPath()) -> {
                        val relative = target.relativeTo(skills).toPath()
                        if (relative.nameCount < 2) return null
                        skills.resolve(relative.getName(0).toString()) to false
                    }
                    else -> return null
                }
                val relative = target.relativeTo(root).invariantSeparatorsPath
                val handler = WebViewAssetLoader.InternalStoragePathHandler(context, root)
                val hostKey = if (singleFile) target.canonicalPath else root.canonicalPath
                val hostId = MessageDigest.getInstance("SHA-256").digest(hostKey.toByteArray())
                    .take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val host = "preview-$hostId.appassets.androidplatform.net"
                val loader = WebViewAssetLoader.Builder()
                    .setDomain(host)
                    .addPathHandler(ROUTE) { requested ->
                        if (singleFile && requested != relative) null else handler.handle(requested)
                    }
                    .build()
                val address = Uri.Builder().scheme("https").authority(host)
                    .encodedPath(ROUTE + Uri.encode(relative, "/"))
                    .encodedQuery(uri.encodedQuery)
                    .encodedFragment(uri.encodedFragment)
                    .build()
                BrowserLocalPage(address.toString(), loader)
            }.getOrNull()
        }
    }
}
