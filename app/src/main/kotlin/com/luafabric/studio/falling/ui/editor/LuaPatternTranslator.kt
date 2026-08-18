package com.luafabric.studio.falling.ui.editor

/**
 * Lua 模式（Lua pattern）→ Java 正则 翻译器。
 *
 * 判定规则：模式中含 `%` 转义序列视为 Lua 模式语法，翻译为 Java 正则；
 * 否则视为普通正则直接透传。这样"支持 Lua 正则语法则用 Lua，不支持用普通正则"。
 */
object LuaPatternTranslator {

    private val charClasses = mapOf(
        'a' to "[a-zA-Z]",
        'A' to "[^a-zA-Z]",
        'c' to "[\\x00-\\x1f\\x7f]",
        'C' to "[^\\x00-\\x1f\\x7f]",
        'd' to "\\d",
        'D' to "\\D",
        'l' to "[a-z]",
        'L' to "[^a-z]",
        'p' to "[\\x21-\\x2F\\x3A-\\x40\\x5B-\\x60\\x7B-\\x7E]",
        'P' to "[^\\x21-\\x2F\\x3A-\\x40\\x5B-\\x60\\x7B-\\x7E]",
        's' to "\\s",
        'S' to "\\S",
        'u' to "[A-Z]",
        'U' to "[^A-Z]",
        'w' to "[a-zA-Z0-9]",
        'W' to "[^a-zA-Z0-9]",
        'x' to "[0-9a-fA-F]",
        'X' to "[^0-9a-fA-F]",
        'z' to "\\x00"
    )

    /** 是否包含 Lua 模式特征（% 转义序列） */
    fun isLuaPattern(pattern: String): Boolean =
        pattern.indexOf('%') >= 0

    /**
     * 翻译 Lua 模式为 Java 正则。
     * @return 翻译结果；不支持的特性返回 null（错误信息在 [lastError]）
     */
    fun translate(pattern: String): String? {
        lastError = null
        val sb = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '%' -> {
                    if (i + 1 >= pattern.length) {
                        lastError = "模式以 '%' 结尾"
                        return null
                    }
                    val d = pattern[i + 1]
                    when (d) {
                        'b' -> {
                            if (i + 3 >= pattern.length) {
                                lastError = "'%b' 后需要两个字符"
                                return null
                            }
                            val x = pattern[i + 2]
                            val y = pattern[i + 3]
                            // 近似：支持一层嵌套的平衡匹配
                            sb.append(escapeLiteral(x))
                                .append("(?:[^").append(escapeInClass(x))
                                .append(escapeInClass(y)).append("]|")
                                .append(escapeLiteral(x)).append("[^")
                                .append(escapeInClass(y)).append("]*")
                                .append(escapeLiteral(y)).append(")*")
                                .append(escapeLiteral(y))
                            i += 4
                            continue
                        }
                        'f' -> {
                            lastError = "'%f' 边界匹配暂不支持，请改用全词匹配"
                            return null
                        }
                        '%' -> { sb.append('%'); i += 2; continue }
                        else -> {
                            val replacement = charClasses[d]
                            if (replacement != null) {
                                sb.append(replacement)
                            } else {
                                // 未知 %x 转义：按字面输出（Java 正则中 % 无特殊含义）
                                sb.append('%').append(d)
                            }
                            i += 2
                            continue
                        }
                    }
                }
                c == '[' -> {
                    // 字符类：找到匹配的 ]（允许开头的 ^）
                    var j = i + 1
                    if (j < pattern.length && pattern[j] == '^') j++
                    var content = StringBuilder()
                    var closed = false
                    while (j < pattern.length) {
                        val cc = pattern[j]
                        if (cc == ']') { closed = true; break }
                        if (cc == '%' && j + 1 < pattern.length) {
                            val dd = pattern[j + 1]
                            if (dd == '%') { content.append('%'); j += 2; continue }
                            val replacement = charClasses[dd]
                            if (replacement != null) {
                                content.append(replacement.removeSurrounding("[", "]"))
                                j += 2
                                continue
                            }
                            content.append('%').append(dd)
                            j += 2
                            continue
                        }
                        if (cc == '-' && (j + 1 >= pattern.length || pattern[j + 1] == ']')) {
                            // Lua 类内末尾的 '-' 是字面量，Java 中需转义
                            content.append("\\-")
                        } else {
                            content.append(escapeInClass(cc))
                        }
                        j++
                    }
                    if (!closed) {
                        lastError = "字符类缺少 ']'"
                        return null
                    }
                    val body = content.toString()
                    val negated = pattern[i + 1] == '^'
                    sb.append('[')
                    if (negated) sb.append('^')
                    sb.append(body)
                    sb.append(']')
                    i = j + 1
                }
                c == '-' -> {
                    // Lua 非贪婪量词
                    sb.append("*?")
                    i++
                }
                c == '^' || c == '$' -> {
                    sb.append(c)
                    i++
                }
                c == '.' || c == '+' || c == '*' || c == '?' -> {
                    sb.append(c)
                    i++
                }
                else -> {
                    if (isJavaMeta(c)) sb.append('\\').append(c) else sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    /** 最近一次翻译错误 */
    var lastError: String? = null
        private set

    private fun isJavaMeta(c: Char): Boolean = c in "\\.[]{}()*+?^$|"

    private fun escapeLiteral(c: Char): String =
        if (isJavaMeta(c)) "\\$c" else c.toString()

    private fun escapeInClass(c: Char): String = when (c) {
        '\\', ']', '^', '-', '[' -> "\\$c"
        else -> c.toString()
    }

    private fun String.removeSurrounding(prefix: String, suffix: String): String =
        if (startsWith(prefix) && endsWith(suffix)) substring(1, length - 1) else this
}
