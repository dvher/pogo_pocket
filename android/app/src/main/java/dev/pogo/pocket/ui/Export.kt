package dev.pogo.pocket.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.OpenableColumns
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** File export and import through the system file picker (SAF), and printing. */
object Export {
    /** Keeps the print WebView alive until its print job has started. */
    private var printing: WebView? = null

    suspend fun write(context: Context, uri: Uri, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Can't open the file")
            out.use { it.write(data) }
        }
    }

    suspend fun read(context: Context, uri: Uri): Pair<String, ByteArray> = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Can't open the file")
        displayName(context, uri) to bytes
    }

    private fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()

    /** Opens the system print dialog for a page, where "Save as PDF" is one of the printers. */
    fun print(activity: Activity, title: String, html: String) {
        val web = WebView(activity)
        printing = web
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            override fun onPageFinished(view: WebView, url: String?) {
                if (printing !== view) return
                printing = null
                val manager = activity.getSystemService(PrintManager::class.java)
                manager.print(title, view.createPrintDocumentAdapter(title), PrintAttributes.Builder().build())
            }
        }
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}
