package com.luafabric.console.output

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.IdentityHashMap

/**
 * 两级类型解析：
 * 一级 = Lua 可见类型（userdata/function/string/...），
 * 二级 = 真实类型（java 完整类名，数组解码，如 byte[] → java.lang.Byte[]）
 *         + 真实内容（数组解码内容 / 普通 toString，不混入类型名）。
 */
object TypeResolver {

    private val LUA_TYPE_NAMES = mapOf(
        -1 to "none", 0 to "nil", 1 to "boolean", 2 to "lightuserdata",
        3 to "number", 4 to "string", 5 to "table", 6 to "function",
        7 to "userdata", 8 to "thread"
    )

    data class ResolvedArg(val level1: String, val type: String, val content: String, val full: String? = null)

    fun resolve(luaType: Int, raw: Any?, depth: Int): ResolvedArg {
        val l1 = LUA_TYPE_NAMES[luaType] ?: "unknown"
        return when (l1) {
            "userdata", "lightuserdata" -> if (raw == null) {
                ResolvedArg(l1, "null", "null")
            } else {
                ResolvedArg(l1, javaTypeName(raw), singleLine(previewValue(raw)), previewValue(raw))
            }
            "table" -> ResolvedArg(l1, "table", resolveTable(raw, depth), resolveTableFull(raw, depth))
            else -> ResolvedArg(l1, javaTypeName(raw), singleLine(raw?.toString() ?: "null"), raw?.toString())
        }
    }

    private val PRIMITIVE_BOXED = mapOf(
        "boolean" to "java.lang.Boolean",
        "byte" to "java.lang.Byte",
        "char" to "java.lang.Character",
        "short" to "java.lang.Short",
        "int" to "java.lang.Integer",
        "long" to "java.lang.Long",
        "float" to "java.lang.Float",
        "double" to "java.lang.Double",
        "void" to "java.lang.Void"
    )

    /** 真实类型：剥离数组维度取基类；primitive 基类 → boxed 完整名（byte[] → java.lang.Byte[]）。 */
    private fun javaTypeName(raw: Any?): String {
        if (raw == null) return "null"
        var base: Class<*> = raw.javaClass
        var dims = 0
        while (base.isArray) {
            dims++
            base = base.componentType!!
        }
        val baseName = if (base.isPrimitive) PRIMITIVE_BOXED[base.name] ?: base.name else base.name
        return baseName + "[]".repeat(dims)
    }

    // ---------- table 行内预览 ----------

    private fun resolveTable(raw: Any?, depth: Int): String {
        if (raw !is Map<*, *>) return singleLine(raw?.toString() ?: "null")
        return tablePreview(raw, depth, IdentityHashMap())
    }

    /** table 完整内容（不截断，供复制预览）：非 Map 返回 null。 */
    private fun resolveTableFull(raw: Any?, depth: Int): String? {
        if (raw !is Map<*, *>) return null
        return tableFullPreview(raw, depth, IdentityHashMap())
    }

    private fun tableFullPreview(map: Map<*, *>, depth: Int, seen: IdentityHashMap<Any, Boolean>): String {
        if (depth <= 0 || seen.put(map, true) != null) return "{...}"
        val sb = StringBuilder("{")
        var i = 0
        for ((k, v) in map) {
            if (i > 0) sb.append(", ")
            sb.append(noCap(k?.toString() ?: "null")).append('=')
            sb.append(if (v is Map<*, *>) tableFullPreview(v, depth - 1, seen) else noCap(v?.toString() ?: ""))
            i++
        }
        return sb.append('}').toString()
    }

    /** 清空换行（仅多行变量折叠为单行），不做长度截断。 */
    private fun noCap(text: String): String = text.replace('\n', ' ')

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

    /** 内容预览（真实内容）：数组解码（避免 [B@hash 之类默认 toString），普通对象回退安全 toString。
     *  字节数组优先 UTF-8 解码为字符串，解码失败（含非法字节）回退元素列表。 */
    private fun previewValue(raw: Any): String = when (raw) {
        is BooleanArray -> raw.contentToString()
        is ByteArray -> decodeBytes(raw)
        is CharArray -> raw.contentToString()
        is ShortArray -> raw.contentToString()
        is IntArray -> raw.contentToString()
        is LongArray -> raw.contentToString()
        is FloatArray -> raw.contentToString()
        is DoubleArray -> raw.contentToString()
        is Array<*> -> if (raw.all { it is Byte }) {
            decodeBytes(ByteArray(raw.size) { raw[it] as Byte })
        } else {
            raw.contentDeepToString()
        }
        else -> safeToString(raw)
    }

    /** 严格 UTF-8 解码；失败回退字节元素列表（如 contentToString 的 [1, 2, 3]）。 */
    private fun decodeBytes(bytes: ByteArray): String =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            bytes.joinToString(", ", "[", "]") { it.toString() }
        }

    private fun safeToString(raw: Any): String =
        try {
            raw.toString()
        } catch (e: Throwable) {
            raw.javaClass.simpleName
        }

    /** 单行化 + 上限（内容行，避免超长 bloat）。 */
    private fun singleLine(text: String, cap: Int = 512): String {
        val trimmed = text.replace('\n', ' ')
        return if (trimmed.length <= cap) trimmed else trimmed.substring(0, cap) + "…"
    }

    /** 表内元素预览：短路精简化。 */
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