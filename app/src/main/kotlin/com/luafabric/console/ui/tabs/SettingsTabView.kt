package com.luafabric.console.ui.tabs

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.luafabric.console.ConsoleBridgeImpl
import com.luafabric.console.core.ConsoleSettings
import com.luafabric.console.ui.ConsoleTheme
import com.luafabric.console.ui.ExpandableCard
import com.luafabric.console.ui.dp
import com.luafabric.console.ui.themeSwitch
import com.luafabric.studio.falling.core.console.DebugConsoleRegistry

/**
 * 设置页：折叠卡片分组（默认全折叠）——输出 / 拦截 / 报错。
 * 与环境页同款 ExpandableCard；SharedPreferences 持久化，切页实时回显。
 */
class SettingsTabView(context: Context) : ScrollView(context) {

    private val settings = ConsoleSettings(context)
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
    }
    private var metaValue: TextView? = null
    private var depthValue: TextView? = null
    private var toastSwitch: MaterialSwitch? = null
    private var interceptSwitch: MaterialSwitch? = null

    init {
        setBackgroundColor(ConsoleTheme.surface)
        addView(content)

        // 输出
        content.addView(ExpandableCard(context, "输出").apply {
            addBody(actionRow("元数据展示", "开 / 关二级元数据（完整时间 · 线程 · 类型解析）") {
                settings.showMeta = !settings.showMeta
                refresh()
            }.also { metaValue = it.second }.first)
            addBody(divider())
            addBody(actionRow("解析深度", "1=浅（类名/短预览），2=中（默认），3=深（递归表）") {
                settings.parseDepth = when (settings.parseDepth) {
                    1 -> 2
                    2 -> 3
                    else -> 1
                }
                refresh()
            }.also { depthValue = it.second }.first)
        })

        // 拦截
        content.addView(ExpandableCard(context, "拦截").apply {
            addBody(switchRow(
                "拦截界面跳转/结束请求",
                "newActivity 确认弹窗 + finish 挂起均由本开关控制，默认开",
                initial = settings.interceptNavigation
            ) { on ->
                settings.interceptNavigation = on
                (DebugConsoleRegistry.get() as? ConsoleBridgeImpl)?.setInterceptEnabled(on) // 即时生效
            }.also { interceptSwitch = it.second }.first)
        })

        // 报错
        content.addView(ExpandableCard(context, "报错").apply {
            addBody(switchRow(
                "使用 Toast 输出 Lua 侧错误",
                "默认关：报错仅入控制台输出并显示角标；开启后同时以 Toast 回显",
                initial = settings.toastLuaErrors
            ) { on ->
                settings.toastLuaErrors = on
                DebugConsoleRegistry.setErrorToastEnabled(on) // 同步 core：LuaActivity.sendError 按此门控 toast
            }.also { toastSwitch = it.second }.first)
        })

        content.addView(TextView(context).apply {
            text = "注：音量 - 键可随时隐藏/显示控制台浮球。"
            textSize = 12f
            setTextColor(ConsoleTheme.onSurfaceVariant)
            setPadding(context.dp(4), context.dp(12), context.dp(4), context.dp(4))
        })
    }

    /** 切页/回显时刷新设置行状态。 */
    fun refresh() {
        metaValue?.text = if (settings.showMeta) "开" else "关"
        depthValue?.text = "${settings.parseDepth}"
        toastSwitch?.isChecked = settings.toastLuaErrors
        interceptSwitch?.isChecked = settings.interceptNavigation
    }

    /** 折叠卡内设置项行：左侧文本 + 右侧开关。 */
    private fun switchRow(
        label: String,
        desc: String,
        initial: Boolean,
        onChange: (Boolean) -> Unit
    ): Pair<LinearLayout, MaterialSwitch> {
        val toggle = MaterialSwitch(context).apply {
            isChecked = initial
            themeSwitch() // 颜色跟随 luafabric 主题
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(8), context.dp(10), context.dp(8), context.dp(10))
            setOnClickListener { toggle.toggle() } // 点行等价于点开关
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
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
        return row to toggle // 行由调用方 addBody 进卡片，开关随行带回显
    }

    /** 折叠卡内设置项行：左侧文本 + 右侧文本值（点击切换）。返回 (row, valueView)。 */
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
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(8), context.dp(10), context.dp(8), context.dp(10))
            setOnClickListener { onToggle() }
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
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
        return row to value
    }

    private fun divider(): View = View(context).apply {
        setBackgroundColor(ConsoleTheme.onSurfaceVariant and 0x00FFFFFF or 0x1F000000)
    }
}