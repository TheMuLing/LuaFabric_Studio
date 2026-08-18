package com.luafabric.studio.falling.langs.lua

import java.util.Stack

/**
 * 语法分析状态
 * 保存分词过程中的状态信息
 */
class State {

    /**
     * 当前状态
     * 0: 正常状态
     * 1: 不完整注释
     * 2: 不完整长字符串
     */
    var state = 0

    /**
     * 是否包含大括号
     */
    var hasBraces = false

    /**
     * 标识符列表
     */
    var identifiers: MutableList<String>? = null

    /**
     * 字符栈
     */
    val stack = Stack<Char>()

    /**
     * 长注释等号计数
     */
    var longCommentEqualCount = 0

    /**
     * 长字符串等号计数
     */
    var longStringEqualCount = 0

    /**
     * 是否在长字符串中
     */
    var inLongString = false

    /**
     * 添加标识符
     *
     * @param charSequence 标识符字符序列
     */
    fun addIdentifier(charSequence: CharSequence) {
        if (identifiers == null) {
            identifiers = ArrayList()
        }
        identifiers!!.add(charSequence.toString())
    }

    /**
     * 比较状态是否相等
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as State

        return state == other.state &&
                hasBraces == other.hasBraces &&
                longCommentEqualCount == other.longCommentEqualCount &&
                longStringEqualCount == other.longStringEqualCount &&
                inLongString == other.inLongString
    }

    /**
     * 计算哈希值
     */
    override fun hashCode(): Int {
        var result = state
        result = 31 * result + hasBraces.hashCode()
        result = 31 * result + longCommentEqualCount
        result = 31 * result + longStringEqualCount
        result = 31 * result + inLongString.hashCode()
        return result
    }
}