package com.luafabric.studio.falling.ui.editor.viewmodel

import android.graphics.Paint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.luafabric.studio.falling.ui.editor.persistence.ProjectEditorStateManager
import com.luafabric.studio.falling.langs.lua.LuaIncrementalAnalyzeManager
import com.luafabric.studio.falling.langs.lua.LuaLanguage
import com.luafabric.studio.falling.langs.lua.completion.CompletionName
import com.luafabric.studio.falling.ui.editor.EditorColorSchemeManager
import com.luafabric.studio.falling.ui.editor.LuaPatternTranslator
import com.luafabric.studio.falling.ui.settings.FontManager
import com.luafabric.studio.falling.ui.settings.SettingsManager
import muling.views.tool.utils.LogCatcher
import muling.views.tool.utils.NonBlockingToastState
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SubscriptionReceipt
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.Dispatchers as KotlinDispatchers

// DataStore 实例
private val Context.symbolDataStore by preferencesDataStore(name = "symbol_frequency")
private val Context.quickActionDataStore by preferencesDataStore(name = "quick_action_frequency")

data class CodeEditorState(
    val file: File,
    val languageType: String,
) {
    var content by mutableStateOf("")
    var savedContent by mutableStateOf("")
    val isModified: Boolean get() = content != savedContent
    var contentHash by mutableStateOf("")
        private set

    fun onContentLoaded(loadedContent: String) {
        content = loadedContent
        savedContent = loadedContent
        updateContentHash()
    }

    fun onContentSaved() {
        savedContent = content
        updateContentHash()
    }

    fun updateContentHash() {
        contentHash = calculateContentHash(content)
    }

    private fun calculateContentHash(text: String): String = try {
        val bytes = text.toByteArray(Charsets.UTF_8)
        java.security.MessageDigest.getInstance("MD5")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        ""
    }
}

class EditorViewModel : ViewModel(), CompletionDataManager.OnCompletionDataListener {
    var hasShownInitialLoader by mutableStateOf(false); private set
    var openFiles by mutableStateOf<List<CodeEditorState>>(emptyList()); private set
    var activeFileIndex by mutableIntStateOf(-1); private set
    var isCompletionDataLoading by mutableStateOf(false); private set
    var completionDataProgress by mutableStateOf(0f)    // 新增进度状态
    private var _isInitialized by mutableStateOf(false)
    val isInitialized: Boolean get() = _isInitialized
    val activeFileState: CodeEditorState?
        get() = if (activeFileIndex in openFiles.indices) openFiles[activeFileIndex] else null

    private val editorInstances = mutableMapOf<String, CodeEditor>()
    private val cursorListeners = mutableMapOf<String, SubscriptionReceipt<SelectionChangeEvent>>()
    private lateinit var appContext: Context
    private lateinit var stateManager: ProjectEditorStateManager

    private var currentEditorFontType = SettingsManager.currentSettings.editorFontType
    private var currentCustomFontPath = SettingsManager.currentSettings.customFontPath
    private var currentIndentGuideEnabled by mutableStateOf(SettingsManager.currentSettings.indentGuideEnabled)
    private var currentSyntaxColors by mutableStateOf(
        mapOf(
            "className" to SettingsManager.currentSettings.classNameColor,
            "localVariable" to SettingsManager.currentSettings.localVariableColor,
            "keyword" to SettingsManager.currentSettings.keywordColor,
            "functionName" to SettingsManager.currentSettings.functionNameColor,
            "literal" to SettingsManager.currentSettings.literalColor,
            "comment" to SettingsManager.currentSettings.commentColor,
            "selectedLine" to SettingsManager.currentSettings.selectedLineColor
        )
    )
    private var currentProjectPath: String? = null
    var completionDataLoaded by mutableStateOf(false); private set

    // ---------- 选中的短类名 ----------
    var selectedClassName by mutableStateOf<String?>(null)
        private set
    var selectedClassCandidates by mutableStateOf<List<String>?>(null)
        private set

    // ---------- 导航到 API 阅览器的类名 ----------
    private var _navigateToApiClass by mutableStateOf<String?>(null)
    val navigateToApiClass: String? get() = _navigateToApiClass

    fun consumeNavigateToApiClass(): String? {
        val result = _navigateToApiClass
        _navigateToApiClass = null
        return result
    }

    fun requestNavigateToApiClass(className: String) {
        _navigateToApiClass = className
    }

    // 根据选中的短类名检查是否存在
    fun checkAndSetSelectedClass(selectedText: String) {
        val (classMap, _, _) = CompletionDataManager.getCompletionData()
        if (classMap == null) {
            clearSelectedClass()
            return
        }
        val candidates = classMap[selectedText]
        if (candidates != null && candidates.isNotEmpty()) {
            selectedClassName = selectedText
            selectedClassCandidates = candidates
        } else {
            clearSelectedClass()
        }
    }

    fun clearSelectedClass() {
        selectedClassName = null
        selectedClassCandidates = null
    }
    // ----------------------------------------

    // 符号频率
    val symbolFrequencyMap = mutableStateMapOf<String, Int>()
    fun incrementSymbolFrequency(symbol: String) {
        symbolFrequencyMap[symbol] = (symbolFrequencyMap[symbol] ?: 0) + 1
        viewModelScope.launch { saveSymbolFrequency() }
    }

    private suspend fun loadSymbolFrequency() {
        try {
            val preferences = appContext.symbolDataStore.data.first()
            val json = preferences[stringPreferencesKey("frequency_map")] ?: return
            val type = object : TypeToken<Map<String, Int>>() {}.type
            val map: Map<String, Int> = Gson().fromJson(json, type)
            symbolFrequencyMap.clear()
            symbolFrequencyMap.putAll(map)
        } catch (e: Exception) {
            LogCatcher.e("EditorViewModel", "加载符号频率失败", e)
        }
    }

