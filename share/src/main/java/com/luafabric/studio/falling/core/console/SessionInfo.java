package com.luafabric.studio.falling.core.console;

import android.app.Activity;

import com.luajava.LuaState;

/** 调试会话信息。Activity 一律持为 android.app.Activity，避免向控制台实现泄漏 androlua 类型。 */
public class SessionInfo {

    public final Activity activity;
    public final LuaState luaState;
    public final String luaPath;
    public final String luaDir;
    public final String luaExtDir;
    public final long startTimeMs;
    public final boolean debugMode;

    /** F7：跨 Intent 携带的调试参数 JSON（Lua 侧注入为全局 debugParams 表），可为 null。 */
    public final String debugParamsJson;

    public SessionInfo(
            Activity activity,
            LuaState luaState,
            String luaPath,
            String luaDir,
            String luaExtDir,
            long startTimeMs,
            boolean debugMode,
            String debugParamsJson) {
        this.activity = activity;
        this.luaState = luaState;
        this.luaPath = luaPath;
        this.luaDir = luaDir;
        this.luaExtDir = luaExtDir;
        this.startTimeMs = startTimeMs;
        this.debugMode = debugMode;
        this.debugParamsJson = debugParamsJson;
    }
}
