package com.akira.app.feature.online

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.akira.app.core.design.theme.AkiraTheme
import com.akira.app.core.online.OnlineRepository
import com.akira.app.core.design.navigation.PageBackButton
import com.akira.app.core.design.components.localizedText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.json.JSONObject

/** Google owns the sign-in form. Akira reads only the resulting Music session. */
class YouTubeLoginActivity : ComponentActivity() {
    private val navigationPolicy by lazy { GoogleLoginNavigation(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { AkiraTheme(themeMode = intent.getStringExtra("themeMode") ?: "SYSTEM",
            appearancePreset = intent.getStringExtra("appearancePreset") ?: "MIDNIGHT") {
            Surface(Modifier.fillMaxSize()) { LoginContent() }
        } }
    }
    @SuppressLint("SetJavaScriptEnabled")
    @Composable private fun LoginContent() {
        var webView by remember { mutableStateOf<WebView?>(null) }
        var loading by remember { mutableStateOf(true) }
        var saving by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var pageFailed by remember { mutableStateOf(false) }
        var pageVisible by remember { mutableStateOf(false) }
        var slowLoad by remember { mutableStateOf(false) }
        var attempt by remember { mutableIntStateOf(0) }
        var navigation by remember { mutableIntStateOf(0) }
        var location by remember { mutableStateOf("accounts.google.com") }
        fun capture(view: WebView, url: String) {
            if (saving || failed || Uri.parse(url).host != "music.youtube.com") return
            val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com") ?: return
            if (!cookie.split(';').any { it.trim().startsWith("SAPISID=") || it.trim().startsWith("__Secure-3PAPISID=") }) return
            saving = true
            trace("session verification started", url)
            lifecycleScope.launch {
                    try {
                        withTimeout(30_000) {
                            // Google can land on Music before SetSID and ytcfg finish propagating after MFA.
                            for (round in 0..2) {
                                delay(if (round == 0) 800L else 1500L)
                                try {
                                    val config = withTimeout(3_000) { suspendCancellableCoroutine<JSONObject?> { continuation ->
                                        view.evaluateJavascript(CONFIG_SCRIPT) { result ->
                                            if (continuation.isActive) continuation.resume(runCatching { JSONObject(result) }.getOrNull())
                                        }
                                    } }
                                    fun value(key: String) = config?.optString(key)?.takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
                                    val freshCookie = CookieManager.getInstance().getCookie("https://music.youtube.com") ?: cookie
                                    withTimeout(7_000) { OnlineRepository.get(this@YouTubeLoginActivity).connect(freshCookie, value("visitor"), value("sync")) }
                                    break
                                } catch (timeout: TimeoutCancellationException) { if (round == 2) throw timeout }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (error: Exception) { if (round == 2) throw error }
                            }
                        }
                        if(com.akira.app.BuildConfig.DEBUG) android.util.Log.d("AkiraSignIn", "session verified")
                        finish()
                    } catch (_: TimeoutCancellationException) { trace("session verification timed out"); failed = true; saving = false; loading = false }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { trace("session verification failed type=${error.javaClass.simpleName}"); failed = true; saving = false }
            }
        }
        // Music's SPA may never finish its initial page load. Session capture must not depend on that callback.
        LaunchedEffect(webView, attempt) {
            val view = webView ?: return@LaunchedEffect
            while (true) {
                delay(750)
                view.url?.let { capture(view, it) }
            }
        }
        BackHandler { val view = webView; if (view?.canGoBack() == true && !saving) view.goBack() else finish() }
        LaunchedEffect(loading, saving, pageVisible, navigation, attempt) {
            slowLoad = false
            // MFA can remain interactive without finishing all background requests.
            // A slow load must not mark the document as failed or block session capture.
            if (loading && !saving && !pageVisible) { delay(25_000); slowLoad = loading && !saving && !pageVisible }
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PageBackButton({ finish() })
                Column {
                    Text(localizedText("Connect YouTube Music", "Conectar YouTube Music"), style = MaterialTheme.typography.titleMedium)
                    Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                if (saving || loading) LinearProgressIndicator(Modifier.fillMaxSize())
            }
            if (saving) Text(localizedText("Finishing connection…", "Terminando la conexión…"), Modifier.padding(16.dp))
            if (failed) Column(Modifier.padding(16.dp)) {
                Text(localizedText("Couldn't verify the Music session. Open YouTube Music and try again.",
                    "No se pudo verificar la sesión. Abre YouTube Music e inténtalo de nuevo."))
                TextButton(onClick = { failed = false; webView?.loadUrl("https://music.youtube.com/") }) {
                    Text(localizedText("Retry", "Reintentar"))
                }
            }
            if (pageFailed || slowLoad) Column(Modifier.padding(20.dp)) {
                Text(if(pageFailed) localizedText("The sign-in page couldn't load. You can retry or go back and keep listening without an account.",
                    "No se pudo cargar la página de conexión. Reintenta o vuelve para seguir escuchando sin cuenta.")
                    else localizedText("The sign-in page is taking longer than usual. You can wait or reload it.",
                        "La página de conexión está tardando más de lo habitual. Puedes esperar o volver a cargarla."))
                TextButton(onClick = { pageFailed = false; loading = true; attempt++ }) {
                    Text(localizedText("Reload sign-in", "Volver a cargar"))
                }
            }
            key(attempt) { AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                WebView(context).apply {
                    webView = this
                    // Chromium needs an explicit viewport inside AndroidView; WRAP_CONTENT can lay it out at zero height.
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.WHITE)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    if (Build.VERSION.SDK_INT >= 33) settings.isAlgorithmicDarkeningAllowed = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.saveFormData = false
                    // Use this installed Chromium's identity, not the provider's desktop Firefox API identity.
                    settings.userAgentString = WebSettings.getDefaultUserAgent(context)
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, progress: Int) {
                            if(progress >= 100) { loading = false; slowLoad = false }
                            if (progress >= 60) view.url?.let { capture(view, it) }
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val blocked = !navigationPolicy.allows(request.url)
                            if(blocked) trace("blocked navigation main=${request.isForMainFrame} gesture=${request.hasGesture()}", request.url.toString())
                            // A cancelled link does not mean that the visible sign-in document failed.
                            return blocked
                        }
                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            trace("page started", url)
                            loading = true; pageVisible = false; slowLoad = false; pageFailed = false; navigation++; location = Uri.parse(url).host.orEmpty()
                        }
                        override fun onPageCommitVisible(view: WebView, url: String) {
                            trace("page committed", url)
                            pageVisible = true; loading = false; slowLoad = false
                            capture(view, url)
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            trace("page finished", url)
                            loading = false; slowLoad = false
                            capture(view, url)
                        }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                            trace("TLS error code=${error.primaryError}", error.url)
                            handler.cancel()
                            if(error.url == view.url) { loading = false; pageFailed = true }
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if(request.isForMainFrame) trace("page error code=${error.errorCode}", request.url.toString())
                            if (request.isForMainFrame && request.url.toString() == view.url) { loading = false; pageFailed = true }
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if(request.isForMainFrame) trace("page HTTP status=${response.statusCode}", request.url.toString())
                            if (request.isForMainFrame && request.url.toString() == view.url && response.statusCode >= 400) { loading = false; pageFailed = true }
                        }
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            trace("renderer gone crashed=${detail.didCrash()}")
                            loading = false; pageFailed = true
                            (view.parent as? ViewGroup)?.removeView(view)
                            if (webView === view) webView = null
                            view.destroy()
                            return true
                        }
                    }
                    loadUrl(LOGIN_URL)
                }
            }, onRelease = { view ->
                if (webView === view) { webView = null; view.stopLoading(); view.destroy() }
            }) }
        }
    }
    companion object {
        private fun trace(event: String, url: String? = null) {
            if(!com.akira.app.BuildConfig.DEBUG) return
            val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() }
            val host = uri?.host?.lowercase(java.util.Locale.ROOT)?.split('.')?.takeLast(3)?.joinToString(".").orEmpty()
            android.util.Log.d("AkiraSignIn", if(uri == null) event else "$event scheme=${uri.scheme} host=$host")
        }
        private const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
        private const val CONFIG_SCRIPT = """(function(){try{function get(k){return (window.ytcfg&&ytcfg.get&&ytcfg.get(k))||(window.yt&&yt.config_&&yt.config_[k])||null}return {visitor:get('VISITOR_DATA'),sync:get('DATASYNC_ID')}}catch(e){return {}}})();"""
    }
}
