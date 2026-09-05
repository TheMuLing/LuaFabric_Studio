package com.luafabric.console.ui.tabs

import android.content.Context
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.luafabric.console.core.EventTracker
import com.luafabric.console.ui.dp

/** 事件页：runFunc 显式调用且实际触发的条目列表（相对路径 + 毫秒时间 + 参数摘要）。 */
class EventTabView(context: Context) : ScrollView(context) {

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
    }

    init {
        setBackgroundColor(Color.WHITE)
        addView(content)
    }

    /** 页签展示时刷新。 */
    fun refresh() {
        content.removeAllViews()
        val events = EventTracker.snapshot()
        if (events.isEmpty()) {
            content.addView(
                TextView(context).apply {
                    text = "(无事件)"
                    textSize = 13f
                    setTextColor(0xFF999999.toInt())
                }
            )
            return
        }
        for (e in events) {
            content.addView(
                TextView(context).apply {
                    text = "[${e.timeLabel()}] ${e.funcName}"
                    textSize = 13f
                    setTextColor(0xFF222222.toInt())
                    setPadding(0, context.dp(8), 0, context.dp(2))
                }
            )
            content.addView(
                TextView(context).apply {
                    text = e.argsSummary
                    textSize = 12f
                    setTextColor(0xFF666666.toInt())
                }
            )
            content.addView(
                TextView(context).apply {
                    text = "${e.fileLabel} · ${if (e.isMainThread) "主线程" else "子线程"}"
                    textSize = 11f
                    setTextColor(0xFF999999.toInt())
                }
            )
        }
    }
}
