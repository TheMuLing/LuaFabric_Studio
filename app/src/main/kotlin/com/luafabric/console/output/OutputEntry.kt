package com.luafabric.console.output

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 单条输出条目。
 *
 * 一级常显：内容 + 类型小标签 + 简略时间（MM-dd HH:mm:ss）。
 * 二级（默认折叠）：完整时间（MM-dd HH:mm:ss.SSS，不含年份）+ 线程（主/子）+ lua 类型解析。
 */
class OutputEntry(
    val id: Long,
    val file: String,
    val label: String,
    val primary: String,
    val luaTypes: List<String>,
    val typeDetails: List<String>,
    val isMainThread: Boolean,
    val timestampMs: Long,
    /** 创建时刻的项目相对路径（展示用，区别于 buffer 键 file）。 */
    val relFile: String = "",
    /** 事件条目的函数名：非空则正文行首渲染独立药丸 chip（(函数名) + 事件监听触发）。 */
    val eventFunc: String? = null,
    /** 完整内容（不截断，供复制选项弹窗预览；primary 为列表展示截断版）。 */
    val fullText: String? = null
) {
    val shortTime: String get() = TIME_SHORT.format(Date(timestampMs))
    val fullTime: String get() = TIME_FULL.format(Date(timestampMs))
    val threadLabel: String get() = if (isMainThread) "主" else "子"

    companion object {
        private val TIME_SHORT = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
        private val TIME_FULL = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    }
}
