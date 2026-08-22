package com.luafabric.studio.falling.ui.editor.ai

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin

@Composable
fun AiMessageContent(
    content: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(JLatexMathPlugin.create(
                context.resources.displayMetrics.scaledDensity * 14f,
                { builder -> builder.inlinesEnabled(true) }
            ))
            .build()
    }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                textSize = 14f
                maxLines = Int.MAX_VALUE
                // TableRowSpan sets fm.ascent/descent manually; default font padding
                // pushes cell text downward out of the row bounds
                includeFontPadding = false
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, preprocessLatex(content))
        },
        modifier = modifier
    )
}

/**
 * JLatexMathPlugin only recognizes `$$...$$` delimiters. Convert the common
 * `\(...\)` (inline) and `\[...\]` (block) LaTeX delimiters to that form.
 */
internal fun preprocessLatex(content: String): String {
    val blockPattern = Regex("""\\\[\s*(.*?)\s*\\\]""", RegexOption.DOT_MATCHES_ALL)
    val result = blockPattern.replace(content) { m ->
        "$$\n${m.groupValues[1]}\n$$"
    }
    val inlinePattern = Regex("""\\\(\s*(.*?)\s*\\\)""", RegexOption.DOT_MATCHES_ALL)
    return inlinePattern.replace(result) { m ->
        "\$\$${m.groupValues[1]}\$\$"
    }
}