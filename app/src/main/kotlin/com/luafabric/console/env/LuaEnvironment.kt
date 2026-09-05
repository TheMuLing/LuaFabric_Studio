package com.luafabric.console.env

import com.luajava.LuaState

/** 环境页：Lua 版本 + JIT 检测，会话开始时经 LuaState 只读探测。 */
object LuaEnvironment {

    @Volatile
    var version: String = ""
        private set

    @Volatile
    var jit: Boolean = false
        private set

    @Volatile
    var probed: Boolean = false
        private set

    fun probe(luaState: LuaState?) {
        if (luaState == null) return
        synchronized(luaState) {
            try {
                luaState.getGlobal("_VERSION")
                if (luaState.type(-1) == LuaState.LUA_TSTRING) {
                    version = luaState.toString(-1)
                }
                luaState.pop(1)
                luaState.getGlobal("jit")
                jit = luaState.type(-1) != LuaState.LUA_TNIL
                luaState.pop(1)
                probed = true
            } catch (e: Exception) {
                // 探测失败保持默认值
            }
        }
    }

    fun versionLabel(): String =
        "Lua ${version.ifBlank { "?" }}${if (jit) " (JIT)" else ""}"
}
