package com.luafabric.console.ui.tabs

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luafabric.console.core.ConsoleSettings
import com.luafabric.console.core.EventTracker
import com.luafabric.console.output.ClipboardHelper
import com.luafabric.console.output.OutputEntry
import com.luafabric.console.output.OutputExporter
import com.luafabric.console.output.OutputManager
import com.luafabric.console.persist.ConsolePaths
import com.luafabric.console.ConsoleBridgeImpl
import com.luafabric.console.ui.ConsoleTheme
import com.luafabric.console.ui.adapters.OutputAdapter
import com.luafabric.console.ui.dp
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.core.console.DebugConsoleRegistry
import java.io.File
import java.io.FileOutputStream

/** 输出页：当前文件缓冲列表 + 多选复制/导出/清空 + 元数据开关。 */
class OutputTabView(context: Context) : LinearLayout(context), OutputManager.Listener {

    private val settings = ConsoleSettings(context)
    private val adapter = OutputAdapter(settings) { refreshSelectionBar() }
    /** 仅事件模式：列表只显示 runFunc 事件流（原「事件」页合并入输出页）。 */
    private var onlyEvents = false
    private var onlyEventsBtn: ImageButton? = null

    private val titleView = TextView(context)
    private val selBar = LinearLayout(context)
    private val selCount = TextView(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(ConsoleTheme.surface)

        // 当前文件标题：置顶固定，路径过长时中间省略（保留前缀与文件名尾部）
        titleView.apply {
            textSize = 13f
            setTextColor(ConsoleTheme.onSurfaceVariant)
            setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(4))
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        addView(titleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 常规操作栏（置顶居左，纯图标）：仅事件（calendar 切换）+ 清空（trash-can）；无文本开关
        val toolBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.START
            setPadding(context.dp(4), context.dp(2), context.dp(12), context.dp(2))
        }
        val btn = iconButton(R.drawable.ic_calendar_blank, "仅事件：关") {
            onlyEvents = !onlyEvents
            onlyEventsBtn?.let(::syncOnlyEventsIcon)
            refresh()
        }
        onlyEventsBtn = btn
        toolBar.addView(btn)
        toolBar.addView(iconButton(R.drawable.ic_trash_can, "清空") {
            if (onlyEvents) {
                EventTracker.clear()
                refresh()
            } else confirmClear()
        })
        addView(toolBar)

        selBar.orientation = HORIZONTAL
        selBar.gravity = Gravity.CENTER_VERTICAL
        selBar.setPadding(context.dp(12), context.dp(4), context.dp(12), context.dp(4))
        selBar.visibility = View.GONE
        selCount.textSize = 13f
        selBar.addView(selCount, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        selBar.addView(iconButton(R.drawable.ic_select_all, "全选") { adapter.selectAll() })
        selBar.addView(iconButton(R.drawable.ic_select_inverse, "反选") { adapter.invertSelection() })
        selBar.addView(iconButton(R.drawable.ic_select_off, "取消选择") { adapter.clearSelection() })
        selBar.addView(actionText("复制") { copySelected() })
        selBar.addView(actionText("导出") { exportSelected() })
        selBar.addView(actionText("取消") { adapter.setSelectionMode(false) })
        addView(selBar)

        val listHolder = FrameLayout(context)
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@OutputTabView.adapter
        }
        listHolder.addView(list, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        // 列表边缘渐隐（fading edge），上下各 4dp
        val fade = context.dp(4)
        listHolder.addView(View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(ConsoleTheme.surface, ConsoleTheme.surface and 0x00FFFFFF)
            )
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, fade, Gravity.TOP))
        listHolder.addView(View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(ConsoleTheme.surface, ConsoleTheme.surface and 0x00FFFFFF)
            )
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, fade, Gravity.BOTTOM))
        addView(listHolder, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun actionText(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(ConsoleTheme.primary)
            setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
            setOnClickListener { onClick() }
        }

    /** 选择操作栏图标按钮：矢量图标 + primary 着色，禁止文本替代图标。 */
    private fun iconButton(@Suppress("unused") res: Int, desc: String, onClick: () -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageResource(res)
            background = null
            setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
            imageTintList = ColorStateList.valueOf(ConsoleTheme.primary)
            contentDescription = desc
            setOnClickListener { onClick() }
        }

    /** 仅事件开关图标回写：开 calendar-check / 关 calendar-blank（图标切换，非文本标签）。 */
    private fun syncOnlyEventsIcon(btn: ImageButton) {
        btn.setImageResource(if (onlyEvents) R.drawable.ic_calendar_check else R.drawable.ic_calendar_blank)
        btn.contentDescription = "仅事件：${if (onlyEvents) "开" else "关"}"
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
        adapter.submit(
            if (onlyEvents) {
                // 事件流：runFunc 触发记录转输出条目（label=event → chip「事件」；一线为「(函数) 事件监听触发」）
                EventTracker.snapshot().mapIndexed { i, e ->
                    OutputEntry(
                        id = e.timeMs * 10000 + i,
                        file = e.fileLabel,
                        relFile = e.fileLabel,
                        label = "event",
                        primary = "(${e.funcName}) 事件监听触发",
                        luaTypes = emptyList(),
                        typeDetails = listOf(e.argsSummary),
                        isMainThread = e.isMainThread,
                        timestampMs = e.timeMs
                    )
                }
            } else {
                OutputManager.bufferFor(OutputManager.currentFile).all()
            }
        )
    }

    private fun refreshSelectionBar() {
        val n = adapter.selectionCount
        selBar.visibility = if (adapter.selectionMode) View.VISIBLE else View.GONE
        selCount.text = "已选 $n"
    }

    /** 清空确认：MD3 弹窗（主题取色跟随 Luafabric 莫奈），左「全部删除」/中「取消」/右「仅当前文件」。 */
    private fun confirmClear() {
        val message = SpannableString("此操作不可撤销，请谨慎操作").apply {
            setSpan(ForegroundColorSpan(Color.RED), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        try {
            val dlg = MaterialAlertDialogBuilder(context)
                .setTitle("清空控制台输出记录")
                .setMessage(message)
                .setNegativeButton("全部删除") { _, _ -> clearAllRecords() }
                .setNeutralButton("取消", null)
                .setPositiveButton("仅当前文件") { _, _ -> clearCurrentFileRecords() }
                .create()
            dlg.show()
            // MD3 圆角窗体（28dp）背景跟随主题
            dlg.window?.setBackgroundDrawable(
                GradientDrawable().apply {
                    setColor(ConsoleTheme.surface)
                    cornerRadius = context.dp(28).toFloat()
                }
            )
        } catch (e: Exception) {
            // 兜底：Material 主题缺失等极端场景回退系统弹窗，保证功能可用
            AlertDialog.Builder(context)
                .setTitle("清空控制台输出记录")
                .setMessage("此操作不可撤销，请谨慎操作")
                .setNegativeButton("全部删除") { _, _ -> clearAllRecords() }
                .setNeutralButton("取消", null)
                .setPositiveButton("仅当前文件") { _, _ -> clearCurrentFileRecords() }
                .show()
        }
    }

    /** 仅清空当前文件缓冲（其他文件保留）。 */
    private fun clearCurrentFileRecords() {
        OutputManager.clearCurrentFile()
        // E：清空当前文件缓冲 → 未读错误角标一并清零
        (DebugConsoleRegistry.get() as? ConsoleBridgeImpl)?.clearErrorBadge()
    }

    /** 全部删除：清空整个会话缓冲池。 */
    private fun clearAllRecords() {
        OutputManager.clearAll()
        (DebugConsoleRegistry.get() as? ConsoleBridgeImpl)?.clearErrorBadge()
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
