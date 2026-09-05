package com.luafabric.console.ui.tabs

import android.content.Context
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.luafabric.console.core.FileStateTracker
import com.luafabric.console.env.LuaEnvironment
import com.luafabric.console.env.ModuleTracker
import com.luafabric.console.output.OutputManager
import com.luafabric.console.ui.dp

/** 环境页：Lua 版本/JIT + 当前文件的 Java 库（反射签名）与 C/Lua 库（函数名+参数个数）。 */
class EnvTabView(context: Context) : ScrollView(context) {

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
    }

    private val envValue = TextView(context)
    private val fileValue = TextView(context)
    private val libsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        setBackgroundColor(Color.WHITE)
        content.addView(header("Lua 环境"))
        content.addView(envValue.apply { textSize = 14f; setTextColor(0xFF222222.toInt()) })
        content.addView(header("当前文件"))
        content.addView(fileValue.apply { textSize = 14f; setTextColor(0xFF222222.toInt()) })
        content.addView(header("native 库"))
        content.addView(libsContainer)
        addView(content)
    }

    /** 页签展示时刷新（require/bindClass 随运行变化）。 */
    fun refresh() {
        envValue.text = LuaEnvironment.versionLabel()
        fileValue.text = FileStateTracker.relativePath.ifBlank { "(无)" }
        libsContainer.removeAllViews()
        val file = OutputManager.currentFile
        val javaLibs = ModuleTracker.javaLibs(file)
        val luaLibs = ModuleTracker.luaLibs(file)
        if (javaLibs.isEmpty() && luaLibs.isEmpty()) {
            libsContainer.addView(
                TextView(context).apply {
                    text = "(无)"
                    textSize = 13f
                    setTextColor(0xFF999999.toInt())
                }
            )
            return
        }
        for (lib in luaLibs) {
            libsContainer.addView(libHeader("Lua/C · ${lib.module}"))
            for ((fn, np) in lib.funcs.entries) {
                libsContainer.addView(libLine("  $fn(${if (np < 0) "?" else np})"))
            }
        }
        for (lib in javaLibs) {
            libsContainer.addView(libHeader("Java · ${lib.className}"))
            for (sig in lib.methods) {
                libsContainer.addView(libLine("  $sig"))
            }
        }
    }

    private fun header(text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            setPadding(0, context.dp(6), 0, context.dp(2))
        }

    private fun libHeader(text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(0xFF3F51B5.toInt())
            setPadding(0, context.dp(6), 0, context.dp(2))
        }

    private fun libLine(text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFF333333.toInt())
        }
}
