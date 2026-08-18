package com.luafabric.studio.falling.langs.lua

/**
 * 语法高亮标记
 * 包含标记类型、偏移量和可选的URL链接
 *
 * @property token 标记类型
 * @property offset 在行中的偏移量
 * @property url 可选的URL链接
 * @property text 标记文本
 */
class HighlightToken(
    val token: Tokens,
    val offset: Int,
    var url: String? = null,
    var text: String? = null
) {
    /**
     * 构造函数
     *
     * @param token 标记类型
     * @param offset 在行中的偏移量
     */
    constructor(token: Tokens, offset: Int) : this(token, offset, null, null)

    /**
     * 构造函数
     *
     * @param token 标记类型
     * @param offset 在行中的偏移量
     * @param url URL链接
     */
    constructor(token: Tokens, offset: Int, url: String) : this(token, offset, url, null)
}