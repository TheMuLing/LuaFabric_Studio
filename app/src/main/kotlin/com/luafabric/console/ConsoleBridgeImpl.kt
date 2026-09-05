package com.luafabric.console

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import com.luafabric.console.core.ConsoleSettings
import com.luafabric.console.output.OutputEntry
import com.luafabric.console.output.OutputManager
import com.luafabric.console.output.TypeResolver
import com.luafabric.console.persist.ConsolePaths
import com.luafabric.studio.falling.core.console.DebugConsoleBridge
import com.luafabric.studio.falling.core.console.MethodCallResult
import com.luafabric.studio.falling.core.console.SessionInfo

/** 调试控制台桥实现：路由到会话/输出/拦截各管理器。 */
class ConsoleBridgeImpl(private val context: Context) : DebugConsoleBridge {

    private val settings = ConsoleSettings(context)

    override fun onSessionStart(info: SessionInfo) {
        ConsolePaths.init(context)
        OutputManager.currentFile = info.luaPath ?: ""
        // F5 记录起点 / F7 debugParams 注入（后续提交）
    }

    override fun onSessionEnd(info: SessionInfo) {
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
    ): MethodCallResult = MethodCallResult.ALLOW

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = false

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
