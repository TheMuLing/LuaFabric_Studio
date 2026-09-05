package com.luafabric.console.ui.tabs

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.luafabric.console.core.SessionManager
import com.luafabric.console.debug.FileLauncher
import com.luafabric.console.debug.ParamFormDialog
import com.luafabric.console.debug.ProjectTreeBuilder
import com.luafabric.console.ui.dp
import java.io.File

/**
 * F7 调试页：项目文件树（点文件 → 参数表单 → 带参调起）+ 重启项目 / 重建当前文件。
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

        val nodes = ProjectTreeBuilder.build(info.luaDir, info.luaExtDir)
        if (nodes.isEmpty()) {
            content.addView(TextView(context).apply {
                text = "（项目无可见文件）"
                textSize = 13f
                setPadding(context.dp(8), context.dp(8), 0, 0)
            })
            return
        }
        val luaDir = info.luaDir
        val luaExtDir = info.luaExtDir
        fun render(nodes: List<ProjectTreeBuilder.Node>, depth: Int) {
            for (n in nodes) {
                content.addView(
                    TextView(context).apply {
                        text = "${"  ".repeat(depth)}${if (n.isDir) "▸ " else "· "}${n.name}"
                        textSize = 13f
                        typeface = if (n.isDir) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        setPadding(context.dp(8), context.dp(4), 0, context.dp(4))
                        if (!n.isDir) {
                            setOnClickListener {
                                val file = resolve(n.relativePath, luaDir, luaExtDir)
                                if (file != null) {
                                    ParamFormDialog(context, n.relativePath) { params ->
                                        FileLauncher.launchFile(file, params)
                                    }.show()
                                }
                            }
                        }
                    }
                )
                render(n.children, depth + 1)
            }
        }
        render(nodes, 0)
    }

    private fun resolve(rel: String, luaDir: String?, luaExtDir: String?): File? {
        for (root in listOfNotNull(luaDir, luaExtDir).filter { it.isNotBlank() }) {
            val f = File(File(root), rel)
            if (f.isFile) return f
        }
        return null
    }

    private fun actionRow(label: String, onClick: () -> Unit): View =
        TextView(context).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
            setBackgroundColor(0x22000000.toInt())
            setOnClickListener { onClick() }
        }
}
