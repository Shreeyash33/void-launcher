package com.ark.voidlauncher.ui.widgets

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ark.voidlauncher.domain.CustomWidgetDefinition

/**
 * Sandboxed renderer for web-authored widgets.
 *
 * JavaScript runs inside an inline document with no bridge, no file/content
 * access, no network, no navigation and no dialogs. Definition validation also
 * rejects external resources before the widget can reach this renderer.
 */
@Composable
fun CustomWebViewWidget(
    definition: CustomWidgetDefinition,
    modifier: Modifier = Modifier,
) {
    val document = remember(definition) { buildDocument(definition) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                configureSandbox()
                tag = null
            }
        },
        update = { view ->
            if (view.tag != document) {
                view.loadDataWithBaseURL(null, document, "text/html", "UTF-8", null)
                view.tag = document
            }
        },
        onRelease = { view -> runCatching { view.destroy() } },
    )
}

private fun buildDocument(definition: CustomWidgetDefinition): String = buildString {
    append("<!doctype html><html><head>")
    append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
    append("<style>html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;")
    append("background:transparent;color:inherit;font-family:sans-serif;}</style>")
    append("<style>")
    append(definition.css.replace("</style", "<\\/style", ignoreCase = true))
    append("</style></head><body>")
    append(definition.html)
    if (definition.js.isNotBlank()) {
        append("<script>")
        append(definition.js.replace("</script", "<\\/script", ignoreCase = true))
        append("</script>")
    }
    append("</body></html>")
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureSandbox() {
    WebView.setWebContentsDebuggingEnabled(false)
    setBackgroundColor(Color.TRANSPARENT)
    isLongClickable = false
    setOnLongClickListener { true }

    settings.apply {
        javaScriptEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        setAllowFileAccessFromFileURLs(false)
        @Suppress("DEPRECATION")
        setAllowUniversalAccessFromFileURLs(false)
        blockNetworkLoads = true
        blockNetworkImage = true
        @Suppress("DEPRECATION")
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        cacheMode = WebSettings.LOAD_NO_CACHE
        databaseEnabled = false
        domStorageEnabled = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        @Suppress("DEPRECATION")
        savePassword = false
        setGeolocationEnabled(false)
    }

    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

        @Deprecated("Deprecated for API 24+, kept for older framework callbacks")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true
    }

    webChromeClient = object : WebChromeClient() {
        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            result?.cancel()
            return true
        }

        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            result?.cancel()
            return true
        }

        override fun onJsPrompt(
            view: WebView?,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult?,
        ): Boolean {
            result?.cancel()
            return true
        }
    }
}

/** Shown when the layout references a custom id whose bundle has not been imported yet. */
@Composable
fun MissingCustomWidget(id: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "CUSTOM WIDGET MISSING",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = id,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shown for a forward-compatible reference scheme this launcher cannot render. */
@Composable
fun UnknownWidgetReference(referenceId: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "WIDGET UNAVAILABLE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = referenceId,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
