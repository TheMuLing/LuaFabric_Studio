package com.luafabric.console

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.luafabric.console.core.ConsoleSettings
import com.luafabric.console.core.ConsoleState
import com.luafabric.console.core.EventTracker
import com.luafabric.console.core.FileStateTracker
import com.luafabric.console.core.SessionManager
import com.luafabric.console.core.StateMachine
import com.luafabric.console.env.LuaEnvironment
import com.luafabric.console.env.ModuleTracker
import com.luafabric.console.intercept.NewActivityInterceptor
import com.luafabric.console.logcat.LogcatManager
import com.luafabric.console.output.OutputEntry
import com.luafabric.console.output.OutputManager
import com.luafabric.console.output.TypeResolver
import com.luafabric.console.debug.FileLauncher
import com.luafabric.console.persist.ConsolePaths
import com.luafabric.console.persist.CrashCapture
import com.luafabric.console.persist.SessionArchiver
import com.luafabric.console.ui.OverlayController
import com.luajava.LuaState
import java.io.File
import muling.views.tool.utils.JsonUtil
import com.luafabric.studio.falling.core.console.DebugConsoleBridge
import com.luafabric.studio.falling.core.console.DebugConsoleRegistry
import com.luafabric.studio.falling.core.console.MethodCallResult
import com.luafabric.studio.falling.core.console.SessionInfo

/** 调试控制台桥实现：路由到会话/输出/拦截各管理器。 */
class ConsoleBridgeImpl(private val context: Context) : DebugConsoleBridge {

    private val settings = ConsoleSettings(context)
    private val overlay = OverlayController(context)
    private val newActivityInterceptor = NewActivityInterceptor(context) { primary ->
        // B：newActivity 允许后重放失败（目标文件被删等）→ error 条目入 F1 缓冲，不复播 toast
        reportError(primary)
    }
    /** 未读 Lua 错误计数 → 浮球右上角角标；打开面板 / 清空当前缓冲时清零。 */
    private val errorUnread = java.util.concurrent.atomic.AtomicInteger(0)

    /** 会话门控：仅 debugmode 项目激活捕获（非调试会话零捕获）。 */
    @Volatile
    private var active = false

    init {
        CrashCapture.install()
        CrashCapture.onCrash = {
            Handler(Looper.getMainLooper()).post { overlay.setBallRed(true) }
        }
        // E：报错 toast 由设置项门控（默认关），同步到 core 注册表（LuaActivity.sendError 读取）
        DebugConsoleRegistry.setErrorToastEnabled(settings.toastLuaErrors)
        // E：打开控制台面板 → 未读错误角标清零（打开即视为已读）
        StateMachine.addListener(object : StateMachine.Listener {
            override fun onStateChanged(old: ConsoleState, new: ConsoleState) {
                if (new == ConsoleState.PANEL) clearErrorBadge()
            }
        })
        registerForegroundCallbacks()
    }

    /** E：错误条目入 F1 缓冲 + 未读计数 +1 → 浮球角标更新。线程安全（onError 来自 Lua 线程）。 */
    private fun reportError(primary: String) {
        appendEntry("error", primary)
        errorUnread.incrementAndGet()
        Handler(Looper.getMainLooper()).post { overlay.setErrorCount(errorUnread.get()) }
    }

    /** E：清空未读错误角标（面板打开 / 清空当前文件缓冲）。 */
    fun clearErrorBadge() {
        errorUnread.set(0)
        Handler(Looper.getMainLooper()).post { overlay.setErrorCount(0) }
    }

