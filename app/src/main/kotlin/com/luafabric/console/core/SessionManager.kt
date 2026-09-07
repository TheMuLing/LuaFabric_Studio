package com.luafabric.console.core

import android.app.Activity
import com.luafabric.studio.falling.core.console.SessionInfo
import java.util.WeakHashMap

/**
 * 会话管理器：项目级会话，跨 LuaActivity 存活。
 * - 代次(gen)：重启/重建/文件调起前 prepareNewSession() 递增；旧页迟到的 onDestroy 代次不匹配 → detach 忽略。
 * - 成员集合：activity → 所属代次；集合清空 = 末页销毁 = 会话真正结束。
 */
object SessionManager {

    @Volatile
    var activity: Activity? = null
        private set

    @Volatile
    var current: SessionInfo? = null
        private set

    @Volatile
    var gen: Long = 0
        private set

    private val members = WeakHashMap<Activity, Long>()

    @Volatile
    private var pendingNew = false

    /** 下一次 begin 强制开新代次（F7 重启/重建/文件调起调用）。 */
    @Synchronized
    fun prepareNewSession() {
        pendingNew = true
    }

    /** 开始/加入会话。返回 true = 新代次（首页或重启），false = 加入既有会话。 */
    @Synchronized
    fun begin(info: SessionInfo): Boolean {
        val fresh = pendingNew || current == null
        if (fresh) {
            gen++
            members.clear()
            pendingNew = false
        }
        current = info
        activity = info.activity
        members[info.activity] = gen
        return fresh
    }

    /** 页面销毁。返回 true = 末页（代次匹配且集合清空），会话应结束。 */
    @Synchronized
    fun detach(info: SessionInfo): Boolean {
        val g = members.remove(info.activity)
        if (g == null || g != gen) return false
        return members.isEmpty()
    }

    /** 宿主 Activity 销毁：清引用防 stale token（openSheet BadToken）；下一次 begin/join 立即重建。 */
    @Synchronized
    fun onHostDestroyed(activity: Activity) {
        if (this.activity === activity) this.activity = null
    }

    @Synchronized
    fun end() {
        current = null
        activity = null
        members.clear()
        pendingNew = false
    }
}
