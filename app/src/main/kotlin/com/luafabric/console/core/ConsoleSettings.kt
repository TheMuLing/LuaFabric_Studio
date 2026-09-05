package com.luafabric.console.core

import android.content.Context

/** 控制台设置：元数据开关 / 类型解析深度等（SharedPreferences 持久化）。 */
class ConsoleSettings(context: Context) {

    private val sp = context.getSharedPreferences("luafabric_console", Context.MODE_PRIVATE)

    /** 二级元数据（完整时间/线程/类型解析）默认展开显示。 */
    var showMeta: Boolean
        get() = sp.getBoolean("show_meta", true)
        set(v) = sp.edit().putBoolean("show_meta", v).apply()

    /** 类型解析深度：1=浅（类名/短预览），2=中（默认），>2=深（递归表）。 */
    var parseDepth: Int
        get() = sp.getInt("parse_depth", 2)
        set(v) = sp.edit().putInt("parse_depth", v).apply()
}
