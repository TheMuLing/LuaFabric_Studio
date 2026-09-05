package com.luafabric.console.env

/** 按文件跟踪 require/bindClass：Java 库反射全方法签名；C/Lua 库函数名 + debug.getinfo 参数个数。 */
object ModuleTracker {

    data class JavaLib(val className: String, val methods: List<String>)

    data class LuaLib(val module: String, val funcs: Map<String, Int>)

    private val lock = Any()
    private val javaLibs = LinkedHashMap<String, LinkedHashMap<String, JavaLib>>()
    private val luaLibs = LinkedHashMap<String, LinkedHashMap<String, LuaLib>>()

    fun recordBindClass(file: String, className: String?, clazz: Class<*>?) {
        if (file.isBlank() || className.isNullOrBlank() || clazz == null) return
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

    fun recordRequire(file: String, module: String?, funcs: Map<String, Int>?) {
        if (file.isBlank() || module.isNullOrBlank()) return
        synchronized(lock) {
            val m = luaLibs.getOrPut(file) { LinkedHashMap() }
            if (m.size >= 60) return
            m[module] = LuaLib(module, funcs ?: emptyMap())
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