    private suspend fun saveSymbolFrequency() {
        try {
            val map = symbolFrequencyMap.toMap()
            val json = Gson().toJson(map)
            appContext.symbolDataStore.edit { preferences ->
                preferences[stringPreferencesKey("frequency_map")] = json
            }
        } catch (e: Exception) {
            LogCatcher.e("EditorViewModel", "保存符号频率失败", e)
        }
    }

    // 快捷功能频率
    val quickActionFrequencyMap = mutableStateMapOf<String, Int>()
    var isQuickActionFrequencyLoaded by mutableStateOf(false); private set
    fun incrementQuickActionFrequency(actionLabel: String) {
        quickActionFrequencyMap[actionLabel] = (quickActionFrequencyMap[actionLabel] ?: 0) + 1
        viewModelScope.launch { saveQuickActionFrequency() }
    }

    private suspend fun loadQuickActionFrequency() {
        try {
            val preferences = appContext.quickActionDataStore.data.first()
            val json = preferences[stringPreferencesKey("frequency_map")] ?: return
            val type = object : TypeToken<Map<String, Int>>() {}.type
            val map: Map<String, Int> = Gson().fromJson(json, type)
            quickActionFrequencyMap.clear()
            quickActionFrequencyMap.putAll(map)
        } catch (e: Exception) {
            LogCatcher.e("EditorViewModel", "加载快捷功能频率失败", e)
        } finally {
            isQuickActionFrequencyLoaded = true
        }
    }

    private suspend fun saveQuickActionFrequency() {
        try {
            val map = quickActionFrequencyMap.toMap()
            val json = Gson().toJson(map)
            appContext.quickActionDataStore.edit { preferences ->
                preferences[stringPreferencesKey("frequency_map")] = json
            }
        } catch (e: Exception) {
            LogCatcher.e("EditorViewModel", "保存快捷功能频率失败", e)
        }
    }

    // 布局助手返回
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun replaceCurrentFileContent(newContent: String) {
        val state = activeFileState ?: return
        val filePath = state.file.absolutePath
        val currentIndex = activeFileIndex
        val file = state.file
        runBlocking(KotlinDispatchers.IO) {
            try {
                if (file.exists() && file.canWrite()) {
                    file.writeText(newContent, Charsets.UTF_8)
                    LogCatcher.i("EditorViewModel", "布局助手返回，已同步保存文件: $filePath")
                } else {
                    LogCatcher.w("EditorViewModel", "文件不可写，无法保存: $filePath")
                }
            } catch (e: Exception) {
                LogCatcher.e("EditorViewModel", "布局助手返回保存文件失败: ${file.name}", e)
            }
        }
        closeFile(currentIndex)
        viewModelScope.launch {
            openFile(file)
            getActiveEditor()?.let { editor ->
                editor.post { editor.setSelection(0, 0) }
            }
            LogCatcher.i("EditorViewModel", "布局助手返回：文件已重新打开，状态完全重置")
        }
    }

    // 补全数据监听
    override fun onProgress(progress: Float) {
    LogCatcher.d("EditorViewModel", "onProgress: $progress")
    viewModelScope.launch(KotlinDispatchers.Main.immediate) {
        completionDataProgress = progress
    }
}

    override fun onCompletionDataLoaded(
        classMap: HashMap<String, List<String>>?,
        baseMap: HashMap<String, HashMap<String, CompletionName>>?,
        androidClasses: MutableSet<String>?
    ) {
        LogCatcher.i(
            "EditorViewModel",
            "onCompletionDataLoaded 被调用，classMap=${classMap?.size}, baseMap=${baseMap?.size}, androidClasses=${androidClasses?.size}"
        )
        updateAllEditorsCompletionData(classMap, baseMap, androidClasses)
        viewModelScope.launch(KotlinDispatchers.Main) {
            completionDataLoaded = true
            isCompletionDataLoading = false
            completionDataProgress = 1f   // 确保进度到头
            LogCatcher.i(
                "EditorViewModel",
                "设置 completionDataLoaded=true, isCompletionDataLoading=false"
            )
        }
    }

    override fun onCompletionDataLoadFailed(error: String?) {
        LogCatcher.e("EditorViewModel", "onCompletionDataLoadFailed: $error")
        editorInstances.values.forEach { editor ->
            try {
                val language = LuaLanguage()
                language.setCompletionCaseSensitive(SettingsManager.currentSettings.completionCaseSensitive)
                .setHighlightHexColorsEnabled(SettingsManager.currentSettings.hexColorHighlightEnabled)
                editor.setEditorLanguage(language)
            } catch (e: Exception) {
                LogCatcher.e("EditorViewModel", "创建基础LuaLanguage失败", e)
            }
        }
        viewModelScope.launch(KotlinDispatchers.Main) {
            completionDataLoaded = false
            isCompletionDataLoading = false
            completionDataProgress = 0f   // 重置进度
            LogCatcher.i(
                "EditorViewModel",
                "设置 completionDataLoaded=false, isCompletionDataLoading=false"
            )
        }
    }

