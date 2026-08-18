package com.luafabric.studio.falling.ui.editor.persistence

import android.content.Context
import com.luafabric.studio.falling.ui.editor.viewmodel.EditorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream

/**
 * 编辑器状态工具类
 */
object EditorStateUtil {

    /**
     * 清理指定项目的状态
     */
    suspend fun cleanProjectState(context: Context, projectPath: String) {
        withContext(Dispatchers.IO) {
            try {
                val stateManager = ProjectEditorStateManager.getInstance(context)
                stateManager.clearProjectState(projectPath)
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * 清理过期的状态文件
     */
    suspend fun cleanupExpiredStateFiles(
        context: Context,
        maxAgeDays: Int = 30
    ) {
        withContext(Dispatchers.IO) {
            try {
                ProjectEditorStateManager.getInstance(context)

                // 获取状态存储目录
                val stateDir = context.filesDir.resolve("editor_states")
                if (!stateDir.exists() || !stateDir.isDirectory) return@withContext

                val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)

                stateDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoffTime) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 检查文件是否需要重新加载（被外部修改）
     */
    suspend fun checkFileExternallyModified(
        file: File,
        lastContentHash: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            if (!file.exists() || !file.isFile) return@withContext false

            val currentHash = try {
                val bytes = file.readBytes()
                java.security.MessageDigest.getInstance("MD5")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                ""
            }

            return@withContext currentHash.isNotEmpty() && currentHash != lastContentHash
        }
    }

    /**
     * 获取建议的打开文件列表（基于历史记录）
     */
    suspend fun getSuggestedFilesToOpen(
        projectPath: String,
        projectName: String,
        context: Context
    ): List<File> {
        return withContext(Dispatchers.IO) {
            val stateManager = ProjectEditorStateManager.getInstance(context)
            stateManager.setCurrentProject(projectPath, projectName)

            val historyFiles = stateManager.currentProjectState.value?.openFiles ?: emptyList()

            historyFiles.mapNotNull { fileState ->
                val file = File(fileState.filePath)
                if (file.exists() && file.isFile && file.canRead()) {
                    file
                } else {
                    null
                }
            }
        }
    }

    /**
     * 批量清理所有项目中不存在的文件状态
     */
    suspend fun cleanupAllNonExistentFiles(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val stateManager = ProjectEditorStateManager.getInstance(context)

                // 获取所有状态文件
                val stateDir = context.filesDir.resolve("editor_states")
                if (!stateDir.exists() || !stateDir.isDirectory) return@withContext

                val stateFiles = stateDir.listFiles() ?: return@withContext

                for (stateFile in stateFiles) {
                    try {
                        ObjectInputStream(FileInputStream(stateFile)).use { ois ->
                            val state = ois.readObject() as ProjectEditorState

                            // 检查每个文件是否存在
                            val existingFiles = state.openFiles.filter { fileState ->
                                val file = File(fileState.filePath)
                                file.exists() && file.isFile && file.canRead()
                            }

                            // 如果存在不存在的文件，更新并保存状态
                            if (existingFiles.size != state.openFiles.size) {
                                val updatedState = state.copy(openFiles = existingFiles)
                                stateManager.saveProjectState(updatedState)
                            }
                        }
                    } catch (_: Exception) {
                        // 文件损坏，删除它
                        stateFile.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 批量清理所有项目中不存在的文件状态（使用协程作用域）
     */
    fun cleanupAllNonExistentFiles(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            cleanupAllNonExistentFiles(context)
        }
    }

    /**
     * 自动保存当前打开的编辑器文件
     */
    suspend fun autoSaveOpenFiles(
        context: Context,
        viewModel: EditorViewModel  // 这里使用导入的EditorViewModel
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 使用新的静默保存方法
                viewModel.saveAllFilesSilently()
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

}
