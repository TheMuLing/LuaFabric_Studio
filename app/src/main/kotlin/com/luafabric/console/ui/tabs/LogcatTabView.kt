package com.luafabric.console.ui.tabs

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luafabric.console.logcat.LogcatManager
import com.luafabric.console.ui.dp

/**
 * Logcat 页：文件懒加载分块读取 + RecyclerView 虚拟化渲染（仅渲染可见行），
 * 「仅看错误」过滤 + 后台轮询增量刷新。
 */
class LogcatTabView(context: Context) : LinearLayout(context) {

    private val lines = ArrayList<String>()
    private val visible = ArrayList<String>()
    private var onlyErrors = false

    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            refresh()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val toggle = TextView(context)
    private val status = TextView(context)
    private val emptyHint = TextView(context)

    private val adapter = object : RecyclerView.Adapter<RowHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder =
            RowHolder(TextView(parent.context).apply {
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(0xFF444444.toInt())
                setLineSpacing(0f, 1.05f)
            })

        override fun getItemCount(): Int = visible.size

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            holder.textView.text = visible[position]
        }
    }

    private class RowHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    private val recycler = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        adapter = this@LogcatTabView.adapter
    }

    init {
        orientation = VERTICAL
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        setBackgroundColor(Color.WHITE)

        val toolBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolBar.addView(
            TextView(context).apply {
                text = "Logcat"
                textSize = 13f
                setTextColor(0xFF222222.toInt())
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        toggle.apply {
            text = "仅看错误"
            textSize = 12f
            setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4))
            setTextColor(0xFF3F51B5.toInt())
            setOnClickListener {
                onlyErrors = !onlyErrors
                toggle.setTextColor(if (onlyErrors) 0xFFE53935.toInt() else 0xFF3F51B5.toInt())
                rebuildVisible()
            }
        }
        toolBar.addView(toggle)
        addView(toolBar)

        status.apply {
            textSize = 11f
            setTextColor(0xFF999999.toInt())
            setPadding(0, context.dp(2), 0, context.dp(6))
        }
        addView(status)

        emptyHint.apply {
            text = "(无记录)"
            textSize = 13f
            setTextColor(0xFF999999.toInt())
            gravity = Gravity.CENTER
        }
        addView(
            emptyHint,
            LayoutParams(LayoutParams.MATCH_PARENT, context.dp(120))
        )
        addView(recycler, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** 页签展示时刷新并启动轮询。 */
    fun refresh() {
        val store = LogcatManager.store
        if (store == null) {
            status.text = "(未记录)"
            emptyHint.visibility = if (lines.isEmpty()) View.VISIBLE else View.GONE
            return
        }
        status.text = store.file().name
        val chunk = store.readChunk(CHUNK_LINES)
        if (chunk.isNotEmpty()) {
            lines.addAll(chunk)
            if (lines.size > MAX_LINES) {
                val drop = lines.size - MAX_LINES
                repeat(drop) { lines.removeAt(0) }
            }
            rebuildVisible()
            // 已在底部时跟随新行
            if (!recycler.canScrollVertically(1)) {
                recycler.scrollToPosition(adapter.itemCount - 1)
            }
        }
        emptyHint.visibility = if (lines.isEmpty()) View.VISIBLE else View.GONE
    }

    fun startPolling() {
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
    }

    fun stopPolling() {
        handler.removeCallbacks(pollRunnable)
    }

    private fun rebuildVisible() {
        visible.clear()
        if (onlyErrors) {
            for (l in lines) if (isErrorLine(l)) visible.add(l)
        } else {
            visible.addAll(lines)
        }
        adapter.notifyDataSetChanged()
    }

    private fun isErrorLine(line: String): Boolean =
        line.contains(" E ") || line.contains(" E/")

    companion object {
        private const val POLL_MS = 1000L
        private const val CHUNK_LINES = 300
        private const val MAX_LINES = 5000
    }
}