    // 初始化
    fun initialize(context: Context) {
        if (_isInitialized) {
            // ViewModel 复用（重入）：检查补全数据是否已加载完成但状态未同步
            if (isCompletionDataLoading && CompletionDataManager.isInitialized()) {
                isCompletionDataLoading = false
                completionDataLoaded = true
                completionDataProgress = 1f
                LogCatcher.i("EditorViewModel", "ViewModel 复用：补全数据已加载完成，同步状态")
            }
            LogCatcher.i("EditorViewModel", "initialize 已初始化，跳过")
            return
        }
        LogCatcher.i("EditorViewModel", "initialize 开始")
        appContext = context.applicationContext
        stateManager = ProjectEditorStateManager.getInstance(context)
        CompletionDataManager.addListener(this)
        if (CompletionDataManager.isInitialized()) {
            LogCatcher.i("EditorViewModel", "CompletionDataManager 已初始化，立即获取数据")
            val (classMap, baseMap, androidClasses) = CompletionDataManager.getCompletionData()
            updateAllEditorsCompletionData(classMap, baseMap, androidClasses)
            completionDataLoaded = true
            isCompletionDataLoading = false
            completionDataProgress = 1f
            LogCatcher.i(
                "EditorViewModel",
                "已从 CompletionDataManager 获取数据，设置 completionDataLoaded=true, isCompletionDataLoading=false"
            )
        } else {
            LogCatcher.i(
                "EditorViewModel",
                "CompletionDataManager 未初始化，设置 isCompletionDataLoading=true"
            )
            isCompletionDataLoading = true
            completionDataProgress = 0f
            CompletionDataManager.initialize(context)

            // 安全网超时：120秒后若补全数据仍未加载完成则强制结束，避免进度条卡死
            // 正常情况 should never reach here，因为 CompletionDataManager 完成后会回调 onCompletionDataLoaded
            viewModelScope.launch {
                delay(120_000)
                if (isCompletionDataLoading) {
                    isCompletionDataLoading = false
                    LogCatcher.w("EditorViewModel", "补全数据加载超时（120s），强制结束加载状态")
                }
            }
        }

        SettingsManager.addListener { newSettings ->
            val fontChanged = newSettings.editorFontType != currentEditorFontType ||
                    newSettings.customFontPath != currentCustomFontPath
            if (fontChanged) {
                updateEditorFonts()
                currentEditorFontType = newSettings.editorFontType
                currentCustomFontPath = newSettings.customFontPath
            }
            val indentGuideChanged = newSettings.indentGuideEnabled != currentIndentGuideEnabled
            if (indentGuideChanged) {
                updateEditorIndentGuides()
                currentIndentGuideEnabled = newSettings.indentGuideEnabled
            }
            val newSyntaxColors = mapOf(
                "className" to newSettings.classNameColor,
                "localVariable" to newSettings.localVariableColor,
                "keyword" to newSettings.keywordColor,
                "functionName" to newSettings.functionNameColor,
                "literal" to newSettings.literalColor,
                "comment" to newSettings.commentColor,
                "selectedLine" to newSettings.selectedLineColor
            )
            if (newSyntaxColors != currentSyntaxColors) {
                currentSyntaxColors = newSyntaxColors
                updateEditorSyntaxColors(newSettings)
            }
            editorInstances.values.forEach { editor ->
                (editor.editorLanguage as? LuaLanguage)?.setCompletionCaseSensitive(newSettings.completionCaseSensitive)
                ?.setHighlightHexColorsEnabled(SettingsManager.currentSettings.hexColorHighlightEnabled)
            }
        }

        viewModelScope.launch {
            loadSymbolFrequency()
            loadQuickActionFrequency()
        }
        _isInitialized = true
        LogCatcher.i("EditorViewModel", "initialize 完成")
    }

