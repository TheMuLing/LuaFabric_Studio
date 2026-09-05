package com.luafabric.console.core

import android.app.Activity
import com.luafabric.studio.falling.core.console.SessionInfo

/** 会话管理器：跟踪当前调试会话。Activity 一律持为 android.app.Activity。 */
object SessionManager {

    @Volatile
    var activity: Activity? = null
        private set

    @Volatile
    var current: SessionInfo? = null
        private set

    @Synchronized
    fun begin(info: SessionInfo) {
        current = info
        activity = info.activity
    }

    @Synchronized
    fun end() {
        current = null
        activity = null
    }
}
