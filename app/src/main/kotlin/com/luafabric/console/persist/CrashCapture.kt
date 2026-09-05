package com.luafabric.console.persist

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃捕获：链式接管默认 UncaughtExceptionHandler，崩溃独立记录至 crash/ 私有目录，
 * 并经 onCrash 回调通知浮球变红；随后仍按原流程终止进程。
 */
object CrashCapture {

    @Volatile
    private var installed = false

    @Volatile
    var onCrash: (() -> Unit)? = null

    fun install() {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrash(thread, throwable)
            onCrash?.invoke()
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(thread: Thread, throwable: Throwable) {
        try {
            val dir = ConsolePaths.crash()
            val file = File(dir, "crash_${System.currentTimeMillis()}.txt")
            file.writeText(
                buildString {
                    append("time: ").append(dateLabel()).append('\n')
                    append("thread: ").append(thread.name).append('\n')
                    appendLine(throwable.toString())
                    for (e in throwable.stackTrace) {
                        append("    at ").appendLine(e.toString())
                    }
                    var cause = throwable.cause
                    var depth = 0
                    while (cause != null && depth < 8) {
                        append("Caused by: ").appendLine(cause.toString())
                        for (e in cause.stackTrace) {
                            append("    at ").appendLine(e.toString())
                        }
                        cause = cause.cause
                        depth++
                    }
                }
            )
        } catch (e: Exception) {
            // 崩溃写入失败不影响主流程
        }
    }

    private fun dateLabel(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
