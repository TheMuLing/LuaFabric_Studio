package com.luafabric.studio.falling.core

import android.content.Context
import android.util.Log
import com.luafabric.studio.falling.native.YunJuBridge
import com.luafabric.studio.falling.ui.login.LoginStore

/**
 * 在线状态心跳：独立后台线程每 60s POST zaixian.php（yuju:81，native mbedTLS）。
 *
 * 规则（用户约定）：
 *  - 每 60 秒一次，worker 线程，睡眠式定时，不占主线程；
 *  - 绑定 luafabric 应用生命周期：MainActivity onCreate 启动 / onDestroy 停；
 *  - 未登录不上报（跳过本轮请求）；
 *  - 任何失败静默，仅 LogCat 记录（tag=Online），不影响任何其他逻辑。
 */
object OnlinePresence {

    private const val INTERVAL_MS = 60_000L
    private const val TAG = "Online"
    // 与原生层常量一致：云居后台账号 / 应用 APPID
    private const val BACKSTAGE = "3445352175" // YUNJU_ADMIN
    private const val APPID = "2283" // YUNJU_APP_ID

    @Volatile
    private var running = false

    private var thread: Thread? = null

    /** 幂等启动：重复调用直接返回。 */
    fun start(context: Context) {
        if (running) return
        running = true
        thread = Thread {
            val appContext = context.applicationContext
            while (running) {
                val qq = currentLoggedQq(appContext)
                if (!qq.isNullOrBlank()) {
                    submit(appContext, qq)
                } else {
                    Log.d(TAG, "not logged in, skip round")
                }
                if (!running) break
                try {
                    Thread.sleep(INTERVAL_MS)
                } catch (ie: InterruptedException) {
                    break
                }
            }
        }.apply {
            isDaemon = true
            name = "online-presence"
            thread = this
        }
        thread?.start()
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun currentLoggedQq(context: Context): String? {
        val saved = LoginStore.read(context)
        // user_json 仅在 keepLoggedIn 会话中持久化并解封 → 视为已登录；其余一律不上报
        return saved.user?.qq?.takeIf { it.isNotBlank() }
    }

    private fun submit(context: Context, qq: String) {
        try {
            val resp = YunJuBridge.nativeOnlineSubmit(context, BACKSTAGE, APPID, qq)
            if (resp == null) {
                Log.w(TAG, "submit gated/failed, qq=${qq.take(4)}")
                return
            }
            val json = extractJson(resp)
            if (json == null) {
                Log.w(TAG, "bad response, no JSON body")
                return
            }
            try {
                val obj = org.json.JSONObject(json)
                Log.d(
                    TAG,
                    "submit ok code=${obj.opt("code")} online_count=${obj.opt("online_count")} " +
                        "expire=${obj.opt("expire_time")} msg=${obj.opt("msg")}"
                )
            } catch (e: Exception) {
                Log.w(TAG, "response parse fail: ${e.message}")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "submit error: ${e.message}")
        }
    }

    /** 裁剪纯 JSON body：取首 '{' 起 + 末 '}' 截断（响应可能带 HTTP 头）。 */
    private fun extractJson(s: String): String? {
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        return if (start >= 0 && end > start) s.substring(start, end + 1) else null
    }
}