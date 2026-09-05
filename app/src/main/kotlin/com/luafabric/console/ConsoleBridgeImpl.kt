package com.luafabric.console

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import com.luafabric.console.core.ConsoleSettings
import com.luafabric.console.core.ConsoleState
import com.luafabric.console.core.FileStateTracker
import com.luafabric.console.core.SessionManager
import com.luafabric.console.core.StateMachine
import com.luafabric.console.intercept.NewActivityInterceptor
import com.luafabric.console.output.OutputEntry
import com.luafabric.console.output.OutputManager
import com.luafabric.console.output.TypeResolver
import com.luafabric.console.persist.ConsolePaths
import com.luafabric.console.ui.OverlayController
import com.luafabric.studio.falling.core.console.DebugConsoleBridge
import com.luafabric.studio.falling.core.console.MethodCallResult
import com.luafabric.studio.falling.core.console.SessionInfo

/** 调试控制台桥实现：路由到会话/输出/拦截各管理器。 */
class ConsoleBridgeImpl(private val context: Context) : DebugConsoleBridge {

    private val settings = ConsoleSettings(context)
    private val overlay = OverlayController(context)
    private val newActivityInterceptor = NewActivityInterceptor(context)

    override fun onSessionStart(info: SessionInfo) {
        ConsolePaths.init(context)
        SessionManager.begin(info)
        FileStateTracker.updateFromSession(info)
        OutputManager.currentFile = info.luaPath ?: ""
        StateMachine.transition(ConsoleState.BALL)
        overlay.showBall()
        // F5 记录起点 / F7 debugParams 注入（后续提交）
    }

    override fun onSessionEnd(info: SessionInfo) {
        overlay.closeAll()
        StateMachine.transition(ConsoleState.IDLE)
        SessionManager.end()
        // F5 记录终点 / 归档（后续提交）
    }

    override fun onPrint(text: String?, luaTypes: IntArray?, rawArgs: Array<out Any?>?) {
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
        appendEntry("toast", text ?: "")
    }

    override fun onSnackbar(text: String?) {
        appendEntry("snackbar", text ?: "")
    }

    override fun onError(title: String?, message: String?) {
        val primary = if (title.isNullOrBlank()) (message ?: "") else "$title: ${message ?: ""}"
        appendEntry("error", primary)
    }

    override fun onMethodCall(
        receiver: Any?,
        methodName: String?,
        args: Array<out Any?>?,
        luaState: Long
    ): MethodCallResult {
        // F2：观察 setContentView（布局判定）
        if (methodName == "setContentView" && receiver is android.app.Activity) {
            FileStateTracker.onSetContentView()
        }
        // F3：newActivity 阻塞确认 / 同参丢弃 / 异参列表单选
        return newActivityInterceptor.intercept(receiver, methodName, args)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 仅完全关闭（CLOSED）状态消费音量下键恢复浮球，其余状态放行。
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && StateMachine.state == ConsoleState.CLOSED) {
            StateMachine.transition(ConsoleState.BALL)
            overlay.showBall()
            return true
        }
        return false
    }

    override fun onEvent(funcName: String?, args: Array<out Any?>?) {
        // F6（后续提交）
    }

    override fun onRequire(moduleName: String?) {
        // F4（后续提交）
    }

    override fun onBindClass(className: String?, clazz: Class<*>?) {
        // F4（后续提交）
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
