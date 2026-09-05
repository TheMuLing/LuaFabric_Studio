package com.luafabric.console

import android.content.Context
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
import com.luafabric.studio.falling.core.console.MethodCallResult
import com.luafabric.studio.falling.core.console.SessionInfo

/** 调试控制台桥实现：路由到会话/输出/拦截各管理器。 */
class ConsoleBridgeImpl(private val context: Context) : DebugConsoleBridge {

    private val settings = ConsoleSettings(context)
    private val overlay = OverlayController(context)
    private val newActivityInterceptor = NewActivityInterceptor(context)

    /** 会话门控：仅 debugmode 项目激活捕获（非调试会话零捕获）。 */
    @Volatile
    private var active = false

    init {
        CrashCapture.install()
        CrashCapture.onCrash = {
            Handler(Looper.getMainLooper()).post { overlay.setBallRed(true) }
        }
    }

    override fun onSessionStart(info: SessionInfo) {
        active = info.debugMode
        ConsolePaths.init(context)
        SessionManager.begin(info)
        FileStateTracker.updateFromSession(info)
        LuaEnvironment.probe(info.luaState)
        OutputManager.currentFile = info.luaPath ?: ""
        injectDebugParams(info)
        if (!active) return
        LogcatManager.start(projectName(info))
        StateMachine.transition(ConsoleState.BALL)
        overlay.showBall()
    }

    override fun onSessionEnd(info: SessionInfo) {
        if (active) {
            SessionArchiver.archive(projectName(info))
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
        appendEntry("error", primary)
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
        // F3：newActivity 阻塞确认 / 同参丢弃 / 异参列表单选
        return newActivityInterceptor.intercept(receiver, methodName, args)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!active) return false
        // 仅完全关闭（CLOSED）状态消费音量下键恢复浮球，其余状态放行。
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && StateMachine.state == ConsoleState.CLOSED) {
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

    override fun onRequire(moduleName: String?, funcParams: Map<String, Int>?) {
        if (!active) return
        ModuleTracker.recordRequire(OutputManager.currentFile, moduleName, funcParams)
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
