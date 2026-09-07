package com.luafabric.console.ui.tabs

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.luafabric.console.core.ConsoleSettings
import com.luafabric.console.ui.ConsoleTheme
import com.luafabric.console.ui.dp

/** 设置页：元数据开关 / 解析深度（SharedPreferences 持久化，输出页/适配器实时读取生效）。 */
class SettingsTabView(context: Context) : LinearLayout(context) {

    private val settings = ConsoleSettings(context)
    private var metaValue: TextView? = null
    private var depthValue: TextView? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(ConsoleTheme.surface)
        setPadding(0, context.dp(4), 0, context.dp(12))

        addView(sectionTitle("控制台设置"))

        actionRow(
            label = "元数据展示",
            desc = "开 / 关二级元数据（完整时间 · 线程 · 类型解析）"
        ) {
            settings.showMeta = !settings.showMeta
            refresh()
        }.also { metaValue = it.second }

        actionRow(
            label = "解析深度",
            desc = "1=浅（类名/短预览），2=中（默认），3=深（递归表）"
        ) {
            settings.parseDepth = when (settings.parseDepth) {
                1 -> 2
                2 -> 3
                else -> 1
            }
            refresh()
        }.also { depthValue = it.second }

        addView(TextView(context).apply {
            text = "注：音量 - 键可随时隐藏/显示控制台浮球。"
            textSize = 12f
            setTextColor(ConsoleTheme.onSurfaceVariant)
            setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(4))
        })
    }

    /** 切页/回显时刷新设置行状态。 */
    fun refresh() {
        metaValue?.text = if (settings.showMeta) "开" else "关"
        depthValue?.text = "${settings.parseDepth}"
    }

    private fun sectionTitle(text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(ConsoleTheme.onSurfaceVariant)
            setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(4))
        }

    private fun actionRow(
        label: String,
        desc: String,
        onToggle: () -> Unit
    ): Pair<LinearLayout, TextView> {
        val value = TextView(context).apply {
            textSize = 14f
            setTextColor(ConsoleTheme.primary)
            gravity = Gravity.CENTER_VERTICAL
        }
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
            setOnClickListener { onToggle() }
            addView(
                LinearLayout(context).apply {
                    orientation = VERTICAL
                    addView(TextView(context).apply {
                        text = label
                        textSize = 14f
                        setTextColor(ConsoleTheme.onSurface)
                    })
                    addView(TextView(context).apply {
                        text = desc
                        textSize = 11f
                        setTextColor(ConsoleTheme.onSurfaceVariant)
                    })
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(value)
        }
        addView(divider())
        addView(row)
        return row to value
    }

    private fun divider(): View = View(context).apply {
        setBackgroundColor(ConsoleTheme.onSurfaceVariant and 0x00FFFFFF or 0x1F000000)
    }
}
