package com.luafabric.console.ui.tabs

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.luafabric.console.core.SessionManager
import com.luafabric.console.debug.FileLauncher
import com.luafabric.console.ui.ConsoleTheme
import com.luafabric.console.ui.dp

/**
 * F7 调试页：重启项目 / 重建当前文件（原文件树已移入结构页）。
 */
class DebugTabView(context: Context) : ScrollView(context) {

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    init {
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    @SuppressLint("SetTextI18n")
    fun refresh() {
        content.removeAllViews()
        val info = SessionManager.current ?: return
        content.addView(actionRow("重启项目") { FileLauncher.restartProject() })
        content.addView(actionRow("重建当前文件") { FileLauncher.rebuildCurrentFile() })
    }

    private fun actionRow(label: String, onClick: () -> Unit): View =
        TextView(context).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
            setTextColor(ConsoleTheme.onSurface)
            setBackgroundColor(ConsoleTheme.accentContainer)
            setOnClickListener { onClick() }
        }
}
