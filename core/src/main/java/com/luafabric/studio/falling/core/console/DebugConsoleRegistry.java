package com.luafabric.studio.falling.core.console;

/** 调试控制台桥注册表。未注册实现时所有钩点单次 volatile 判空短路。 */
public final class DebugConsoleRegistry {

    private static volatile DebugConsoleBridge bridge;

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
}
