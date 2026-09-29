package com.luafabric.studio.falling.core

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.luafabric.studio.falling.native.YunJuBridge
import com.luafabric.studio.falling.ui.login.LoginStore
import java.util.concurrent.TimeUnit

/**
 * 在线状态心跳：前台 60s 线程 + 后台 WorkManager 周期任务兜底。
 *
 * 规则（用户约定）：
 *  - 前台：worker 线程每 60 秒 POST zaixian.php（yuju:81，native mbedTLS），睡眠式定时，不占主线程；
 *  - 退后台：注册 15 分钟周期 Job（WorkManager 下限），进程被冻结/回收后仍由 JobScheduler 唤醒上报；
 *  - 回前台：取消后台周期任务，交回 60s 线程；
 *  - 未登录不上报（跳过本轮请求）；
 *  - 任何失败静默，仅 LogCat 记录（tag=Online），不影响任何其他逻辑。
 */
object OnlinePresence {

    private const val INTERVAL_MS = 60_000L
    private const val TAG = "Online"
    private const val WORK_NAME = "online_presence_bg"
    private const val BG_INTERVAL_MINUTES = 15L
    // 与原生层常量一致：云居后台账号 / 应用 APPID
    private const val BACKSTAGE = "3445352175" // YUNJU_ADMIN
    private const val APPID = "2283" // YUNJU_APP_ID

    @Volatile
    private var running = false

    private var thread: Thread? = null

    /** 最近一次成功上报的在线总人数（-1=尚未获取），Compose 可直接读（写由后台线程触发，快照自动重组合）。 */
    var lastOnlineCount by mutableIntStateOf(-1)
        private set

    /** 最近一次成功上报的时间戳（毫秒，0=尚未获取）。 */
    var lastUpdateMillis by mutableLongStateOf(0L)
        private set

    /** 幂等启动：重复调用直接返回。 */
    fun start(context: Context) {
        if (running) return
        running = true
        thread = Thread {
            val appContext = context.applicationContext
            while (running) {
                submitOnce(appContext)
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

    /** 退后台：注册 15 分钟周期任务（KEEP 保留既有排期），进程冻结/回收后由系统唤醒上报。 */
    fun onBackground(context: Context) {
        try {
            val request = PeriodicWorkRequestBuilder<OnlinePresenceWorker>(
                BG_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            Log.d(TAG, "background worker scheduled (${BG_INTERVAL_MINUTES}min)")
        } catch (e: Throwable) {
            Log.w(TAG, "schedule background worker failed: ${e.message}")
        }
    }

    /** 回前台：取消后台周期任务，交回 60s 线程。 */
    fun onForeground(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "background worker cancelled")
        } catch (e: Throwable) {
            Log.w(TAG, "cancel background worker failed: ${e.message}")
        }
    }

    /** 单次上报（前台线程与后台 Worker 共用）。 */
    fun submitOnce(context: Context): Boolean {
        val qq = currentLoggedQq(context)
        if (qq.isNullOrBlank()) {
            Log.d(TAG, "not logged in, skip round")
            return false
        }
        return submit(context, qq)
    }

    private fun currentLoggedQq(context: Context): String? {
        val saved = LoginStore.read(context)
        // user_json 仅在 keepLoggedIn 会话中持久化并解封 → 视为已登录；其余一律不上报
        return saved.user?.qq?.takeIf { it.isNotBlank() }
    }

    private fun submit(context: Context, qq: String): Boolean {
        try {
            val resp = YunJuBridge.nativeOnlineSubmit(context, BACKSTAGE, APPID, qq)
            if (resp == null) {
                Log.w(TAG, "submit gated/failed, qq=${qq.take(4)}")
                return false
            }
            val json = extractJson(resp)
            if (json == null) {
                Log.w(TAG, "bad response, no JSON body")
                return false
            }
            try {
                val obj = org.json.JSONObject(json)
                lastOnlineCount = obj.optInt("online_count", lastOnlineCount)
                lastUpdateMillis = System.currentTimeMillis()
                Log.d(
                    TAG,
                    "submit ok code=${obj.opt("code")} online_count=${obj.opt("online_count")} " +
                        "expire=${obj.opt("expire_time")} msg=${obj.opt("msg")}"
                )
                return true
            } catch (e: Exception) {
                Log.w(TAG, "response parse fail: ${e.message}")
                return false
            }
        } catch (e: Throwable) {
            Log.w(TAG, "submit error: ${e.message}")
            return false
        }
    }

    /** 裁剪纯 JSON body：取首 '{' 起 + 末 '}' 截断（响应可能带 HTTP 头）。 */
    private fun extractJson(s: String): String? {
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        return if (start >= 0 && end > start) s.substring(start, end + 1) else null
    }
}

/** 后台在线心跳周期任务：被系统唤醒时执行一次上报（未登录自动跳过）。 */
class OnlinePresenceWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val ok = OnlinePresence.submitOnce(applicationContext)
        Log.d("Online", "background worker run => submitted=$ok")
        return Result.success()
    }
}