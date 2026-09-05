package com.luafabric.studio.falling.core.console;

/**
 * 一次性线程标记：luajava 调用层拦到 Lua 侧显式调用 activity.runFunc 时置位，
 * LuaActivity.runFunc 确认事件实际触发后消费。用于区分「Lua 显式调用」与「Java 生命周期内部调用」。
 */
public final class ConsoleCallMarker {

    private static final ThreadLocal<Boolean> MARKER = new ThreadLocal<>();

    private ConsoleCallMarker() {}

    public static void set() {
        MARKER.set(Boolean.TRUE);
    }

    public static boolean consume() {
        Boolean v = MARKER.get();
        if (v == null) {
            return false;
        }
        MARKER.remove();
        return v;
    }
}
