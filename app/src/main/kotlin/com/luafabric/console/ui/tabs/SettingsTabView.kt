package com.luafabric.console.ui.tabs

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.luafabric.console.core.ConsoleSettings
import com.luafabric.console.ui.ConsoleTheme
import com.luafabric.console.ui.dp
import com.luafabric.studio.falling.core.console.DebugConsoleRegistry

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

        // E：Lua 侧报错 Toast 回显（默认关）。无论开关，报错恒入 F1 缓冲 + 浮球右上角角标。
        switchRow(
            label = "使用 Toast 输出 Lua 侧错误",
            desc = "默认关：报错仅入控制台输出并显示角标；开启后同时以 Toast 回显",
            initial = settings.toastLuaErrors
        ) { on ->
            settings.toastLuaErrors = on
            DebugConsoleRegistry.setErrorToastEnabled(on) // 同步 core：LuaActivity.sendError 按此门控 toast
            refresh()
        }

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

    private fun switchRow(
        label: String,
        desc: String,
        initial: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val toggle = MaterialSwitch(context).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
            setOnClickListener { toggle.toggle() } // 点行等价于点开关
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
            addView(toggle)
        }
        addView(divider())
        addView(row)
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
