package com.luafabric.studio.falling.langs.lua.completion

import io.github.rosemoe.sora.lang.completion.CompletionItemKind

/**
 * 补全项名称数据类
 * 用于存储和管理代码补全项的信息
 *
 * @property name 完整名称（通常为全限定类名或方法签名）
 * @property type 补全项类型（类、方法、字段等）
 * @property description 简短的描述信息
 * @property generic 泛型参数信息
 */
data class CompletionName(
    val name: String,
    val type: CompletionItemKind,
    val description: String = "",
    val generic: String = ""
) {

    /**
     * 简化构造函数
     *
     * @param name 完整名称
     * @param type 补全项类型
     */
    constructor(name: String, type: CompletionItemKind) : this(name, type, "", "")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CompletionName

        if (name != other.name) return false
        if (type != other.type) return false
        if (description != other.description) return false
        if (generic != other.generic) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + generic.hashCode()
        return result
    }

    override fun toString(): String {
        return "CompletionName{name='$name', type=$type, description='$description', generic='$generic'}"
    }
}