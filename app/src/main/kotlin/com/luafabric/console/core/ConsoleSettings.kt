package com.luafabric.console.core

import android.content.Context

/** 控制台设置：类型解析深度等（SharedPreferences 持久化）。 */
class ConsoleSettings(context: Context) {

    private val sp = context.getSharedPreferences("luafabric_console", Context.MODE_PRIVATE)

    /** 类型解析深度：1=浅（类名/短预览），2=中（默认），>2=深（递归表）。 */
    var parseDepth: Int
        get() = sp.getInt("parse_depth", 2)
        set(v) = sp.edit().putInt("parse_depth", v).apply()

    /** 是否已首次完全关闭控制台（用于只提示一次音量键恢复）。 */
    var firstCloseDone: Boolean
        get() = sp.getBoolean("first_close_done", false)
        set(v) = sp.edit().putBoolean("first_close_done", v).apply()

    /** Lua 侧报错是否以 Toast 回显（默认关；无论开关，报错恒入 F1 缓冲 + 浮球角标）。 */
    var toastLuaErrors: Boolean
        get() = sp.getBoolean("toast_lua_errors", false)
        set(v) = sp.edit().putBoolean("toast_lua_errors", v).apply()

    /** 拦截界面跳转/结束请求（newActivity/finish 拦截确认总开关），默认开。 */
    var interceptNavigation: Boolean
        get() = sp.getBoolean("intercept_navigation", true)
        set(v) = sp.edit().putBoolean("intercept_navigation", v).apply()

    /** 捕获 print() 内容入控制台缓冲（默认开；关闭后不再写入，render 主线程早期输出不受影响）。 */
    var capturePrint: Boolean
        get() = sp.getBoolean("capture_print", true)
        set(v) = sp.edit().putBoolean("capture_print", v).apply()

    /** 捕获 Toast 内容入控制台缓冲（默认开；不拦截原显示，make/show 均捕获，标注是否调用 show）。 */
    var captureToast: Boolean
        get() = sp.getBoolean("capture_toast", true)
        set(v) = sp.edit().putBoolean("capture_toast", v).apply()

    /** 捕获 Snackbar 内容入控制台缓冲（默认关；不拦截原显示，make/show 均捕获，标注是否调用 show）。 */
    var captureSnackbar: Boolean
        get() = sp.getBoolean("capture_snackbar", false)
        set(v) = sp.edit().putBoolean("capture_snackbar", v).apply()
}