    /** 前后台感知：后台藏球+面板强收，前台按 BALL 态恢复；会话/logcat 不中断。 */
    private fun registerForegroundCallbacks() {
        val app = context.applicationContext as? Application ?: return
        // started/stopped 计数：页内跳转 A.stop 晚于 B.start，计数不归零 → 不误判后台
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                if (++started == 1) onForeground()
            }
            override fun onActivityStopped(activity: Activity) {
                if (--started <= 0) {
                    started = 0
                    onBackground()
                }
            }
            override fun onActivityResumed(activity: Activity) {
                // 回旧页等恢复场景：宿主切到该页，消除 openSheet 读到已销毁宿主而吞点击的竞态
                SessionManager.onHostResumed(activity)
                // 页内返回/恢复：浮球缺失则重建（面板开在销毁页上被收走/宿主销毁竞态等场景）
                if (active && StateMachine.state == ConsoleState.BALL && !overlay.isBallShowing()) {
                    overlay.showBall()
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityDestroyed(activity: Activity) {
                // 宿主销毁（newActivity+finish 等）：清 stale 引用，防死 sheet 短路 openSheet / 浮球随 decorView 丢失。
                // 时序：晚于 LuaActivity.onDestroy → onSessionEnd，末页场景已 end() 全清，此处无操作；非末页场景补清理。
                SessionManager.onHostDestroyed(activity)
                overlay.onHostDestroyed(activity)
                // 兜底重建：fallback 浮球宿主销毁后 ball 引用被摘 → BALL 态缺失时立即重建挂新宿主
                if (active && StateMachine.state == ConsoleState.BALL && !overlay.isBallShowing()) {
                    overlay.showBall()
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    private fun onForeground() {
        if (!active) return
        // 状态自愈：PANEL 但无 sheet（宿主销毁/泄漏竞态）→ 回 BALL 重建
        if (StateMachine.state == ConsoleState.PANEL && !overlay.isSheetShowing()) {
            StateMachine.transition(ConsoleState.BALL)
        }
        if (StateMachine.state == ConsoleState.BALL) overlay.showBall()
    }

    private fun onBackground() {
        if (!active) return
        overlay.hideForBackground()
    }

    override fun onSessionStart(info: SessionInfo) {
        val prev = SessionManager.current
        val fresh = SessionManager.begin(info)
        if (fresh) {
            // 重启/重建/文件调起：旧会话先归档 + 停旧 logcat，再开新会话
            if (prev != null && active) {
                SessionArchiver.archive(prev)
                LogcatManager.stop()
            }
            // 项目级输出隔离：新会话清空缓冲池，杜绝上一项目输出混入本会话
            OutputManager.clearAll()
            active = info.debugMode
            ConsolePaths.init(context)
            if (active) LogcatManager.start(info.luaDir, projectName(info))
            injectDebugParams(info)
        }
        // 每页上下文刷新（页面相关，跨页会话持续）
        FileStateTracker.updateFromSession(info)
        LuaEnvironment.probe(info.luaState)
        OutputManager.currentFile = info.luaPath ?: ""
        if (!active) return
        if (fresh) {
            StateMachine.transition(ConsoleState.BALL)
            overlay.showBall()
            overlay.setErrorCount(errorUnread.get()) // 新会话浮球重建后补回未读角标
        } else if (StateMachine.state == ConsoleState.BALL && !overlay.isBallShowing()) {
            // 宿主销毁后 join：浮球缺失则重建（兜底挂旧页 decorView 一并覆盖）
            overlay.showBall()
        }
    }

    override fun onSessionEnd(info: SessionInfo) {
        // 非当前代次（重启后旧页迟到销毁）→ 忽略；非末页 → 仅摘成员
        val last = SessionManager.detach(info)
        if (!last) return
        if (active) {
            SessionArchiver.archive(info)
            overlay.closeAll()
            StateMachine.transition(ConsoleState.IDLE)
            LogcatManager.stop()
        }
        active = false
        EventTracker.clear()
        ModuleTracker.clear()
        SessionManager.end()
    }

    private fun projectName(info: SessionInfo): String =
        (info.luaDir?.let { File(it).name }?.ifBlank { null }) ?: "project"

    /** F7：跨 Intent 的 debugParams JSON → Lua 全局 debugParams 真 table（须在 doFile 前调用）。 */
    private fun injectDebugParams(info: SessionInfo) {
        val json = info.debugParamsJson ?: return
        if (json.isBlank()) return
        val map = try {
            JsonUtil.parseObject(json)
        } catch (_: Exception) {
            return
        }
        try {
            info.luaState.newTable()
            pushMap(info.luaState, map)
            info.luaState.setGlobal("debugParams")
        } catch (_: Exception) {
        }
    }

    private fun pushMap(state: LuaState, map: Map<String, Any?>) {
        for ((k, v) in map) {
            pushValue(state, v)
            state.setField(-2, k)
        }
    }

    private fun pushValue(state: LuaState, v: Any?) {
        when (v) {
            null -> state.pushNil()
            is Boolean -> state.pushBoolean(v)
            is Int -> state.pushInteger(v.toLong())
            is Long -> state.pushInteger(v)
            is Number -> state.pushNumber(v.toDouble())
            is String -> state.pushString(v)
            is Map<*, *> -> {
                state.newTable()
                for ((k, value) in v) {
                    pushValue(state, value)
                    state.setField(-2, k?.toString() ?: "")
                }
            }
            is List<*> -> {
                state.newTable()
                v.forEachIndexed { i, value ->
                    pushValue(state, value)
                    state.setField(-2, (i + 1).toString())
                }
            }
            else -> state.pushObjectValue(v)
        }
    }

    override fun onPrint(text: String?, luaTypes: IntArray?, rawArgs: Array<out Any?>?) {
        if (!active) return
        val depth = settings.parseDepth
        val l1 = ArrayList<String>(luaTypes?.size ?: 0)
        val l2 = ArrayList<String>(luaTypes?.size ?: 0)
        if (luaTypes != null) {
            for (i in luaTypes.indices) {
                val r = TypeResolver.resolve(luaTypes[i], rawArgs?.getOrNull(i), depth)
                l1 += r.level1
                l2 += r.level2
            }
        }
        appendEntry("print", text ?: "", l1, l2)
    }

    override fun onToast(text: String?) {
        if (!active) return
        appendEntry("toast", text ?: "")
    }

    override fun onSnackbar(text: String?) {
        if (!active) return
        appendEntry("snackbar", text ?: "")
    }

    override fun onError(title: String?, message: String?) {
        if (!active) return
        val primary = if (title.isNullOrBlank()) (message ?: "") else "$title: ${message ?: ""}"
        reportError(primary)
    }

    override fun onMethodCall(
        receiver: Any?,
        methodName: String?,
        args: Array<out Any?>?,
        luaState: Long
    ): MethodCallResult {
        if (!active) return MethodCallResult.ALLOW
        // F2：观察 setContentView（布局判定）
        if (methodName == "setContentView" && receiver is android.app.Activity) {
            FileStateTracker.onSetContentView()
        }
        // F3：newActivity 阻塞确认 / 同参丢弃 / 异参列表单选（弹窗用调用方 Activity 作 context）
        return newActivityInterceptor.intercept(receiver as? Activity, methodName, args)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!active) return false
        // IDLE（会话意外隐藏）/ CLOSED（显式关闭）状态下按音量下键恢复浮球，其余状态放行系统音量。
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN &&
            (StateMachine.state == ConsoleState.IDLE || StateMachine.state == ConsoleState.CLOSED)
        ) {
            StateMachine.transition(ConsoleState.BALL)
            overlay.showBall()
            return true
        }
        return false
    }

    override fun onEvent(funcName: String?, args: Array<out Any?>?) {
        if (!active) return
        EventTracker.record(
            file = FileStateTracker.relativePath.ifBlank { OutputManager.currentFile },
            funcName = funcName ?: "?",
            args = args,
            timeMs = System.currentTimeMillis(),
            isMainThread = Looper.getMainLooper().thread === Thread.currentThread()
        )
    }

    override fun onRequire(moduleName: String?, funcParams: Map<String, Int>?, nativeModule: Boolean) {
        if (!active) return
        ModuleTracker.recordRequire(OutputManager.currentFile, moduleName, funcParams, nativeModule)
    }

    override fun onBindClass(className: String?, clazz: Class<*>?) {
        if (!active) return
        ModuleTracker.recordBindClass(OutputManager.currentFile, className, clazz)
    }

    private fun appendEntry(label: String, primary: String, luaTypes: List<String> = emptyList(), typeDetails: List<String> = emptyList()) {
        OutputManager.append(
            OutputEntry(
                id = OutputManager.nextId(),
                file = OutputManager.currentFile,
                label = label,
                primary = primary,
                luaTypes = luaTypes,
                typeDetails = typeDetails,
                isMainThread = Looper.getMainLooper().thread === Thread.currentThread(),
                timestampMs = System.currentTimeMillis()
            )
        )
    }
}
