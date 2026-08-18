package com.luafabric.studio.falling.langs.lua.completion

/**
 * 自定义前缀检查器实现
 * 用于识别代码补全中的有效前缀字符
 */
class MyPrefixChecker : CompletionHelper.PrefixChecker {

    /**
     * 检查字符是否可作为前缀的一部分
     *
     * @param c 要检查的字符
     * @return 如果是有效的前缀字符返回 true
     */
    override fun check(c: Char): Boolean {
        return Character.isLetterOrDigit(c) ||
                c == '.' ||
                c == '_' ||
                c == '(' ||
                c == ')' ||
                c == '$'
    }
}