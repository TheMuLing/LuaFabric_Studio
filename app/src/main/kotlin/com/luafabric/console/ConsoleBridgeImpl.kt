package com.luafabric.console

import android.content.Context
import android.view.KeyEvent
import com.luafabric.studio.falling.core.console.DebugConsoleBridge
import com.luafabric.studio.falling.core.console.MethodCallResult
import com.luafabric.studio.falling.core.console.SessionInfo

/** 调试控制台桥实现：路由到会话/输出/拦截各管理器。 */
class ConsoleBridgeImpl(private val context: Context) : DebugConsoleBridge {

    override fun onSessionStart(info: SessionInfo) {
        // F5 记录起点 / F7 debugParams 注入
    }

    override fun onSessionEnd(info: SessionInfo) {
        // F5 记录终点 / 归档
    }

    override fun onPrint(text: String?, luaTypes: IntArray?, rawArgs: Array<out Any?>?) {
        // F1
    }

    override fun onToast(text: String?) {
        // F1
    }

    override fun onSnackbar(text: String?) {
        // F1
    }

    override fun onError(title: String?, message: String?) {
        // F1
    }

    override fun onMethodCall(
        receiver: Any?,
        methodName: String?,
        args: Array<out Any?>?,
        luaState: Long
    ): MethodCallResult = MethodCallResult.ALLOW

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = false

    override fun onEvent(funcName: String?, args: Array<out Any?>?) {
        // F6
    }

    override fun onRequire(moduleName: String?) {
        // F4
    }

    override fun onBindClass(className: String?, clazz: Class<*>?) {
        // F4
    }
}
