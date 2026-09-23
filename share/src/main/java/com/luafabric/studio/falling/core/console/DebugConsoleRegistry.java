package com.luafabric.studio.falling.core.console;

/** 调试控制台桥注册表。未注册实现时所有钩点单次 volatile 判空短路。 */
public final class DebugConsoleRegistry {

    private static volatile DebugConsoleBridge bridge;

    /** Lua 侧报错是否以 Toast 回显（app 侧设置项控制；未集成控制台时保持默认 true=旧行为）。 */
    private static volatile boolean errorToastEnabled = true;

    private DebugConsoleRegistry() {}

    public static void register(DebugConsoleBridge b) {
        bridge = b;
    }

    public static void unregister() {
        bridge = null;
    }

    public static DebugConsoleBridge get() {
        return bridge;
    }

    public static void setErrorToastEnabled(boolean enabled) {
        errorToastEnabled = enabled;
    }

    /** Lua 报错 Toast 回显是否开启（LuaActivity.sendError 读取）。 */
    public static boolean isErrorToastEnabled() {
        return errorToastEnabled;
    }
}
