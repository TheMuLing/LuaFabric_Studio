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

    public SessionInfo(
            Activity activity,
            LuaState luaState,
            String luaPath,
            String luaDir,
            String luaExtDir,
            long startTimeMs,
            boolean debugMode) {
        this.activity = activity;
        this.luaState = luaState;
        this.luaPath = luaPath;
        this.luaDir = luaDir;
        this.luaExtDir = luaExtDir;
        this.startTimeMs = startTimeMs;
        this.debugMode = debugMode;
    }
}
