package com.ultrabrowser.pro

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

data class MediaItem(val url: String, val title: String, val type: String)

class MainActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var urlInput by remember { mutableStateOf("https://duckduckgo.com") }
                var currentUrl by remember { mutableStateOf("https://duckduckgo.com") }
                var webView by remember { mutableStateOf<WebView?>(null) }
                val detectedList = remember { mutableStateListOf<MediaItem>() }
                var showSheet by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                TextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            val target = if (urlInput.startsWith("http")) urlInput else "https://duckduckgo.com/?q=$urlInput"
                                            currentUrl = target
                                            webView?.loadUrl(target)
                                        }) { Icon(Icons.Default.ArrowForward, "Go") }
                                    }
                                )
                            }
                        )
                    },
                    bottomBar = {
                        BottomAppBar {
                            IconButton(onClick = { if (webView?.canGoBack() == true) webView?.goBack() }) { Icon(Icons.Default.ArrowBack, "Back") }
                            IconButton(onClick = { if (webView?.canGoForward() == true) webView?.goForward() }) { Icon(Icons.Default.ArrowForward, "Forward") }
                            IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, "Reload") }
                        }
                    },
                    floatingActionButton = {
                        if (detectedList.isNotEmpty()) {
                            FloatingActionButton(onClick = { showSheet = true }) {
                                Row(Modifier.padding(12.dp)) {
                                    Icon(Icons.Default.VideoLibrary, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("${detectedList.size}")
                                }
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true

                                    addJavascriptInterface(object {
                                        @JavascriptInterface
                                        fun onMedia(src: String) {
                                            if (detectedList.none { it.url == src }) {
                                                detectedList.add(MediaItem(src, "Detected Video", "Video"))
                                            }
                                        }
                                    }, "UltraBridge")

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            view?.evaluateJavascript("""
                                                document.querySelectorAll('video, audio').forEach(el => {
                                                    let s = el.currentSrc || el.src;
                                                    if(s && !s.startsWith('blob:')) window.UltraBridge.onMedia(s);
                                                });
                                            """.trimIndent(), null)
                                        }

                                        override fun shouldInterceptRequest(view: WebView?, req: WebResourceRequest?): WebResourceResponse? {
                                            val u = req?.url?.toString() ?: ""
                                            if (listOf(".mp4", ".m3u8", ".mpd", ".webm", ".mp3").any { u.contains(it) }) {
                                                if (detectedList.none { it.url == u }) {
                                                    val ext = if (u.contains("m3u8")) "HLS Stream" else "Video"
                                                    detectedList.add(MediaItem(u, "Media Stream", ext))
                                                }
                                            }
                                            return super.shouldInterceptRequest(view, req)
                                        }
                                    }
                                    loadUrl(currentUrl)
                                    webView = this
                                }
                            }
                        )
                    }

                    if (showSheet) {
                        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Detected Media (${detectedList.size})", style = MaterialTheme.typography.titleLarge)
                                LazyColumn {
                                    items(detectedList) { item ->
                                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                            showSheet = false
                                            downloadMedia(this@MainActivity, item.url)
                                        }) {
                                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Movie, null)
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(item.title)
                                                    Text(item.type, style = MaterialTheme.typography.bodySmall)
                                                }
                                                Icon(Icons.Default.Download, null)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun downloadMedia(ctx: Context, url: String) {
        try {
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle("Downloading Media")
                .setDescription("Ultra Browser Pro Downloader")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "video_${System.currentTimeMillis()}.mp4")
            (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(ctx, "Download Started!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
