package com.luafabric.studio.falling.ui.components

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.editor.ai.preprocessLatex
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MarkdownDialog(
    mdFileName: String,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    // 状态：Markdown内容
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // 检查文件是否存在
    LaunchedEffect(mdFileName) {
        isLoading = true
        error = null

        try {
            withContext(Dispatchers.IO) {
                // 检查assets中是否存在该文件
                val filePath = "doc/$mdFileName"
                try {
                    context.assets.open(filePath).use {
                        // 文件存在，继续
                    }
                } catch (_: Exception) {
                    error = context.getString(R.string.error_doc_not_found, mdFileName)
                }
            }
        } catch (e: Exception) {
            error = context.getString(R.string.error_check_failed, e.message)
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .shadow(0.dp, MaterialTheme.shapes.extraLarge),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when {
                    isLoading -> {
                        // 加载中
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.loading_doc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }
                    }

                    error != null -> {
                        // 错误状态
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Error,
                                    contentDescription = stringResource(R.string.error),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    text = error ?: stringResource(R.string.unknown_error),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.file_label, mdFileName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }

                    else -> {
                        // 显示MarkdownView
                        MarkdownViewContent(
                            mdFileName = mdFileName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownViewContent(
    mdFileName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var markdownContent by remember { mutableStateOf<String?>(null) }

    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(JLatexMathPlugin.create(
                context.resources.displayMetrics.scaledDensity * 14f,
                { builder -> builder.inlinesEnabled(true) }
            ))
            .build()
    }

    LaunchedEffect(mdFileName) {
        withContext(Dispatchers.IO) {
            try {
                context.assets.open("doc/$mdFileName").bufferedReader().use {
                    markdownContent = it.readText()
                }
            } catch (e: Exception) {
                markdownContent = "**Error:** ${e.message}"
            }
        }
    }

    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        markdownContent?.let { content ->
            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        movementMethod = LinkMovementMethod.getInstance()
                        textSize = 14f
                        includeFontPadding = false
                        setPadding(16, 16, 16, 16)
                    }
                },
                update = { textView ->
                    markwon.setMarkdown(textView, preprocessLatex(content))
                }
            )
        }
    }
}