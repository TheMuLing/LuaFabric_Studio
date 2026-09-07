package com.luafabric.console.env

/** 按文件跟踪 require/bindClass：Java 库反射全方法签名；C/Lua 库函数名 + debug.getinfo 参数个数。 */
object ModuleTracker {

    data class JavaLib(val className: String, val methods: List<String>)

    /** native=false 为自定义 lua 模块（函数 source 非 [C]）。 */
    data class LuaLib(val module: String, val funcs: Map<String, Int>, val native: Boolean)

    private val lock = Any()
    private val javaLibs = LinkedHashMap<String, LinkedHashMap<String, JavaLib>>()
    private val luaLibs = LinkedHashMap<String, LinkedHashMap<String, LuaLib>>()

    /** Lua 标准库 / AndLua 内置模块：require 时跳过，只显示第三方 native 模块（yyjson/cjson 等）。 */
    private val LUA_BUILTIN = setOf(
        "table", "string", "os", "io", "math", "debug", "package", "coroutine",
        "utf8", "bit32", "loadlayout", "loadbitmap", "loaddimen", "loadcolor",
        "loadstring", "import", "gc", "collectgarbage", "loadmenu"
    )

    /** 系统 / 内置 Java 类前缀：bindClass 时跳过，只显示引入的 dex 类。 */
    private val JAVA_SYSTEM_PREFIXES = listOf(
        "android.", "androidx.", "java.", "javax.", "kotlin.", "kotlinx.",
        "org.w3c.", "org.xml.", "com.androlua.", "com.google.",
        "com.luafabric.studio.falling."
    )

    fun recordBindClass(file: String, className: String?, clazz: Class<*>?) {
        if (file.isBlank() || className.isNullOrBlank() || clazz == null) return
        if (JAVA_SYSTEM_PREFIXES.any { className.startsWith(it) }) return
        val methods = clazz.declaredMethods
            .filter { it.declaringClass == clazz }
            .sortedBy { it.name }
            .take(200)
            .map { "${it.name}(${it.parameterTypes.joinToString(", ") { p -> p.simpleName }}) : ${it.returnType.simpleName}" }
        synchronized(lock) {
            val m = javaLibs.getOrPut(file) { LinkedHashMap() }
            if (m.size >= 60) return
            m[className] = JavaLib(className, methods)
        }
    }

    fun recordRequire(file: String, module: String?, funcs: Map<String, Int>?, native: Boolean) {
        if (file.isBlank() || module.isNullOrBlank()) return
        if (LUA_BUILTIN.contains(module)) return
        synchronized(lock) {
            val m = luaLibs.getOrPut(file) { LinkedHashMap() }
            if (m.size >= 60) return
            m[module] = LuaLib(module, funcs ?: emptyMap(), native)
        }
    }

    fun javaLibs(file: String): List<JavaLib> = synchronized(lock) {
        javaLibs[file]?.values?.toList() ?: emptyList()
    }

    fun luaLibs(file: String): List<LuaLib> = synchronized(lock) {
        luaLibs[file]?.values?.toList() ?: emptyList()
    }

    fun clear() = synchronized(lock) {
        javaLibs.clear()
        luaLibs.clear()
    }
}
