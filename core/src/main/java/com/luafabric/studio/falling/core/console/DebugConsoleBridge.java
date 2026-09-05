package com.luafabric.studio.falling.core.console;

import android.view.KeyEvent;

/**
 * 调试控制台桥接口。
 *
 * <p>core 侧仅在存在已注册实现时转发事件；用户应用（core-apk 打包）不含实现，所有钩点
 * 在 {@link DebugConsoleRegistry#get()} 为 null 时短路，零行为变化。
 */
public interface DebugConsoleBridge {

    /** 调试会话开始（debugmode 项目 onCreate，doFile 之前）。 */
    default void onSessionStart(SessionInfo info) {}

    /** 调试会话中的某个 Activity 销毁。 */
    default void onSessionEnd(SessionInfo info) {}

    /** Lua 侧 print 输出（含每个参数的一级 Lua 类型与原始值）。 */
    default void onPrint(String text, int[] luaTypes, Object[] rawArgs) {}

    /** Lua 侧调用 Toast.makeText 的内容（luajava 调用层观察）。 */
    default void onToast(String text) {}

    /** Lua 侧调用 Snackbar.make 的内容（luajava 调用层观察）。 */
    default void onSnackbar(String text) {}

    /** Lua 脚本报错（LuaActivity.sendError）。 */
    default void onError(String title, String message) {}

    /**
     * Lua 侧调用 Java 对象方法的拦截点（activity.newActivity / setContentView / runFunc /
     * Toast.makeText / Snackbar.make 等）。
     *
     * @return ALLOW 放行；VETO 阻断（Lua 侧得到 nil）；REPLACE 以 replaceValue 替代返回值
     */
    default MethodCallResult onMethodCall(Object receiver, String methodName, Object[] args, long luaState) {
        return MethodCallResult.ALLOW;
    }

    /** 音量键等按键事件；返回 true 表示消费。 */
    default boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    /** Lua 侧显式调用 activity.runFunc 且事件实际触发。 */
    default void onEvent(String funcName, Object[] args) {}

    /** Lua 侧 require 模块。 */
    default void onRequire(String moduleName) {}

    /** Lua 侧 bindClass 绑定 Java 类。 */
    default void onBindClass(String className, Class<?> clazz) {}
}
