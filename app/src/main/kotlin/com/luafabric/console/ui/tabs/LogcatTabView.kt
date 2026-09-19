package com.luafabric.console.ui.tabs

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luafabric.console.logcat.LogcatManager
import com.luafabric.console.ui.ConsoleTheme
import com.luafabric.console.ui.dp

/**
 * Logcat 页：7 种日志等级（V/D/I/W/E/F/S）各自横向开关过滤 + RecyclerView 虚拟化渲染。
 * 文件分块懒加载读取移至后台线程（readChunk 是逐字节 RandomAccessFile IO），
 * 读到增量后主线程只 append 可见行，避免整表 notifyDataSetChanged → 消除打开/轮询卡顿。
 */
class LogcatTabView(context: Context) : LinearLayout(context) {

    private val lines = ArrayList<String>()
    private val visible = ArrayList<String>()
    /** Android Log 等级按序 V D I W E F S，index 对应；true=显示（默认全开）。 */
    private val levelEnabled = BooleanArray(LEVELS.size) { true }

    private val toggles = ArrayList<TextView>(LEVELS.size)

    private val worker = HandlerThread("logcat-poll").apply { start() }
    private val workerHandler = Handler(worker.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var attached = false

    private val levelRegex = Regex("\\s+\\d+\\s+\\d+\\s+([VDIWEFS])\\s")

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!attached) return // 页已离屏：停止轮询
            val store = LogcatManager.store
            if (store == null) {
                mainHandler.post { updateStatus("(未记录)") }
            } else {
                val chunk = try {
                    store.readChunk(CHUNK_LINES)
                } catch (_: Exception) {
                    emptyList()
                }
                mainHandler.post {
                    if (attached) append(chunk)
                }
            }
            if (attached) workerHandler.postDelayed(this, POLL_MS)
        }
    }

    private val status = TextView(context)
    private val emptyHint = TextView(context)

    private val adapter = object : RecyclerView.Adapter<RowHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val tv = TextView(parent.context).apply {
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(ConsoleTheme.onSurfaceVariant)
                setLineSpacing(0f, 1.05f)
                isClickable = true
                // 行波纹：主题 onSurface 低透明 ripple，随主题色
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(
                        ConsoleTheme.onSurface and 0x00FFFFFF or 0x1AFFFFFF
                    ),
                    null,
                    null
                )
                setOnClickListener { onRowClick(it as TextView) }
            }
            return RowHolder(tv)
        }

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
        setBackgroundColor(ConsoleTheme.surface)

        // 「日志输出：」标题 + 7 个等级开关同一行，开关居右
        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, context.dp(4))
        }
        headerRow.addView(
            TextView(context).apply {
                text = "日志输出："
                textSize = 13f
                setTextColor(ConsoleTheme.onSurface)
            },
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )
        for (i in LEVELS.indices) {
            val tb = TextView(context).apply {
                text = LEVELS[i].toString()
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setOnClickListener { onToggle(i) }
            }
            // 固定正方形容器，格间 4dp
            headerRow.addView(
                tb, LayoutParams(context.dp(26), context.dp(26)).apply { marginStart = context.dp(4) }
            )
            styleToggle(tb, i)
            toggles.add(tb)
        }
        addView(headerRow)

        status.apply {
            textSize = 11f
            setTextColor(ConsoleTheme.onSurfaceVariant)
            setPadding(0, context.dp(2), 0, context.dp(6))
        }
        addView(status)

        emptyHint.apply {
            text = "(无记录)"
            textSize = 13f
            setTextColor(ConsoleTheme.onSurfaceVariant)
            gravity = Gravity.CENTER
        }
        addView(emptyHint, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(120)))
        addView(recycler, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        updateStatus(null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
    }

    override fun onDetachedFromWindow() {
        attached = false
        workerHandler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    /** 等级开关点击：翻转 + 重跑过滤。 */
    private fun onToggle(i: Int) {
        levelEnabled[i] = !levelEnabled[i]
        styleToggle(toggles[i], i)
        rebuildAll()
        if (!recycler.canScrollVertically(1)) recycler.scrollToPosition(adapter.itemCount - 1)
    }

    /** 开关外观：选中 accentContainer 底 + onSurface 字；未选 surfaceContainer 底 + onSurfaceVariant 字。 */
    private fun styleToggle(tb: TextView, i: Int) {
        val on = levelEnabled[i]
        tb.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (on) ConsoleTheme.accentContainer else ConsoleTheme.surfaceContainer)
            cornerRadius = ConsoleTheme.cornerRadiusPx
        }
        tb.setTextColor(if (on) ConsoleTheme.onSurface else ConsoleTheme.onSurfaceVariant)
    }

    /** 页签展示时刷新并启动轮询。 */
    fun refresh() {
        attached = true
        workerHandler.removeCallbacks(pollRunnable)
        workerHandler.post(pollRunnable)
    }

    fun startPolling() {
        workerHandler.removeCallbacks(pollRunnable)
        workerHandler.post(pollRunnable)
    }

    fun stopPolling() {
        workerHandler.removeCallbacks(pollRunnable)
    }

    /** 主线程：追加后台读到的新块，仅插入可见行，避免整表刷新。 */
    private fun append(chunk: List<String>) {
        if (chunk.isEmpty()) {
            updateStatus(null)
            return
        }
        lines.addAll(chunk)
        if (lines.size > MAX_LINES) {
            // 超上限：整段重建（低频，仅超限点触发）
            repeat(lines.size - MAX_LINES) { lines.removeAt(0) }
            rebuildAll()
        } else {
            val start = visible.size
            for (l in chunk) if (passes(l)) visible.add(l)
            if (visible.size > start) {
                adapter.notifyItemRangeInserted(start, visible.size - start)
            }
        }
        if (!recycler.canScrollVertically(1)) recycler.scrollToPosition(adapter.itemCount - 1)
        updateStatus(null)
    }

    /** 行点击仅触发波纹反馈，暂不承载具体动作。 */
    private fun onRowClick(row: TextView) = Unit

    /** 等级过滤变化：全量重建可见集。 */
    private fun rebuildAll() {
        visible.clear()
        for (l in lines) if (passes(l)) visible.add(l)
        adapter.notifyDataSetChanged()
        emptyHint.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
    }

    /** 等级过滤：threadtime 格式 `time pid tid 级 标签: 消息`，取 pid/tid 后的单字母等级。 */
    private fun passes(line: String): Boolean {
        val m = levelRegex.find(line) ?: return true // 非标准行（如二进制流碎片）恒显示
        val idx = LEVELS.indexOf(m.groupValues[1][0])
        return idx < 0 || levelEnabled[idx]
    }

    private fun updateStatus(s: String?) {
        status.text = s ?: LogcatManager.store?.file()?.name ?: "(未记录)"
        emptyHint.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
    }

    companion object {
        /** Android Log 优先级字母：V=Verbose, D=Debug, I=Info, W=Warn, E=Error, F=Fatal, S=Silent。 */
        private val LEVELS = charArrayOf('V', 'D', 'I', 'W', 'E', 'F', 'S')
        private const val POLL_MS = 1000L
        private const val CHUNK_LINES = 300
        private const val MAX_LINES = 5000
    }
}