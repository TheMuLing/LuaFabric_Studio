package com.luafabric.studio.falling.ui.editor.persistence

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Collections
import java.util.Date
import kotlinx.coroutines.Dispatchers as KotlinDispatchers

/**
 * 项目编辑器状态管理器
 * 负责持久化保存和恢复项目的编辑器状态
 */
class ProjectEditorStateManager private constructor(context: Context) : ViewModel() {

    companion object {
        @Volatile
        private var INSTANCE: ProjectEditorStateManager? = null

        fun getInstance(context: Context): ProjectEditorStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProjectEditorStateManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    // 应用上下文
    private val appContext = context.applicationContext

    // 内存缓存：项目路径 -> 项目状态
    private val projectStates = Collections.synchronizedMap(
        LinkedHashMap<String, ProjectEditorState>()
    )

    // 当前活动的项目状态
    var currentProjectState = mutableStateOf<ProjectEditorState?>(null)
        private set

    // 状态文件存储目录
    private val stateStorageDir: File by lazy {
        File(appContext.filesDir, "editor_states").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * 获取项目的状态文件名
     */
    private fun getStateFileName(projectPath: String): String {
        // 使用项目路径的哈希作为文件名，避免路径中的非法字符
        val hash = projectPath.hashCode().toUInt().toString(16)
        return "project_state_$hash.dat"
    }

    /**
     * 获取项目的状态文件
     */
    private fun getStateFile(projectPath: String): File {
        return File(stateStorageDir, getStateFileName(projectPath))
    }

    /**
     * 加载项目编辑器状态
     */
    suspend fun loadProjectState(projectPath: String, projectName: String): ProjectEditorState {
        return withContext(KotlinDispatchers.IO) {
            // 检查内存缓存
            projectStates[projectPath]?.let {
                return@withContext it
            }

            val stateFile = getStateFile(projectPath)

            // 从文件加载
            if (stateFile.exists() && stateFile.length() > 0) {
                try {
                    ObjectInputStream(FileInputStream(stateFile)).use { ois ->
                        val state = ois.readObject() as ProjectEditorState

                        // 验证项目路径是否匹配（防止文件误用）
                        if (state.projectPath == projectPath) {
                            // 检查文件是否仍然存在且可读
                            val validFiles = state.openFiles.filter { fileState ->
                                val file = File(fileState.filePath)
                                file.exists() && file.isFile && file.canRead()
                            }

                            val updatedState = state.copy(
                                openFiles = validFiles,
                                projectName = projectName,
                                lastUpdated = Date()
                            )

                            projectStates[projectPath] = updatedState
                            return@withContext updatedState
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 如果反序列化失败，删除损坏的文件
                    stateFile.delete()
                }
            }

            // 创建新的状态
            val newState = ProjectEditorState.empty(projectPath, projectName)
            projectStates[projectPath] = newState
            return@withContext newState
        }
    }

    /**
     * 保存项目编辑器状态
     */
    suspend fun saveProjectState(state: ProjectEditorState) {
        withContext(KotlinDispatchers.IO) {
            try {
                // 更新内存缓存
                projectStates[state.projectPath] = state

                // 保存到文件
                val stateFile = getStateFile(state.projectPath)
                ObjectOutputStream(FileOutputStream(stateFile)).use { oos ->
                    oos.writeObject(state)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 异步保存项目编辑器状态
     */
    fun saveProjectStateAsync(state: ProjectEditorState) {
        viewModelScope.launch(KotlinDispatchers.IO) {
            saveProjectState(state)
        }
    }

    /**
     * 设置当前活动项目
     */
    suspend fun setCurrentProject(projectPath: String, projectName: String) {
        val state = loadProjectState(projectPath, projectName)
        currentProjectState.value = state
    }

    /**
     * 更新当前项目状态
     */
    fun updateCurrentProjectState(updateFn: (ProjectEditorState) -> ProjectEditorState) {
        val current = currentProjectState.value ?: return
        val updated = updateFn(current)
        currentProjectState.value = updated
        saveProjectStateAsync(updated)
    }

    /**
     * 添加或更新文件到当前项目
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun addOrUpdateFileToCurrentProject(
        filePath: String,
        displayName: String = "",
        cursorLine: Int = 0,
        cursorColumn: Int = 0
    ) {
        updateCurrentProjectState { state ->
            val file = File(filePath)
            if (file.exists() && file.isFile && file.canRead()) {
                val fileState = FileEditorState(
                    filePath = filePath,
                    displayName = displayName.ifEmpty { File(filePath).name },
                    cursorLine = cursorLine,
                    cursorColumn = cursorColumn,
                    scrollY = 0,
                    scrollX = 0,
                    lastAccessed = Date(),
                    isSaved = true,
                    contentHash = ""
                ).apply {
                    updateContentHash()
                }

                state.addOrUpdateFileState(fileState)
            } else {
                // 文件不存在，从状态中移除
                state.removeFileState(filePath)
            }
        }
    }

    /**
     * 从当前项目移除文件
     */
    fun removeFileFromCurrentProject(filePath: String) {
        updateCurrentProjectState { state ->
            // 移除前检查文件是否存在
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                // 文件不存在，从状态中移除
                state.removeFileState(filePath)
            } else {
                state.removeFileState(filePath)
            }
        }
    }

    /**
     * 从当前项目移除所有文件
     */
    fun clearAllFilesFromCurrentProject() {
        updateCurrentProjectState { state ->
            state.clearAllFiles()
        }
    }

    /**
     * 从当前项目移除其他文件
     */
    fun removeOtherFilesFromCurrentProject(keepFilePath: String) {
        updateCurrentProjectState { state ->
            state.removeOtherFiles(keepFilePath)
        }
    }

    /**
     * 更新文件光标位置
     */
    fun updateFileCursorPosition(
        filePath: String,
        line: Int,
        column: Int
    ) {
        updateCurrentProjectState { state ->
            state.updateCursorPosition(filePath, line, column)
        }
    }

    /**
     * 更新文件滚动位置
     */
    fun updateFileScrollPosition(
        filePath: String,
        scrollY: Int,
        scrollX: Int = 0
    ) {
        updateCurrentProjectState { state ->
            state.updateScrollPosition(filePath, scrollY, scrollX)
        }
    }

    /**
     * 更新文件保存状态
     */
    fun updateFileSavedState(
        filePath: String,
        isSaved: Boolean
    ) {
        updateCurrentProjectState { state ->
            state.updateFileSavedState(filePath, isSaved)
        }
    }

    /**
     * 获取上次最后打开的文件路径（包含文件存在性检查）
     */
    fun getLastOpenedFile(): String? {
        return currentProjectState.value?.let { state ->
            if (state.openFiles.isNotEmpty()) {
                // 按最后访问时间排序，返回最新的
                val latestFileState = state.openFiles.maxByOrNull { it.lastAccessed }
                latestFileState?.let { fileState ->
                    // 检查文件是否存在
                    val file = File(fileState.filePath)
                    if (file.exists() && file.isFile && file.canRead()) {
                        fileState.filePath
                    } else {
                        // 文件不存在，从状态中移除
                        viewModelScope.launch {
                            removeFileFromCurrentProject(fileState.filePath)
                        }
                        null
                    }
                }
            } else {
                null
            }
        }
    }

    /**
     * 获取文件的光标位置
     */
    fun getFileCursorPosition(filePath: String): Pair<Int, Int> {
        return currentProjectState.value?.let { state ->
            state.openFiles.find { it.filePath == filePath }?.let {
                Pair(it.cursorLine, it.cursorColumn)
            } ?: Pair(0, 0)
        } ?: Pair(0, 0)
    }

    /**
     * 获取文件的滚动位置
     */
    fun getFileScrollPosition(filePath: String): Pair<Int, Int> {
        return currentProjectState.value?.let { state ->
            state.openFiles.find { it.filePath == filePath }?.let {
                Pair(it.scrollY, it.scrollX)
            } ?: Pair(0, 0)
        } ?: Pair(0, 0)
    }

    /**
     * 清理指定项目的状态
     */
    suspend fun clearProjectState(projectPath: String) {
        withContext(KotlinDispatchers.IO) {
            projectStates.remove(projectPath)
            getStateFile(projectPath).delete()

            if (currentProjectState.value?.projectPath == projectPath) {
                currentProjectState.value = null
            }
        }
    }

    /**
     * 清理所有项目状态
     */
    suspend fun clearAllStates() {
        withContext(KotlinDispatchers.IO) {
            projectStates.clear()
            currentProjectState.value = null
            stateStorageDir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * 检查并清理不存在的文件状态
     */
    suspend fun cleanupNonExistentFiles(projectPath: String) {
        withContext(KotlinDispatchers.IO) {
            val state = loadProjectState(projectPath, File(projectPath).name)

            // 检查每个文件状态对应的文件是否存在
            val existingFiles = state.openFiles.filter { fileState ->
                val file = File(fileState.filePath)
                file.exists() && file.isFile && file.canRead()
            }

            // 如果存在不存在的文件，更新状态
            if (existingFiles.size != state.openFiles.size) {
                val updatedState = state.copy(openFiles = existingFiles)
                saveProjectState(updatedState)
                if (currentProjectState.value?.projectPath == projectPath) {
                    currentProjectState.value = updatedState
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 保存所有状态
        viewModelScope.launch {
            projectStates.values.forEach { state ->
                try {
                    saveProjectState(state)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}