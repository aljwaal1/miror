package com.explapp.mirror

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BrowserActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_MEDIA_URL = "com.explapp.mirror.MEDIA_URL"
        private const val HOME_URL = "https://www.google.com"
    }

    private lateinit var addressBar: EditText
    private lateinit var webView: WebView
    private lateinit var mediaStatus: TextView
    private lateinit var castButton: Button
    private var detectedMediaUrl: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addressBar = EditText(this).apply {
            setSingleLine(true)
            hint = "https://example.com"
            textDirection = View.TEXT_DIRECTION_LTR
            setOnEditorActionListener { _, _, _ ->
                openAddress(text.toString())
                true
            }
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            webViewClient = BrowserClient()
        }

        mediaStatus = TextView(this).apply {
            text = "لم يتم اكتشاف رابط وسائط مباشر بعد"
            setPadding(18, 10, 18, 10)
            maxLines = 2
        }
        castButton = Button(this).apply {
            text = "تشغيل على التلفاز"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { returnMediaUrl() }
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(navButton("رجوع") { if (webView.canGoBack()) webView.goBack() })
            addView(navButton("تقدم") { if (webView.canGoForward()) webView.goForward() })
            addView(navButton("تحديث") { webView.reload() })
            addView(navButton("الرئيسية") { webView.loadUrl(HOME_URL) })
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(addressBar, matchWrap())
            addView(controls, matchWrap())
            addView(mediaStatus, matchWrap())
            addView(castButton, matchWrap())
            addView(webView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }
        setContentView(root)
        webView.loadUrl(HOME_URL)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun navButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun openAddress(rawValue: String) {
        val value = rawValue.trim()
        if (value.isBlank()) return
        val url = if (value.startsWith("http://") || value.startsWith("https://")) {
            value
        } else {
            "https://$value"
        }
        webView.loadUrl(url)
    }

    private fun detectMedia(rawUrl: String?) {
        val url = rawUrl?.trim().orEmpty()
        if (!isSupportedMediaUrl(url) || url == detectedMediaUrl) return
        runOnUiThread {
            detectedMediaUrl = url
            mediaStatus.text = "تم اكتشاف وسيط مباشر:\n$url"
            castButton.isEnabled = true
        }
    }

    private fun scanPageMedia() {
        val script = """
            (function() {
              const urls = [];
              document.querySelectorAll('video,audio,source,img,a[href]').forEach(function(el) {
                ['src','currentSrc','href'].forEach(function(key) {
                  const value = el[key] || el.getAttribute && el.getAttribute(key);
                  if (value) { try { urls.push(new URL(value, document.baseURI).href); } catch (_) {} }
                });
              });
              return urls.join('\\n');
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { encoded ->
            val decoded = encoded
                .removeSurrounding("\"")
                .replace("\\n", "\n")
                .replace("\\/", "/")
                .replace("\\u0026", "&")
            decoded.lineSequence().firstOrNull(::isSupportedMediaUrl)?.let(::detectMedia)
        }
    }

    private fun isSupportedMediaUrl(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val path = Uri.parse(url).path.orEmpty().lowercase()
        return SUPPORTED_EXTENSIONS.any(path::endsWith)
    }

    private fun returnMediaUrl() {
        val url = detectedMediaUrl ?: return
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_MEDIA_URL, url))
        finish()
    }

    private inner class BrowserClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            detectMedia(url)
            return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            addressBar.setText(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            addressBar.setText(url)
            detectMedia(url)
            scanPageMedia()
        }

        override fun onLoadResource(view: WebView, url: String) {
            detectMedia(url)
        }
    }
}

private val SUPPORTED_EXTENSIONS = setOf(
    ".mp4", ".m4v", ".webm", ".m3u8", ".mp3", ".m4a", ".aac",
    ".wav", ".flac", ".jpg", ".jpeg", ".png", ".webp", ".gif"
)
