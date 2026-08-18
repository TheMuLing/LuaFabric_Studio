package com.luafabric.studio.falling.ui.editor.persistence

import android.os.Build
import androidx.annotation.RequiresApi
import java.io.Serializable
import java.util.Date

/**
 * 项目编辑器状态
 */
data class ProjectEditorState(
    // 项目路径（作为项目的唯一标识）
    val projectPath: String,
    // 项目名称
    val projectName: String,
    // 所有打开的文件状态（按打开顺序排列）
    val openFiles: List<FileEditorState> = emptyList(),
    // 当前活动（聚焦）的文件索引
    var activeFileIndex: Int = 0,
    // 最后更新时间
    var lastUpdated: Date = Date(),
    // 最大保存的历史文件数量
    var maxHistorySize: Int = 20
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L

        /**
         * 创建空的项目状态
         */
        fun empty(projectPath: String, projectName: String): ProjectEditorState {
            return ProjectEditorState(
                projectPath = projectPath,
                projectName = projectName,
                openFiles = emptyList(),
                activeFileIndex = 0,
                lastUpdated = Date(),
                maxHistorySize = 20
            )
        }
    }

    /**
     * 获取当前活动文件状态
     */
    fun getActiveFileState(): FileEditorState? {
        return if (openFiles.isNotEmpty() && activeFileIndex in openFiles.indices) {
            openFiles[activeFileIndex]
        } else {
            null
        }
    }

    /**
     * 添加或更新文件状态
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun addOrUpdateFileState(fileState: FileEditorState): ProjectEditorState {
        val existingIndex = openFiles.indexOfFirst { it.filePath == fileState.filePath }

        return if (existingIndex != -1) {
            // 更新现有文件状态
            val updatedFiles = openFiles.toMutableList().apply {
                this[existingIndex] = fileState.copy(lastAccessed = Date())
            }
            this.copy(
                openFiles = updatedFiles,
                activeFileIndex = existingIndex,
                lastUpdated = Date()
            )
        } else {
            // 添加新文件状态
            val newFiles = openFiles.toMutableList().apply {
                // 如果达到最大历史限制，移除最旧的文件
                if (size >= maxHistorySize) {
                    removeFirst()
                }
                add(fileState.copy(lastAccessed = Date()))
            }
            this.copy(
                openFiles = newFiles,
                activeFileIndex = newFiles.size - 1,
                lastUpdated = Date()
            )
        }
    }

    /**
     * 移除文件状态
     */
    fun removeFileState(filePath: String): ProjectEditorState {
        val updatedFiles = openFiles.filter { it.filePath != filePath }.toMutableList()
        var newActiveIndex = activeFileIndex

        if (updatedFiles.size < openFiles.size) {
            // 文件被移除，需要调整活动索引
            if (activeFileIndex >= updatedFiles.size) {
                newActiveIndex = maxOf(0, updatedFiles.size - 1)
            }
        }

        return this.copy(
            openFiles = updatedFiles,
            activeFileIndex = newActiveIndex,
            lastUpdated = Date()
        )
    }

    /**
     * 移除所有文件状态
     */
    fun clearAllFiles(): ProjectEditorState {
        return this.copy(
            openFiles = emptyList(),
            activeFileIndex = 0,
            lastUpdated = Date()
        )
    }

    /**
     * 移除其他文件状态（只保留指定文件）
     */
    fun removeOtherFiles(keepFilePath: String): ProjectEditorState {
        val keptFile = openFiles.find { it.filePath == keepFilePath }

        return if (keptFile != null) {
            this.copy(
                openFiles = listOf(keptFile.copy(lastAccessed = Date())),
                activeFileIndex = 0,
                lastUpdated = Date()
            )
        } else {
            this
        }
    }

    /**
     * 更新光标位置
     */
    fun updateCursorPosition(
        filePath: String,
        line: Int,
        column: Int
    ): ProjectEditorState {
        val fileIndex = openFiles.indexOfFirst { it.filePath == filePath }
        if (fileIndex == -1) return this

        val updatedFiles = openFiles.toMutableList().apply {
            this[fileIndex] = this[fileIndex].copy(
                cursorLine = line,
                cursorColumn = column,
                lastAccessed = Date()
            )
        }

        return this.copy(
            openFiles = updatedFiles,
            lastUpdated = Date()
        )
    }

    /**
     * 更新滚动位置
     */
    fun updateScrollPosition(
        filePath: String,
        scrollY: Int,
        scrollX: Int = 0
    ): ProjectEditorState {
        val fileIndex = openFiles.indexOfFirst { it.filePath == filePath }
        if (fileIndex == -1) return this

        val updatedFiles = openFiles.toMutableList().apply {
            this[fileIndex] = this[fileIndex].copy(
                scrollY = scrollY,
                scrollX = scrollX,
                lastAccessed = Date()
            )
        }

        return this.copy(
            openFiles = updatedFiles,
            lastUpdated = Date()
        )
    }

    /**
     * 更新文件保存状态
     */
    fun updateFileSavedState(
        filePath: String,
        isSaved: Boolean
    ): ProjectEditorState {
        val fileIndex = openFiles.indexOfFirst { it.filePath == filePath }
        if (fileIndex == -1) return this

        val updatedFiles = openFiles.toMutableList().apply {
            this[fileIndex] = this[fileIndex].copy(
                isSaved = isSaved,
                lastAccessed = Date()
            )
        }

        return this.copy(
            openFiles = updatedFiles,
            lastUpdated = Date()
        )
    }
}