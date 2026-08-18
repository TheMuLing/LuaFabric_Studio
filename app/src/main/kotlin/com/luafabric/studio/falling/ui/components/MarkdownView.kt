package com.luafabric.studio.falling.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class MarkdownView @JvmOverloads constructor(
    private val mContext: Context,
    attributeSet: AttributeSet? = null,
    i: Int = 0
) : WebView(
    mContext, attributeSet, i
) {
    var isOpenUrlInBrowser: Boolean = false
    private var mPreviewText: String? = null

    init {
        initialize()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    private fun initialize() {
        webViewClient = object : WebViewClient() {
            // android.webkit.WebViewClient
            override fun onPageFinished(webView: WebView?, str: String?) {
                this@MarkdownView.evaluateJavascript(this@MarkdownView.mPreviewText!!, null)
            }

            @Suppress("deprecation")  // android.webkit.WebViewClient
            override fun shouldOverrideUrlLoading(webView: WebView?, str: String?): Boolean {
                if (this@MarkdownView.isOpenUrlInBrowser) {
                    this@MarkdownView.mContext.startActivity(
                        Intent(
                            "android.intent.action.VIEW",
                            str?.toUri()
                        )
                    )
                    return true
                }
                return false
            }
        }
        loadUrl("file:///android_asset/html/preview.html")
        settings.javaScriptEnabled = true
        settings.allowUniversalAccessFromFileURLs = true
        0.also { settings.mixedContentMode = 0 }
    }

    fun loadFromFile(file: File?) {
        var str: String?
        try {
            val fileInputStream = FileInputStream(file)
            val bufferedReader = BufferedReader(InputStreamReader(fileInputStream))
            val sb = StringBuilder()
            while (true) {
                val readLine = bufferedReader.readLine() ?: break
                sb.append(readLine)
                sb.append("\n")
            }
            fileInputStream.close()
            str = sb.toString()
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "FileNotFoundException:$e")
            str = ""
        } catch (e2: IOException) {
            Log.e(TAG, "IOException:$e2")
            str = ""
        }
        loadFromText(str)
    }

    fun loadFromAssets(str: String) {
        try {
            val sb = StringBuilder()
            val bufferedReader = BufferedReader(
                InputStreamReader(
                    context.assets.open(str),
                    StandardCharsets.UTF_8
                )
            )
            while (true) {
                val readLine = bufferedReader.readLine()
                if (readLine != null) {
                    sb.append(readLine).append("\n")
                } else {
                    bufferedReader.close()
                    loadFromText(sb.toString())
                    return
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun loadFromText(str: String) {
        String.format("preview('%s')", escapeForText(imgToBase64(str)))
            .also { this.mPreviewText = it }
        initialize()
    }

    private fun escapeForText(str: String): String {
        return str.replace("\n", "\\\\n").replace("'", "\\'").replace("\r", "")
    }

    private fun imgToBase64(str: String): String {
        val matcher = Pattern.compile(IMAGE_PATTERN).matcher(str)
        if (!matcher.find()) {
            return str
        }
        val group = matcher.group(2) ?: throw AssertionError()
        if (isUrlPrefix(group) || !isPathExCheck(group)) {
            return str
        }
        val imgEx2BaseType = imgEx2BaseType(group)
        if (imgEx2BaseType.isEmpty()) {
            return str
        }
        val file = File(group)
        val bArr = ByteArray(file.length().toInt())
        try {
            BufferedInputStream(FileInputStream(file)).use { bufferedInputStream ->
                bufferedInputStream.read(bArr, 0, bArr.size)
            }
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "FileNotFoundException:$e")
        } catch (e2: IOException) {
            Log.e(TAG, "IOException:$e2")
        }
        return str.replace(group, imgEx2BaseType + Base64.encodeToString(bArr, 2))
    }

    private fun isUrlPrefix(str: String): Boolean {
        return str.startsWith("http://") || str.startsWith("https://")
    }

    private fun isPathExCheck(str: String): Boolean {
        return str.endsWith(".png") || str.endsWith(".jpg") || str.endsWith(".jpeg") || str.endsWith(
            ".gif"
        )
    }

    private fun imgEx2BaseType(str: String): String {
        if (str.endsWith(".png")) {
            return "data:image/png;base64,"
        }
        if (str.endsWith(".jpg") || str.endsWith(".jpeg")) {
            return "data:image/jpg;base64,"
        }
        if (str.endsWith(".gif")) {
            return "data:image/gif;base64,"
        }
        return ""
    }

    companion object {
        private const val IMAGE_PATTERN = "!\\[(.*)]\\((.*)\\)"
        private val TAG: String = MarkdownView::class.java.simpleName
    }
}