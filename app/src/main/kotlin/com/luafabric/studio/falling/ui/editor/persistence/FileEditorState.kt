package com.luafabric.studio.falling.ui.editor.persistence

import java.io.File
import java.io.Serializable
import java.util.Date

/**
 * 单个文件的编辑器状态
 */
data class FileEditorState(
    // 文件绝对路径
    val filePath: String,
    // 文件在编辑器中的显示名称
    val displayName: String = "",
    // 光标位置：行号（从0开始）
    var cursorLine: Int = 0,
    // 光标位置：列号（从0开始）
    var cursorColumn: Int = 0,
    // 垂直滚动位置
    var scrollY: Int = 0,
    // 水平滚动位置
    var scrollX: Int = 0,
    // 最后访问时间
    var lastAccessed: Date = Date(),
    // 是否已保存（没有未保存的更改）
    var isSaved: Boolean = true,
    // 文件内容摘要（用于检测文件是否被外部修改）
    var contentHash: String = ""
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L

        /**
         * 从文件创建初始状态
         */
        fun fromFile(file: File): FileEditorState {
            return FileEditorState(
                filePath = file.absolutePath,
                displayName = file.name,
                cursorLine = 0,
                cursorColumn = 0,
                scrollY = 0,
                scrollX = 0,
                lastAccessed = Date(),
                isSaved = true,
                contentHash = calculateContentHash(file)
            )
        }

        /**
         * 计算文件内容哈希（用于检测外部修改）
         */
        private fun calculateContentHash(file: File): String {
            return if (file.exists() && file.isFile) {
                try {
                    val bytes = file.readBytes()
                    java.security.MessageDigest.getInstance("MD5")
                        .digest(bytes)
                        .joinToString("") { "%02x".format(it) }
                } catch (_: Exception) {
                    ""
                }
            } else {
                ""
            }
        }
    }

    /**
     * 检查文件是否已被外部修改
     */
    fun isExternallyModified(): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false

        val currentHash = calculateContentHash(file)
        return currentHash.isNotEmpty() && currentHash != contentHash
    }

    /**
     * 更新内容哈希
     */
    fun updateContentHash() {
        val file = File(filePath)
        contentHash = calculateContentHash(file)
    }

    private fun calculateContentHash(file: File): String {
        return if (file.exists() && file.isFile) {
            try {
                val bytes = file.readBytes()
                java.security.MessageDigest.getInstance("MD5")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                ""
            }
        } else {
            ""
        }
    }

    /**
     * 检查文件是否存在
     */
    fun fileExists(): Boolean {
        val file = File(filePath)
        return file.exists() && file.isFile && file.canRead()
    }

    /**
     * 从文件创建初始状态（增强版本）
     */
    fun fromFileSafe(file: File): FileEditorState? {
        return if (file.exists() && file.isFile && file.canRead()) {
            FileEditorState(
                filePath = file.absolutePath,
                displayName = file.name,
                cursorLine = 0,
                cursorColumn = 0,
                scrollY = 0,
                scrollX = 0,
                lastAccessed = Date(),
                isSaved = true,
                contentHash = calculateContentHash(file)
            )
        } else {
            null  // 文件不存在或不可读，返回null
        }
    }

}
