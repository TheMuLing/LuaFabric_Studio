package com.luafabric.console.logcat

import com.luafabric.console.persist.ConsolePaths

/** 会话级 logcat 管理器：起止生命周期由控制台桥驱动。 */
object LogcatManager {

    @Volatile
    private var capture: LogcatCapture? = null

    val store: LogcatFileStore? get() = capture?.store

    fun start(projectName: String) {
        stop()
        val c = LogcatCapture(ConsolePaths.logcat(), projectName)
        c.start()
        capture = c
    }

    fun stop() {
        capture?.stop()
        capture = null
    }
}
