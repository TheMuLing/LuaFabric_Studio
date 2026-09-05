package com.luafabric.console.ui.tabs

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luafabric.console.core.ConsoleSettings
import com.luafabric.console.output.ClipboardHelper
import com.luafabric.console.output.OutputExporter
import com.luafabric.console.output.OutputManager
import com.luafabric.console.persist.ConsolePaths
import com.luafabric.console.ui.adapters.OutputAdapter
import com.luafabric.console.ui.dp
import java.io.File
import java.io.FileOutputStream

/** 输出页：当前文件缓冲列表 + 多选复制/导出/清空 + 元数据开关。 */
class OutputTabView(context: Context) : LinearLayout(context), OutputManager.Listener {

    private val settings = ConsoleSettings(context)
    private val adapter = OutputAdapter(settings) { refreshSelectionBar() }

    private val titleView = TextView(context)
    private val selBar = LinearLayout(context)
    private val selCount = TextView(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)

        titleView.apply {
            textSize = 13f
            setTextColor(0xFF444444.toInt())
            setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6))
            maxLines = 1
        }
        addView(titleView)

        // 常规操作栏：元数据开关 + 清空当前缓冲
        val toolBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(12), context.dp(2), context.dp(12), context.dp(2))
        }
        toolBar.addView(actionText("元数据：${if (settings.showMeta) "开" else "关"}") {
            settings.showMeta = !settings.showMeta
            (toolBar.getChildAt(0) as TextView).text = "元数据：${if (settings.showMeta) "开" else "关"}"
            adapter.setShowMeta(settings.showMeta)
        })
        toolBar.addView(actionText("清空") { confirmClear() })
        addView(toolBar)

        // 选择操作栏（默认隐藏）
        selBar.orientation = HORIZONTAL
        selBar.gravity = Gravity.CENTER_VERTICAL
        selBar.setPadding(context.dp(12), context.dp(4), context.dp(12), context.dp(4))
        selBar.visibility = View.GONE
        selCount.textSize = 13f
        selBar.addView(selCount, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        selBar.addView(actionText("复制") { copySelected() })
        selBar.addView(actionText("导出") { exportSelected() })
        selBar.addView(actionText("完成") { adapter.setSelectionMode(false) })
        addView(selBar)

        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@OutputTabView.adapter
        }
        addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun actionText(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(0xFF2962FF.toInt())
            setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
            setOnClickListener { onClick() }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        OutputManager.addListener(this)
        refresh()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        OutputManager.removeListener(this)
    }

    override fun onOutputsChanged() {
        post { refresh() }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refresh() {
        titleView.text = "当前文件：${OutputManager.currentFile.ifBlank { "(无)" }}"
        adapter.submit(OutputManager.bufferFor(OutputManager.currentFile).all())
    }

    private fun refreshSelectionBar() {
        val n = adapter.selectionCount
        selBar.visibility = if (adapter.selectionMode) View.VISIBLE else View.GONE
        selCount.text = "已选 $n"
    }

    private fun confirmClear() {
        AlertDialog.Builder(context)
            .setTitle("清空当前缓冲")
            .setMessage("仅清空当前文件「${OutputManager.currentFile.ifBlank { "(无)" }}」的输出缓冲，其他文件保留。")
            .setPositiveButton("清空") { _, _ -> OutputManager.clearCurrentFile() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun copySelected() {
        val entries = adapter.selectedEntries
        if (entries.isEmpty()) {
            Toast.makeText(context, "先长按选择条目", Toast.LENGTH_SHORT).show()
            return
        }
        ClipboardHelper.copy(context, OutputExporter.export(entries))
        adapter.setSelectionMode(false)
    }

    private fun exportSelected() {
        val entries = adapter.selectedEntries
        if (entries.isEmpty()) {
            Toast.makeText(context, "先长按选择条目", Toast.LENGTH_SHORT).show()
            return
        }
        val dir = ConsolePaths.outputs()
        val f = File(dir, "console_export_${System.currentTimeMillis()}.txt")
        try {
            FileOutputStream(f).use { it.write(OutputExporter.export(entries).toByteArray(Charsets.UTF_8)) }
            Toast.makeText(context, "已导出：${f.absolutePath}", Toast.LENGTH_LONG).show()
            adapter.setSelectionMode(false)
        } catch (e: Exception) {
            Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
