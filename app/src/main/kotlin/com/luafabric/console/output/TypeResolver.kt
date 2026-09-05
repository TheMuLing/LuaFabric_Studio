package com.luafabric.console.output

import java.util.IdentityHashMap

/**
 * 两级类型解析：
 * 一级 = Lua 可见类型（userdata/function/string/...），
 * 二级 = 具体解析（java 类名 / 内容预览，含上限与循环防护）。
 */
object TypeResolver {

    private val LUA_TYPE_NAMES = mapOf(
        -1 to "none", 0 to "nil", 1 to "boolean", 2 to "lightuserdata",
        3 to "number", 4 to "string", 5 to "table", 6 to "function",
        7 to "userdata", 8 to "thread"
    )

    data class ResolvedArg(val level1: String, val level2: String)

    fun resolve(luaType: Int, raw: Any?, depth: Int): ResolvedArg {
        val l1 = LUA_TYPE_NAMES[luaType] ?: "unknown"
        val l2 = when (l1) {
            "userdata", "lightuserdata" -> resolveUserdata(raw, depth)
            "table" -> resolveTable(raw, depth)
            else -> preview(raw?.toString(), depth)
        }
        return ResolvedArg(l1, l2)
    }

    private fun resolveUserdata(raw: Any?, depth: Int): String {
        if (raw == null) return "null"
        val cls = raw.javaClass.name
        return "$cls ${preview(safeToString(raw), depth)}".trimEnd()
    }

    private fun resolveTable(raw: Any?, depth: Int): String {
        if (raw !is Map<*, *>) return preview(raw?.toString(), 1)
        return tablePreview(raw, depth, IdentityHashMap())
    }

    private fun tablePreview(map: Map<*, *>, depth: Int, seen: IdentityHashMap<Any, Boolean>): String {
        if (depth <= 0 || seen.put(map, true) != null) return "{...}"
        val sb = StringBuilder("{")
        var i = 0
        for ((k, v) in map) {
            if (i >= 16) {
                sb.append("...")
                break
            }
            if (i > 0) sb.append(", ")
            sb.append(preview(k?.toString(), 1)).append('=')
            sb.append(if (v is Map<*, *>) tablePreview(v, depth - 1, seen) else preview(v?.toString(), depth - 1))
            i++
        }
        return sb.append('}').toString()
    }

    private fun safeToString(raw: Any): String =
        try {
            raw.toString()
        } catch (e: Throwable) {
            raw.javaClass.simpleName
        }

    private fun preview(text: String?, depth: Int): String {
        if (text == null) return "null"
        val cap = when {
            depth <= 0 -> 32
            depth == 1 -> 64
            else -> 256
        }
        val trimmed = text.replace('\n', ' ')
        return if (trimmed.length <= cap) trimmed else trimmed.substring(0, cap) + "…"
    }
}
