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

    /** 宿主 Activity 销毁：清 stale 引用。若销毁的正是当前活动页，则切换至尚存活的成员页，
     *  保证浮球/面板重建能取得有效宿主（否则 hostActivity()==null → 重建被短路，回旧页浮球丢失）。 */
    @Synchronized
    fun onHostDestroyed(activity: Activity) {
        if (this.activity !== activity) return
        this.activity = members.keys.firstOrNull {
            it !== activity && !it.isDestroyed && !it.isFinishing
        }
    }

    /** 页面恢复：该页属当前代次且存活 → 设为当前宿主。消除「detach 后、destroy 回调前」窗口期
     *  宿主仍指向已销毁页导致的 openSheet 被 isDestroyed 短路（需点两次浮球才开面板）。 */
    @Synchronized
    fun onHostResumed(activity: Activity) {
        if (members[activity] == gen && !activity.isDestroyed && !activity.isFinishing) {
            this.activity = activity
        }
    }

    @Synchronized
    fun end() {
        current = null
        activity = null
        members.clear()
        pendingNew = false
    }
}
