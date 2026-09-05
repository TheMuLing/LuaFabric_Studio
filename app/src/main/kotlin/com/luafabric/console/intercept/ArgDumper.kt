package com.luafabric.console.intercept

import java.util.IdentityHashMap

/**
 * 参数通用序列化：Map（含 LuaTable）/数组/集合/基本类型。
 * 深度上限 + 环防护，避免自引用结构与巨型表拖垮确认弹窗。
 */
object ArgDumper {

    private const val MAX_DEPTH = 3
    private const val MAX_CHARS = 600

    fun dump(value: Any?): String {
        val sb = StringBuilder()
        val seen = IdentityHashMap<Any, Boolean>()
        dumpValue(value, 0, sb, seen)
        return if (sb.length > MAX_CHARS) sb.substring(0, MAX_CHARS) + "…" else sb.toString()
    }

    private fun dumpValue(v: Any?, depth: Int, sb: StringBuilder, seen: IdentityHashMap<Any, Boolean>) {
        if (depth > MAX_DEPTH) {
            sb.append("…")
            return
        }
        when {
            v == null -> sb.append("null")
            v is String -> {
                sb.append('"').append(v).append('"')
            }
            v is Char -> {
                sb.append('\'').append(v).append('\'')
            }
            v is Boolean || v is Number -> sb.append(v)
            v is Map<*, *> -> {
                if (seen.containsKey(v)) {
                    sb.append("{…(环)}")
                    return
                }
                seen[v] = true
                sb.append('{')
                var first = true
                for ((k, value) in v.entries) {
                    if (!first) sb.append(", ")
                    first = false
                    dumpValue(k, depth + 1, sb, seen)
                    sb.append('=')
                    dumpValue(value, depth + 1, sb, seen)
                }
                sb.append('}')
                seen.remove(v)
            }
            v is Collection<*> || v is Array<*> || v is IntArray || v is LongArray ||
                v is ShortArray || v is ByteArray || v is FloatArray || v is DoubleArray ||
                v is BooleanArray || v is CharArray
            -> {
                if (seen.containsKey(v)) {
                    sb.append("[…(环)]")
                    return
                }
                seen[v] = true
                sb.append('[')
                var first = true
                for (item in elementsOf(v)) {
                    if (!first) sb.append(", ")
                    first = false
                    dumpValue(item, depth + 1, sb, seen)
                }
                sb.append(']')
                seen.remove(v)
            }
            else -> {
                val s = try {
                    v.toString()
                } catch (e: Exception) {
                    "<unprintable>"
                }
                sb.append(s)
            }
        }
    }

    private fun elementsOf(v: Any): Iterable<*> = when (v) {
        is Collection<*> -> v
        is Array<*> -> v.toList()
        is IntArray -> v.asList()
        is LongArray -> v.asList()
        is ShortArray -> v.asList()
        is ByteArray -> v.asList()
        is FloatArray -> v.asList()
        is DoubleArray -> v.asList()
        is BooleanArray -> v.asList()
        is CharArray -> v.asList()
        else -> emptyList<Any?>()
    }
}
