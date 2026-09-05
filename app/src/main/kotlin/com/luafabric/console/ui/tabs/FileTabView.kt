package com.luafabric.console.ui.tabs

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.luafabric.console.core.FileStateTracker
import com.luafabric.console.output.ClipboardHelper
import com.luafabric.console.ui.dp

/** 文件页：当前文件相对路径（复制）+ 布局三态（aly 相对路径 | 内联布局 | 无布局）。 */
class FileTabView(context: Context) : LinearLayout(context) {

    private val fileValue = TextView(context)
    private val layoutValue = TextView(context)

    init {
        orientation = VERTICAL
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        setBackgroundColor(Color.WHITE)

        addView(header("当前文件"))
        addView(valueRow(fileValue) { FileStateTracker.relativePath })
        addView(header("布局"))
        addView(valueRow(layoutValue) { layoutText() })

        val hint = TextView(context).apply {
            text = "点击条目复制"
            textSize = 11f
            setTextColor(0xFFBBBBBB.toInt())
        }
        addView(hint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = context.dp(8)
        })
    }

    /** 页签每次展示时刷新（布局状态随 setContentView 观察变化）。 */
    fun refresh() {
        fileValue.text = FileStateTracker.relativePath.ifBlank { "(无)" }
        layoutValue.text = layoutText()
    }

    private fun layoutText(): String {
        val s = FileStateTracker
        return when (s.layout) {
            FileStateTracker.Layout.ALY -> s.alyRelativePath
            else -> s.layout.label
        }
    }

    private fun header(text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            setPadding(0, context.dp(6), 0, context.dp(2))
        }

    private fun valueRow(view: TextView, source: () -> String): LinearLayout =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            view.apply {
                textSize = 14f
                setTextColor(0xFF222222.toInt())
            }
            addView(view, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener {
                val v = source()
                if (v.isNotBlank()) {
                    ClipboardHelper.copy(context, v)
                } else {
                    Toast.makeText(context, "暂无内容", Toast.LENGTH_SHORT).show()
                }
            }
        }
}
