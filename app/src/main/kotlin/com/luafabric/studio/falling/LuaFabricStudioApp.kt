package com.luafabric.studio.falling

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.androlua.LuaApplication
import com.luafabric.studio.falling.core.OnlinePresence

/**
 * Studio 应用入口：继承 androlua LuaApplication，保持既有运行时初始化不变。
 * 在线状态心跳绑定进程级生命周期：
 *  - 前台：60s 线程持续上报；
 *  - 退后台：注册 WorkManager 15 分钟周期任务兜底（进程被冻结/回收后仍由系统唤醒）；
 *  - 回前台：取消后台周期任务，交回前台线程。
 * 未登录跳过、失败仅日志，静默不打扰。
 */
class LuaFabricStudioApp : LuaApplication() {

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        OnlinePresence.start(this)
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                if (startedActivities == 1) {
                    OnlinePresence.onForeground(this@LuaFabricStudioApp)
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities <= 0) {
                    startedActivities = 0
                    OnlinePresence.onBackground(this@LuaFabricStudioApp)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}