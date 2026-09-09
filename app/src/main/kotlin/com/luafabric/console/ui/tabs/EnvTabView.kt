package com.luafabric.console.ui.tabs

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.luafabric.console.core.FileStateTracker
import com.luafabric.console.core.SessionManager
import com.luafabric.console.env.DexLibraries
import com.luafabric.console.env.LuaEnvironment
import com.luafabric.console.env.ModuleTracker
import com.luafabric.console.output.OutputManager
import com.luafabric.console.ui.ConsoleTheme
import com.luafabric.console.ui.ExpandableCard
import com.luafabric.console.ui.dp

/**
 * 环境页：四类别折叠卡——环境信息 / Lua 模块 / 原生库 / Java 类库。
 * 环境信息键值对一行一条；模块/库每项一个折叠卡，卡内为方法签名。
 */
class EnvTabView(context: Context) : ScrollView(context) {

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
    }

    init {
        setBackgroundColor(ConsoleTheme.surface)
        addView(content)
    }

    /** 页签展示时刷新。 */
    fun refresh() {
        content.removeAllViews()
        val file = OutputManager.currentFile
        val luaMods = ModuleTracker.luaLibs(file).filter { !it.native }
        val natives = ModuleTracker.luaLibs(file).filter { it.native }
        val dexLibs = DexLibraries.scan(SessionManager.current?.luaDir)

        // 1. 环境信息（键值对折叠卡，默认展开）
        val envCard = ExpandableCard(context, "环境信息").apply { setExpanded(true) }
        envCard.addBody(kvRow("Lua 版本", LuaEnvironment.versionLabel()))
        envCard.addBody(kvRow("JIT", if (LuaEnvironment.jit) "启用" else "未启用"))
        envCard.addBody(kvRow("当前文件", FileStateTracker.relativePath.ifBlank { "(无)" }))
        envCard.addBody(kvRow("绑定布局", bindLayoutLabel()))
        content.addView(envCard)

        // 2. Lua 模块（自定义 lua 文件模块）
        content.addView(categoryTitle("Lua 模块"))
        if (luaMods.isEmpty()) content.addView(emptyHint("(无)"))
        for (m in luaMods) {
            val card = ExpandableCard(context, m.module)
            for ((fn, np) in m.funcs.entries) {
                card.addBody(sigLine("$fn(${if (np < 0) "?" else np})"))
            }
            addCategoryCard(card)
        }

        // 3. 原生库（全部函数来自 [C] 的 require 模块）
        content.addView(categoryTitle("原生库"))
        if (natives.isEmpty()) content.addView(emptyHint("(无)"))
        for (m in natives) {
            val card = ExpandableCard(context, m.module)
            for ((fn, np) in m.funcs.entries) {
                card.addBody(sigLine("$fn(${if (np < 0) "?" else np})"))
            }
            addCategoryCard(card)
        }

        // 4. Java 类库（仅项目 libs/ 下 .dex 文件）
        content.addView(categoryTitle("Java 类库"))
        if (dexLibs.isEmpty()) content.addView(emptyHint("(无)"))
        for (d in dexLibs) {
            val card = ExpandableCard(context, d.dexFile)
            for (cl in d.classes) {
                card.addBody(classNameLine(cl.className))
                for (sig in cl.methods) {
                    card.addBody(sigLine("  $sig"))
                }
            }
            addCategoryCard(card)
        }
    }

    /** 类别下折叠卡统一加 4dp 上间距（环境信息卡不加，保持顶部贴合）。 */
    private fun addCategoryCard(card: ExpandableCard) {
        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = context.dp(4) }
        )
    }

    /** 绑定布局三态文案：ALY → ./相对路径（layout.aly）；内联布局 / 无布局 → 原标签。 */
    private fun bindLayoutLabel(): String =
        if (FileStateTracker.layout == FileStateTracker.Layout.ALY) {
            "./" + FileStateTracker.alyRelativePath.trimStart('/')
        } else FileStateTracker.layout.label

    /** 键值对样式：一行一条，左键右值，宽度最大。 */
    private fun kvRow(key: String, value: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(2), context.dp(3), context.dp(2), context.dp(3))
            addView(
                TextView(context).apply {
                    text = key
                    textSize = 13f
                    setTextColor(ConsoleTheme.onSurfaceVariant)
                },
                LinearLayout.LayoutParams(context.dp(88), LinearLayout.LayoutParams.WRAP_CONTENT)
            )
            addView(
                TextView(context).apply {
                    text = value
                    textSize = 13f
                    setTextColor(ConsoleTheme.onSurface)
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }

    private fun categoryTitle(text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(ConsoleTheme.onSurfaceVariant)
            setPadding(0, context.dp(10), 0, context.dp(3))
        }

    private fun emptyHint(text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(ConsoleTheme.onSurfaceVariant)
            setPadding(context.dp(12), context.dp(2), context.dp(12), context.dp(2))
        }

    private fun classNameLine(text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(ConsoleTheme.primary)
            setPadding(0, context.dp(4), 0, context.dp(1))
        }

    private fun sigLine(text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(ConsoleTheme.onSurface)
            setPadding(0, context.dp(1), 0, context.dp(1))
        }
}