    private fun updateAllEditorsCompletionData(
        classMap: HashMap<String, List<String>>?,
        baseMap: HashMap<String, HashMap<String, CompletionName>>?,
        androidClasses: MutableSet<String>?
    ) {
        LogCatcher.i(
            "EditorViewModel",
            "updateAllEditorsCompletionData 开始，编辑器数量: ${editorInstances.size}"
        )
        editorInstances.values.forEach { editor ->
            try {
                val language = editor.editorLanguage
                if (language is LuaLanguage) {
                    androidClasses?.let { classes ->
                        val analyzer = language.analyzeManager
                        if (analyzer is LuaIncrementalAnalyzeManager) {
                            analyzer.setClassMap(classes)
                            baseMap?.let { bMap ->
                                classMap?.let { cMap ->
                                    val newLanguage = LuaLanguage(bMap, cMap, classes.toTypedArray()).apply {
                                        setCompletionCaseSensitive(SettingsManager.currentSettings.completionCaseSensitive)
                                        setHighlightHexColorsEnabled(SettingsManager.currentSettings.hexColorHighlightEnabled)
                                    }
                                    editor.setEditorLanguage(newLanguage)
                                    LogCatcher.i("EditorViewModel", "已更新编辑器语言")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                LogCatcher.e("EditorViewModel", "更新编辑器补全数据失败", e)
            }
        }
    }

    // 编辑器外观更新
    fun updateEditorIndentGuides() {
        val settings = SettingsManager.currentSettings
        val baseFlags = 0
        editorInstances.values.forEach { editor ->
            editor.nonPrintablePaintingFlags = if (settings.indentGuideEnabled) 81 else baseFlags
            editor.postInvalidate()
        }
    }

    fun changeActiveFileIndex(index: Int) {
        if (index in openFiles.indices) activeFileIndex = index
    }

    fun getActiveEditor(): CodeEditor? = if (activeFileIndex in openFiles.indices) {
        editorInstances[openFiles[activeFileIndex].file.absolutePath]
    } else null

    fun insertSymbolToCorrectEditor(symbol: String, selectionOffset: Int = symbol.length) {
        val validIndex = activeFileIndex.coerceIn(0, maxOf(0, openFiles.size - 1))
        if (activeFileIndex != validIndex) activeFileIndex = validIndex
        if (activeFileIndex in openFiles.indices) {
            val state = openFiles[activeFileIndex]
            val filePath = state.file.absolutePath
            val editor = editorInstances[filePath]
            if (editor != null) {
                editor.requestFocus()
                val validOffset = selectionOffset.coerceIn(0, symbol.length)
                editor.insertText(symbol, validOffset)
                val newText = editor.text.toString()
                if (state.content != newText) state.content = newText
            } else {
                val newEditor = getOrCreateEditor(appContext, state)
                newEditor.requestFocus()
                newEditor.insertText(symbol, selectionOffset.coerceIn(0, symbol.length))
                val newText = newEditor.text.toString()
                if (state.content != newText) state.content = newText
            }
        }
    }

    // 项目状态管理
    suspend fun setCurrentProject(projectPath: String, projectName: String) {
        if (!_isInitialized) throw IllegalStateException("EditorViewModel must be initialized before calling setCurrentProject")
        currentProjectPath = projectPath
        stateManager.setCurrentProject(projectPath, projectName)
    }

    suspend fun removeFileFromHistory(filePath: String) = withContext(KotlinDispatchers.IO) {
        stateManager.removeFileFromCurrentProject(filePath)
    }

    suspend fun getLastOpenedFile(): File? = withContext(KotlinDispatchers.IO) {
        val filePath = stateManager.getLastOpenedFile()
        filePath?.let { path ->
            val file = File(path)
            if (file.exists() && file.isFile && file.canRead()) file
            else {
                stateManager.removeFileFromCurrentProject(path)
                null
            }
        }
    }

    suspend fun getAllHistoryFiles(): List<File> = withContext(KotlinDispatchers.IO) {
        stateManager.currentProjectState.value?.openFiles?.let { fileStates ->
            fileStates.sortedByDescending { it.lastAccessed }
                .mapNotNull { fileState ->
                    val file = File(fileState.filePath)
                    if (file.exists() && file.isFile && file.canRead()) file
                    else {
                        stateManager.removeFileFromCurrentProject(fileState.filePath)
                        null
                    }
                }
        } ?: emptyList()
    }

    // 光标/滚动
    fun getFileCursorPosition(filePath: String): Pair<Int, Int> {
        if (!_isInitialized) {
            LogCatcher.w("EditorViewModel", "getFileCursorPosition called before initialization")
            return Pair(0, 0)
        }
        return stateManager.getFileCursorPosition(filePath)
    }

    fun updateCursorPosition(filePath: String, line: Int, column: Int) {
        viewModelScope.launch(KotlinDispatchers.IO) {
            stateManager.updateFileCursorPosition(filePath, line, column)
        }
    }

    fun updateFileSavedState(filePath: String, isSaved: Boolean) {
        viewModelScope.launch(KotlinDispatchers.IO) {
            stateManager.updateFileSavedState(filePath, isSaved)
        }
    }

    private fun restoreCursorPosition(filePath: String, line: Int, column: Int) {
        editorInstances[filePath]?.let { editor ->
            editor.post {
                try {
                    val lineCount = editor.text.lineCount
                    val targetLine = line.coerceIn(0, maxOf(0, lineCount - 1))
                    val lineLength = editor.text.getColumnCount(targetLine)
                    val targetColumn = column.coerceIn(0, lineLength)
                    editor.setSelection(targetLine, targetColumn)
                } catch (e: Exception) {
                    LogCatcher.e("EditorViewModel", "恢复光标位置失败", e)
                }
            }
        }
    }

    fun resetForNewProject(projectPath: String) {
        cleanupEditors()
        openFiles = emptyList()
        activeFileIndex = -1
        hasShownInitialLoader = false
        currentProjectPath = projectPath
    }

    fun onInitialLoaderShown() {
        hasShownInitialLoader = true
    }

    fun forceResetCompletionLoading() {
        isCompletionDataLoading = false
        LogCatcher.w("EditorViewModel", "强制重置补全数据加载状态")
    }

    fun updateEditorTheme(
        seedColor: Color, isDark: Boolean,
        primaryColor: Int, backgroundColor: Int, onBackgroundColor: Int,
        outlineColor: Int, surfaceColor: Int, onSurfaceColor: Int
    ) {
        val settings = SettingsManager.currentSettings
        editorInstances.values.forEach { editor ->
            EditorColorSchemeManager.applyNonComposableThemeColors(
                scheme = editor.colorScheme,
                seedColor = seedColor,
                isDark = isDark,
                primaryColor = primaryColor,
                backgroundColor = backgroundColor,
                onBackgroundColor = onBackgroundColor,
                outlineColor = outlineColor,
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor,
                classNameColor = settings.classNameColor.toArgb(),
                localVariableColor = settings.localVariableColor.toArgb(),
                keywordColor = settings.keywordColor.toArgb(),
                functionNameColor = settings.functionNameColor.toArgb(),
                literalColor = settings.literalColor.toArgb(),
                commentColor = settings.commentColor.toArgb(),
                selectedLineColor = settings.selectedLineColor.toArgb()
            )
            editor.postInvalidate()
        }
    }

    fun updateEditorSyntaxColors(settings: com.luafabric.studio.falling.ui.settings.SettingsData) {
        editorInstances.values.forEach { editor ->
            val scheme = editor.colorScheme
            scheme.setColor(EditorColorScheme.CLASS_NAME, settings.classNameColor.toArgb())
            scheme.setColor(EditorColorScheme.LOCAL_VARIABLE, settings.localVariableColor.toArgb())
            scheme.setColor(EditorColorScheme.KEYWORD, settings.keywordColor.toArgb())
            scheme.setColor(EditorColorScheme.FUNCTION_NAME, settings.functionNameColor.toArgb())
            scheme.setColor(EditorColorScheme.LITERAL, settings.literalColor.toArgb())
            scheme.setColor(EditorColorScheme.COMMENT, settings.commentColor.toArgb())
            scheme.setColor(
                EditorColorScheme.SELECTED_TEXT_BACKGROUND,
                settings.selectedLineColor.toArgb()
            )
            editor.postInvalidate()
        }
    }

    fun updateEditorFonts() {
        editorInstances.values.forEach { editor ->
            FontManager.getEditorTypeface(appContext, SettingsManager.currentSettings)
                ?.let { typeface ->
                    editor.setTypefaceText(typeface)
                    editor.setTypefaceLineNumber(typeface)
                    editor.getComponent(EditorAutoCompletion::class.java).setEnabledAnimation(true)
                }
        }
    }

    fun formatCode() {
        openFiles.getOrNull(activeFileIndex)?.let { state ->
            editorInstances[state.file.absolutePath]?.formatCodeAsync()
        }
    }

    fun cleanupEditors() {
        cursorListeners.values.forEach { receipt ->
            try {
                receipt.unsubscribe()
            } catch (e: Exception) {
                LogCatcher.e("EditorViewModel", "取消订阅光标监听器失败", e)
            }
        }
        cursorListeners.clear()
        editorInstances.values.forEach { editor ->
            try {
                (editor.parent as? ViewGroup)?.removeView(editor)
                editor.release()
            } catch (e: Exception) {
                LogCatcher.e("EditorViewModel", "清理编辑器失败", e)
            }
        }
        editorInstances.clear()
    }

    @Synchronized
    fun getOrCreateEditor(context: Context, state: CodeEditorState): CodeEditor {
        val filePath = state.file.absolutePath
        val isActiveEditor = activeFileIndex in openFiles.indices &&
                openFiles[activeFileIndex].file.absolutePath == filePath

        editorInstances[filePath]?.let { existingEditor ->
            existingEditor.post {
                val currentText = existingEditor.text.toString()
                if (currentText != state.content) {
                    existingEditor.setText(state.content)
                    LogCatcher.d("EditorViewModel", "缓存编辑器内容不同步，已修正: $filePath")
                }
                if (isActiveEditor) existingEditor.requestFocus() else existingEditor.clearFocus()
            }
            (existingEditor.parent as? ViewGroup)?.removeView(existingEditor)
            return existingEditor
        }

        val (cursorLine, cursorColumn) = getFileCursorPosition(filePath)
        val settings = SettingsManager.currentSettings
        val luaLanguage = createLuaLanguageWithCompletion()

        val editor = CodeEditor(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setText(state.content)
            setTextSizePx(45f)
            
            setLineNumberAlign(Paint.Align.CENTER)
            lineNumberMarginLeft = 0f
            isDisplayLnPanel = false
            setHardwareAcceleratedDrawAllowed(true)
            isCursorAnimationEnabled = false
            isStickyTextSelection = true
            isVerticalScrollBarEnabled = true
            val density = context.resources.displayMetrics.density
            verticalScrollbarThumbDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8f * density
                setColor(Color.Transparent.copy(alpha = 0.2f).toArgb())
            }
            
            isHorizontalScrollBarEnabled = false
            tabWidth = 4
            isWordwrap = false
            nonPrintablePaintingFlags = if (settings.indentGuideEnabled) 81 else 0
            setEditorLanguage(luaLanguage)
            colorScheme = EditorColorScheme()
            EditorColorSchemeManager.applyNonComposableThemeColors(
                scheme = colorScheme,
                seedColor = when (settings.themeType) {
                    com.luafabric.studio.falling.ui.theme.ThemeType.GREEN -> Color(0xFF2E6A44)
                    com.luafabric.studio.falling.ui.theme.ThemeType.BLUE -> Color(0xFF36618E)
                    com.luafabric.studio.falling.ui.theme.ThemeType.PINK -> Color(0xFF8D4A5A)
                },
                isDark = when (settings.darkMode) {
                    com.luafabric.studio.falling.ui.settings.DarkMode.FOLLOW_SYSTEM ->
                        android.content.res.Configuration.UI_MODE_NIGHT_YES ==
                                context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK

                    com.luafabric.studio.falling.ui.settings.DarkMode.DARK -> true
                    com.luafabric.studio.falling.ui.settings.DarkMode.LIGHT -> false
                },
                primaryColor = Color.Transparent.toArgb(),
                backgroundColor = Color.Transparent.toArgb(),
                onBackgroundColor = Color.Transparent.toArgb(),
                outlineColor = Color.Transparent.toArgb(),
                surfaceColor = Color.Transparent.toArgb(),
                onSurfaceColor = Color.Transparent.toArgb(),
                classNameColor = settings.classNameColor.toArgb(),
                localVariableColor = settings.localVariableColor.toArgb(),
                keywordColor = settings.keywordColor.toArgb(),
                functionNameColor = settings.functionNameColor.toArgb(),
                literalColor = settings.literalColor.toArgb(),
                commentColor = settings.commentColor.toArgb(),
                selectedLineColor = settings.selectedLineColor.toArgb()
            )
            FontManager.getEditorTypeface(context, SettingsManager.currentSettings)
                ?.let { typeface ->
                    setTypefaceText(typeface)
                    setTypefaceLineNumber(typeface)
                    getComponent(EditorAutoCompletion::class.java).setEnabledAnimation(true)
                }
            post {
                try {
                    val lineCount = text.lineCount
                    val targetLine = cursorLine.coerceIn(0, maxOf(0, lineCount - 1))
                    val lineLength = text.getColumnCount(targetLine)
                    val targetColumn = cursorColumn.coerceIn(0, lineLength)
                    setSelection(targetLine, targetColumn)
                } catch (e: Exception) {
                    LogCatcher.e("EditorViewModel", "设置光标位置失败", e)
                }
            }
            isFocusable = true
            isFocusableInTouchMode = true
            if (isActiveEditor) {
                post {
                    try {
                        requestFocus()
                    } catch (e: Exception) {
                        LogCatcher.e("EditorViewModel", "请求焦点失败", e)
                    }
                }
            }
            text.addContentListener(object : io.github.rosemoe.sora.text.ContentListener {
                override fun beforeReplace(content: io.github.rosemoe.sora.text.Content) {}
                override fun afterInsert(
                    content: io.github.rosemoe.sora.text.Content,
                    startLine: Int,
                    startColumn: Int,
                    endLine: Int,
                    endColumn: Int,
                    inserted: CharSequence
                ) {
                    val newText = content.toString()
                    if (state.content != newText) state.content = newText
                }

                override fun afterDelete(
                    content: io.github.rosemoe.sora.text.Content,
                    startLine: Int,
                    startColumn: Int,
                    endLine: Int,
                    endColumn: Int,
                    deleted: CharSequence
                ) {
                    val newText = content.toString()
                    if (state.content != newText) state.content = newText
                }
            })
        }

        val receipt = editor.subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
            updateCursorPosition(filePath, editor.cursor.leftLine, editor.cursor.leftColumn)
        }
        cursorListeners[filePath] = receipt
        editorInstances[filePath] = editor
        return editor
    }

    private fun createLuaLanguageWithCompletion(): LuaLanguage {
        val (classMap, baseMap, androidClasses) = CompletionDataManager.getCompletionData()
        return if (classMap != null && baseMap != null && androidClasses != null) {
            LuaLanguage(baseMap, classMap, androidClasses.toTypedArray()).apply {
                setCompletionCaseSensitive(SettingsManager.currentSettings.completionCaseSensitive)
                setHighlightHexColorsEnabled(SettingsManager.currentSettings.hexColorHighlightEnabled)
            }
        } else {
            LuaLanguage().apply {
                setCompletionCaseSensitive(SettingsManager.currentSettings.completionCaseSensitive)
                setHighlightHexColorsEnabled(SettingsManager.currentSettings.hexColorHighlightEnabled)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        LogCatcher.i("EditorViewModel", "onCleared")
        cleanupEditors()
        SettingsManager.removeListener { }
        CompletionDataManager.removeListener(this)
        editorInstances.values.forEach { (it.editorLanguage as? LuaLanguage)?.releaseMemory() }
        isCompletionDataLoading = false
        completionDataLoaded = false
        _isInitialized = false
    }

    // 保存文件
    suspend fun saveAllModifiedFiles(toast: NonBlockingToastState) {
        withContext(KotlinDispatchers.IO) {
            val modifiedFiles = openFiles.filter { it.isModified }
            if (modifiedFiles.isEmpty()) {
                withContext(KotlinDispatchers.Main) { toast.showToast("没有需要保存的文件") }
                return@withContext
            }
            var successCount = 0
            val errorMessages = mutableListOf<String>()
            modifiedFiles.forEach { state ->
                try {
                    if (!state.file.canWrite()) {
                        errorMessages.add("文件 ${state.file.name} 不可写")
                        return@forEach
                    }
                    state.file.outputStream().bufferedWriter(Charsets.UTF_8)
                        .use { it.write(state.content) }
                    state.onContentSaved()
                    updateFileSavedState(state.file.absolutePath, true)
                    successCount++
                } catch (e: Exception) {
                    LogCatcher.e("EditorViewModel", "保存文件失败: ${state.file.name}", e)
                    errorMessages.add("保存 ${state.file.name} 失败: ${e.message}")
                }
            }
            val finalMessage = buildString {
                if (successCount > 0) append("已保存 $successCount 个文件")
                if (errorMessages.isNotEmpty()) {
                    if (successCount > 0) append("，但有错误")
                    else append("保存失败:")
                    errorMessages.forEach { append("\n$it") }
                }
            }
            withContext(KotlinDispatchers.Main) {
                toast.showToast(finalMessage)
            }
        }
    }

    suspend fun saveAllFilesSilently(): Boolean =
        withContext(KotlinDispatchers.IO) {
            val modifiedFiles = openFiles.filter { it.isModified }
            if (modifiedFiles.isEmpty()) return@withContext true
            var successCount = 0
            modifiedFiles.forEach { state ->
                try {
                    if (!state.file.canWrite()) return@forEach
                    state.file.outputStream().bufferedWriter(Charsets.UTF_8)
                        .use { it.write(state.content) }
                    state.onContentSaved()
                    updateFileSavedState(state.file.absolutePath, true)
                    successCount++
                } catch (e: Exception) {
                    LogCatcher.e("EditorViewModel", "静默保存文件失败: ${state.file.name}", e)
                }
            }
            return@withContext successCount == modifiedFiles.size
        }

    suspend fun saveCurrentFileSilently(): Boolean =
        withContext(KotlinDispatchers.IO) {
            val state = activeFileState ?: return@withContext false
            try {
                if (!state.file.canWrite()) return@withContext false
                state.file.outputStream().bufferedWriter(Charsets.UTF_8)
                    .use { it.write(state.content) }
                state.onContentSaved()
                updateFileSavedState(state.file.absolutePath, true)
                true
            } catch (e: Exception) {
                LogCatcher.e("EditorViewModel", "保存当前文件失败: ${state.file.name}", e)
                false
            }
        }

    suspend fun reloadCurrentFile(): Boolean = withContext(KotlinDispatchers.IO) {
        val state = activeFileState ?: return@withContext false
        try {
            if (!state.file.exists() || !state.file.canRead()) return@withContext false
            val content =
                if (state.file.length() > 1024 * 1024) "文件过大" else state.file.readText(Charsets.UTF_8)
            state.onContentLoaded(content)
            editorInstances[state.file.absolutePath]?.let { editor ->
                editor.post {
                    editor.setText(content)
                    val (cursorLine, cursorColumn) = getFileCursorPosition(state.file.absolutePath)
                    try {
                        val lineCount = editor.text.lineCount
                        val targetLine = cursorLine.coerceIn(0, maxOf(0, lineCount - 1))
                        val lineLength = editor.text.getColumnCount(targetLine)
                        val targetColumn = cursorColumn.coerceIn(0, lineLength)
                        editor.setSelection(targetLine, targetColumn)
                    } catch (e: Exception) {
                        LogCatcher.e("EditorViewModel", "恢复光标位置失败: ${state.file.name}", e)
                    }
                }
            }
            true
        } catch (e: Exception) {
            LogCatcher.e("EditorViewModel", "重新加载文件失败: ${state.file.name}", e)
            false
        }
    }

    // 文件操作
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun openFileInternal(file: File): Boolean {
        return try {
            val (cursorLine, cursorColumn) = getFileCursorPosition(file.absolutePath)
            val content = withContext(KotlinDispatchers.IO) {
                try {
                    if (file.length() > 1024 * 1024) "文件过大" else file.readText(Charsets.UTF_8)
                } catch (e: Exception) {
                    "无法读取文件: ${e.message}"
                }
            }
            val languageType = getLanguageType(file.extension)
            val newState = CodeEditorState(file = file, languageType = languageType)
            newState.onContentLoaded(content)
            openFiles = openFiles + newState
            withContext(KotlinDispatchers.IO) {
                stateManager.addOrUpdateFileToCurrentProject(
                    filePath = file.absolutePath,
                    displayName = file.name,
                    cursorLine = cursorLine,
                    cursorColumn = cursorColumn
                )
            }
            true
        } catch (e: Exception) {
            LogCatcher.e("EditorViewModel", "打开文件内部失败: ${file.name}", e)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun openFile(file: File, projectPath: String) {
        if (currentProjectPath != projectPath) {
            resetForNewProject(projectPath)
            viewModelScope.launch { setCurrentProject(projectPath, File(projectPath).name) }
        }
        if (file.isDirectory || !file.exists() || !file.canRead()) return
        viewModelScope.launch {
            val existingIndex = openFiles.indexOfFirst { it.file.absolutePath == file.absolutePath }
            if (existingIndex != -1) {
                activeFileIndex = existingIndex
                val state = openFiles[existingIndex]
                val (cursorLine, cursorColumn) = getFileCursorPosition(file.absolutePath)
                editorInstances[file.absolutePath]?.let { editor ->
                    editor.post {
                        val currentText = editor.text.toString()
                        if (currentText != state.content) editor.setText(state.content)
                        try {
                            val lineCount = editor.text.lineCount
                            val targetLine = cursorLine.coerceIn(0, maxOf(0, lineCount - 1))
                            val lineLength = editor.text.getColumnCount(targetLine)
                            val targetColumn = cursorColumn.coerceIn(0, lineLength)
                            editor.setSelection(targetLine, targetColumn)
                        } catch (e: Exception) {
                            LogCatcher.e("EditorViewModel", "恢复光标位置失败", e)
                        }
                    }
                } ?: restoreCursorPosition(file.absolutePath, cursorLine, cursorColumn)
            } else {
                val success = openFileInternal(file)
                if (success) activeFileIndex = openFiles.lastIndex
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    suspend fun openMultipleFiles(files: List<File>, projectPath: String) {
        if (files.isEmpty()) return
        if (currentProjectPath != projectPath) {
            resetForNewProject(projectPath)
            setCurrentProject(projectPath, File(projectPath).name)
        }
        val existingIndices = mutableMapOf<File, Int>()
        files.forEachIndexed { _, file ->
            val existingIndex = openFiles.indexOfFirst { it.file.absolutePath == file.absolutePath }
            if (existingIndex != -1) existingIndices[file] = existingIndex
        }
        val newFiles = files.filter { it !in existingIndices }
        for (file in newFiles) {
            if (file.isDirectory || !file.exists() || !file.canRead()) continue
            openFileInternal(file)
        }
        if (activeFileIndex == -1 && openFiles.isNotEmpty()) activeFileIndex = 0
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun openFile(file: File) = openFile(file, file.parentFile?.absolutePath ?: "")

    // 编辑操作
    fun undo() =
        openFiles.getOrNull(activeFileIndex)?.let { editorInstances[it.file.absolutePath]?.undo() }

    fun redo() =
        openFiles.getOrNull(activeFileIndex)?.let { editorInstances[it.file.absolutePath]?.redo() }

    // 搜索
    private var lastSearchQuery by mutableStateOf("")
    private var isIgnoreCase by mutableStateOf(true)
    var searchPatternError by mutableStateOf<String?>(null)
        private set
    fun clearSearchPatternError() { searchPatternError = null }
    fun searchText(
        query: String,
        caseSensitive: Boolean = false,
        wholeWord: Boolean = false,
        useRegex: Boolean = false
    ) {
        lastSearchQuery = query
        isIgnoreCase = !caseSensitive
        getActiveEditor()?.apply {
            if (query.isNotEmpty()) {
                val type = when {
                    wholeWord && !useRegex -> EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
                    useRegex -> EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                    else -> EditorSearcher.SearchOptions.TYPE_NORMAL
                }
                var pattern = query
                searchPatternError = null
                if (useRegex) {
                    if (LuaPatternTranslator.isLuaPattern(query)) {
                        val translated = LuaPatternTranslator.translate(query)
                        if (translated == null) {
                            searchPatternError = "Lua模式错误: ${LuaPatternTranslator.lastError}"
                            searcher.stopSearch()
                            return
                        }
                        pattern = translated
                    }
                    if (wholeWord) {
                        pattern = "(?<![a-zA-Z0-9_])" + pattern + "(?![a-zA-Z0-9_])"
                    }
                }
                try {
                    searcher.search(pattern, EditorSearcher.SearchOptions(type, !caseSensitive))
                } catch (e: Exception) {
                    searchPatternError = "无效的正则表达式"
                    searcher.stopSearch()
                }
            } else {
                searchPatternError = null
                searcher.stopSearch()
            }
        }
    }

    fun searchNext() = getActiveEditor()?.let {
        if (lastSearchQuery.isNotEmpty()) try {
            it.searcher.gotoNext()
        } catch (e: IllegalStateException) {
            LogCatcher.e("EditorViewModel", "searchNext: pattern not set", e)
        }
    }

    fun searchPrev() = getActiveEditor()?.let {
        if (lastSearchQuery.isNotEmpty()) try {
            it.searcher.gotoPrevious()
        } catch (e: IllegalStateException) {
            LogCatcher.e("EditorViewModel", "searchPrev: pattern not set", e)
        }
    }

    fun replaceCurrent(text: String) = getActiveEditor()?.let {
        if (lastSearchQuery.isNotEmpty()) try {
            it.searcher.replaceCurrentMatch(text)
        } catch (e: IllegalStateException) {
            LogCatcher.e("EditorViewModel", "replaceCurrent: pattern not set", e)
        }
    }

    fun replaceAll(text: String) = getActiveEditor()?.let {
        if (lastSearchQuery.isNotEmpty()) try {
            it.searcher.replaceAll(text)
        } catch (e: IllegalStateException) {
            LogCatcher.e("EditorViewModel", "replaceAll: pattern not set", e)
        }
    }

    fun stopSearch() {
        getActiveEditor()?.searcher?.stopSearch(); lastSearchQuery = ""
    }

    // 关闭文件
    fun closeAllFiles() {
        openFiles.forEach {
            viewModelScope.launch(KotlinDispatchers.IO) {
                stateManager.removeFileFromCurrentProject(
                    it.file.absolutePath
                )
            }
        }
        cleanupEditors()
        openFiles = emptyList()
        activeFileIndex = -1
    }

    fun closeOtherFiles(indexToKeep: Int) {
        if (indexToKeep !in openFiles.indices) return
        openFiles.forEachIndexed { index, state ->
            if (index != indexToKeep) {
                viewModelScope.launch(KotlinDispatchers.IO) {
                    stateManager.removeFileFromCurrentProject(
                        state.file.absolutePath
                    )
                }
                cursorListeners.remove(state.file.absolutePath)?.unsubscribe()
                editorInstances.remove(state.file.absolutePath)?.release()
            }
        }
        openFiles = listOf(openFiles[indexToKeep])
        activeFileIndex = 0
    }

    fun closeFile(indexToClose: Int) {
        if (indexToClose !in openFiles.indices) return
        val filePath = openFiles[indexToClose].file.absolutePath
        viewModelScope.launch(KotlinDispatchers.IO) {
            stateManager.removeFileFromCurrentProject(
                filePath
            )
        }
        cursorListeners.remove(filePath)?.unsubscribe()
        openFiles.getOrNull(indexToClose)?.file?.absolutePath?.let {
            editorInstances.remove(it)?.release()
        }
        openFiles = openFiles.toMutableList().also { it.removeAt(indexToClose) }
        if (openFiles.isEmpty()) activeFileIndex = -1
        else if (activeFileIndex >= indexToClose) activeFileIndex =
            (activeFileIndex - 1).coerceAtLeast(0)
    }

    fun handleFileRenamed(oldFile: File) {
        viewModelScope.launch {
            try {
                val oldPath = oldFile.absolutePath
                val index = openFiles.indexOfFirst { it.file.absolutePath == oldPath }
                if (index != -1) closeFile(index)
                withContext(KotlinDispatchers.IO) {
                    stateManager.removeFileFromCurrentProject(
                        oldPath
                    )
                }
            } catch (e: Exception) {
                LogCatcher.e("EditorViewModel", "处理文件重命名失败", e)
            }
        }
    }

    fun handleFileDeleted(file: File) {
        viewModelScope.launch {
            try {
                val filePath = file.absolutePath
                val index = openFiles.indexOfFirst { it.file.absolutePath == filePath }
                if (index != -1) closeFile(index)
                withContext(KotlinDispatchers.IO) {
                    stateManager.removeFileFromCurrentProject(
                        filePath
                    )
                }
            } catch (e: Exception) {
                LogCatcher.e("EditorViewModel", "处理文件删除失败", e)
            }
        }
    }

    fun cleanupNonExistentFiles() {
        viewModelScope.launch {
            val existingFiles = openFiles.filter { state ->
                val exists = state.file.exists() && state.file.isFile
                if (!exists) withContext(KotlinDispatchers.IO) {
                    stateManager.removeFileFromCurrentProject(
                        state.file.absolutePath
                    )
                }
                exists
            }
            if (existingFiles.size != openFiles.size) {
                openFiles = existingFiles
                if (activeFileIndex >= openFiles.size) activeFileIndex =
                    maxOf(0, openFiles.size - 1)
            }
        }
    }

    private fun getLanguageType(extension: String): String = when (extension.lowercase()) {
        "lua" -> "lua"
        "aly" -> "aly"
        "json" -> "json"
        else -> "plain"
    }
}
