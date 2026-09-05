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
    val timestampMs: Long
) {
    val shortTime: String get() = TIME_SHORT.format(Date(timestampMs))
    val fullTime: String get() = TIME_FULL.format(Date(timestampMs))
    val threadLabel: String get() = if (isMainThread) "主" else "子"

    companion object {
        private val TIME_SHORT = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
        private val TIME_FULL = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    }
}
