package com.luafabric.console.logcat

import android.os.Process
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 后台常驻 logcat 捕获：调试运行起至停止止，写入
 * logcat_<项目名>_<毫秒时间戳>.log（--pid=<own> 自读，尽力而为）。
 */
class LogcatCapture(private val dir: File, private val projectName: String) {

    @Volatile
    var store: LogcatFileStore? = null
        private set

    @Volatile
    private var process: java.lang.Process? = null

    @Volatile
    private var thread: Thread? = null

    fun start() {
        if (process != null) return
        val file = File(dir, "logcat_${projectName}_${System.currentTimeMillis()}.log")
        store = LogcatFileStore(file)
        try {
            val p = ProcessBuilder(
                "logcat", "-v", "threadtime",
                "--pid=${Process.myPid()}"
            ).redirectErrorStream(true).start()
            process = p
            thread = Thread {
                val writer = file.bufferedWriter(Charsets.UTF_8)
                try {
                    val reader = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8))
                    while (true) {
                        val line = reader.readLine() ?: break
                        writer.write(line)
                        writer.write("\n")
                        writer.flush()
                    }
                } catch (e: Exception) {
                    // 进程被杀/IO 中断：尽力而为，不阻断会话
                } finally {
                    try {
                        writer.close()
                    } catch (e: Exception) {
                    }
                }
            }.apply {
                isDaemon = true
                name = "logcat-capture"
            }
            thread?.start()
        } catch (e: Exception) {
            process = null
            thread = null
            store = null
        }
    }

    fun stop() {
        thread?.interrupt()
        try {
            process?.destroy()
        } catch (e: Exception) {
        }
        process = null
        thread = null
    }
}
