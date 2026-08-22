@file:OptIn(ExperimentalMaterial3Api::class)

package com.luafabric.studio.falling.ui.editor

import android.app.Activity
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androlua.LuaActivity
import com.luafabric.studio.falling.ProjectItem
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.files.FileTree
import com.luafabric.studio.falling.ui.analyse.AnalyseScreen
import com.luafabric.studio.falling.ui.attribute.AttributeScreen
import com.luafabric.studio.falling.ui.components.ColorPickerDialog
import com.luafabric.studio.falling.ui.components.EdgeSwipeDismissibleDrawer
import com.luafabric.studio.falling.ui.editor.ai.AiChatPanel
import com.luafabric.studio.falling.ui.editor.ai.CodeReference
import com.luafabric.studio.falling.ui.editor.viewmodel.EditorViewModel
import com.luafabric.studio.falling.ui.javaapi.JavaApiScreen
import com.luafabric.studio.falling.ui.settings.SettingsManager
import muling.views.tool.utils.LogCatcher
import muling.views.tool.utils.NonBlockingToastState
import muling.views.tool.utils.TransitionUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 定义滑动手势方向枚举
enum class SwipeDirection { UP, DOWN }

// 定义覆盖层密封类
sealed class OverlayScreen {
    object NONE : OverlayScreen()
    data class ANALYSE(val codeContent: String, val projectPath: String?) : OverlayScreen()
    data class JAVA_API(val initialClass: String? = null) : OverlayScreen()
    object ATTRIBUTE : OverlayScreen()
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CodeEditScreen(
    project: ProjectItem,
    onBack: () -> Unit,
    toast: NonBlockingToastState
) {
    var isAutoSaving by remember { mutableStateOf(false) }
    var autoSaveCompleted by remember { mutableStateOf(false) }

    val fileTreeDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isMoreMenuExpanded by remember { mutableStateOf(false) }

    val settingsManager = SettingsManager
    val currentSettings = settingsManager.currentSettings

    var previousFontSettings by remember {
        mutableStateOf(
            currentSettings.editorFontType to currentSettings.customFontPath
        )
    }

    val projectPath = project.path

    val viewModelKey = remember(project.path, project.createdDate.time) {
        "${project.path}_${project.createdDate.time}"
    }

    val viewModel: EditorViewModel = viewModel(key = viewModelKey)

    var showInstallDialog by remember { mutableStateOf(false) }
    var apkFilePath by remember { mutableStateOf<String?>(null) }
    var isBuilding by remember { mutableStateOf(false) }
    var isCompilingFile by remember { mutableStateOf(false) }
    var showInitialLoader by remember { mutableStateOf(!viewModel.hasShownInitialLoader) }
    var tabBarRendered by remember { mutableStateOf(false) }
    val lastFileToOpen = remember { mutableStateOf<String?>(null) }

    var currentOverlay by remember { mutableStateOf<OverlayScreen>(OverlayScreen.NONE) }

    val currentFileName = remember(viewModel.activeFileIndex, viewModel.openFiles) {
        if (viewModel.activeFileIndex in viewModel.openFiles.indices) {
            viewModel.openFiles[viewModel.activeFileIndex].file.name
        } else {
            ""
        }
    }

    var previousProjectPath by remember { mutableStateOf<String?>(null) }
    var previousProjectTimestamp by remember { mutableStateOf<Long?>(null) }

    val density = LocalDensity.current
    val panelState = rememberDraggablePanelState(
        minHeight = with(density) { 88.dp.toPx() }
    )

    // 快捷功能相关状态
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileType by remember { mutableStateOf(context.getString(R.string.code_editor_file)) }
    var newFileName by remember { mutableStateOf("") }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(Color.Black) }
    var isBackingUp by remember { mutableStateOf(false) }
    var refreshFileTreeKey by remember { mutableStateOf(0) }

    // AI 侧边栏状态
    var codeReference by remember { mutableStateOf<CodeReference?>(null) }
    val drawerSelectedTab = remember { mutableStateOf(DrawerTab.FILE_TREE) }

    val onAiCodeReference: (String, String, Int, Int, String) -> Unit = { filePath, fileName, startLine, endLine, content ->
        codeReference = CodeReference(
            filePath = filePath,
            fileName = fileName,
            startLine = startLine,
            endLine = endLine,
            content = content
        )
        drawerSelectedTab.value = DrawerTab.AI
        scope.launch {
            if (fileTreeDrawerState.isClosed) {
                fileTreeDrawerState.open()
            }
        }
    }

    // AI 对话框状态
    var askUserDialogState by remember { mutableStateOf<AskUserDialogState?>(null) }
    var confirmDialogState by remember { mutableStateOf<ConfirmDialogState?>(null) }

    // 后缀选择菜单状态（独立于输入框）
    var suffixMenuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // ========== 搜索面板状态 ==========
    var isSearchVisible by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }

    val onSearchTextChange: (String) -> Unit = { text ->
        searchText = text
        viewModel.searchText(text, caseSensitive, wholeWord, useRegex)
    }
    val onReplaceTextChange: (String) -> Unit = { replaceText = it }
    val onCaseSensitiveChange: (Boolean) -> Unit = { newCaseSensitive ->
        caseSensitive = newCaseSensitive
        if (searchText.isNotEmpty()) {
            viewModel.searchText(searchText, newCaseSensitive, wholeWord, useRegex)
        }
    }
    val onWholeWordChange: (Boolean) -> Unit = { newWholeWord ->
        wholeWord = newWholeWord
        if (searchText.isNotEmpty()) {
            viewModel.searchText(searchText, caseSensitive, newWholeWord, useRegex)
        }
    }
    val onUseRegexChange: (Boolean) -> Unit = { newUseRegex ->
        useRegex = newUseRegex
        if (searchText.isNotEmpty()) {
            viewModel.searchText(searchText, caseSensitive, wholeWord, newUseRegex)
        }
    }
    val onCloseSearch: () -> Unit = {
        isSearchVisible = false
        viewModel.stopSearch()
        searchText = ""
        replaceText = ""
    }
    val onSearchNext: () -> Unit = { viewModel.searchNext() }
    val onSearchPrev: () -> Unit = { viewModel.searchPrev() }
    val onReplaceCurrent: (String) -> Unit = { text -> viewModel.replaceCurrent(text) }
    val onReplaceAll: (String) -> Unit = { text -> viewModel.replaceAll(text) }
    // =================================

    // ========== 快捷功能栏可见性状态 ==========
    var quickBarVisible by remember { mutableStateOf(true) }

    // ========== Maven下载进度状态 ==========
    var showDownloadProgress by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var currentDownloadFile by remember { mutableStateOf("") }
    var currentDownloadIndex by remember { mutableStateOf(0) }
    var totalDownloadFiles by remember { mutableStateOf(0) }
    var downloadedBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(0L) }
    // =================================

    // ========== 布局助手启动器 ==========
    val layoutHelperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val newContent = data?.getStringExtra("layout_result")
            if (newContent != null) {
                viewModel.replaceCurrentFileContent(newContent)
            }
        }
    }

    // ========== 保存滚动状态 ==========
    val quickActionScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val symbolBarScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    // 进度条状态调试日志
    LaunchedEffect(showInitialLoader, isBuilding, isAutoSaving, isCompilingFile, isBackingUp) {
        LogCatcher.d("ProgressBar",
            "showInitialLoader=$showInitialLoader | isBuilding=$isBuilding | isAutoSaving=$isAutoSaving | " +
            "isCompilingFile=$isCompilingFile | isBackingUp=$isBackingUp")
    }

    // 当切换到覆盖层页面时自动隐藏键盘
    LaunchedEffect(currentOverlay) {
        if (currentOverlay !is OverlayScreen.NONE) {
            val imm = context.getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentFocusView = (context as? Activity)?.currentFocus
            if (currentFocusView != null) {
                imm?.hideSoftInputFromWindow(currentFocusView.windowToken, 0)
                currentFocusView.clearFocus()
            } else {
                val decorView = (context as? Activity)?.window?.decorView
                if (decorView != null) {
                    imm?.hideSoftInputFromWindow(decorView.windowToken, 0)
                }
            }
        }
    }

    // 确保 viewModel 已经初始化
    LaunchedEffect(Unit) {
        if (!viewModel.isInitialized) {
            viewModel.initialize(context)
        }
    }

    LaunchedEffect(viewModel.searchPatternError) {
        viewModel.searchPatternError?.let {
            toast.showToast(it)
            viewModel.clearSearchPatternError()
        }
    }

    LaunchedEffect(currentSettings.editorFontType, currentSettings.customFontPath) {
        val currentFontSettings = currentSettings.editorFontType to currentSettings.customFontPath
        if (currentFontSettings != previousFontSettings) {
            viewModel.updateEditorFonts()
            previousFontSettings = currentFontSettings
        }
    }

    LaunchedEffect(projectPath, project.createdDate.time) {
        if (previousProjectPath != projectPath || previousProjectTimestamp != project.createdDate.time) {
            previousProjectPath = projectPath
            previousProjectTimestamp = project.createdDate.time

            if (!viewModel.isInitialized) {
                viewModel.initialize(context)
                showInitialLoader = true
                tabBarRendered = false
            }

            if (!viewModel.hasShownInitialLoader || viewModel.openFiles.isEmpty()) {
                showInitialLoader = true
                tabBarRendered = false

                loadProjectFiles(
                    viewModel = viewModel,
                    projectPath = projectPath,
                    projectName = project.name,
                    enableTabHistory = currentSettings.enableTabHistory,
                    lastFileToOpen = lastFileToOpen
                )
            }

            showInitialLoader = false
            viewModel.onInitialLoaderShown()
        }
    }

    // 监听导航到 API 阅览器的请求
    LaunchedEffect(viewModel.navigateToApiClass) {
        val className = viewModel.consumeNavigateToApiClass()
        if (className != null) {
            currentOverlay = OverlayScreen.JAVA_API(className)
        }
    }

    // ========== 构建项目状态 ==========
    var buildJob: Job? by remember { mutableStateOf(null) }

    // 优先关闭覆盖层
    BackHandler(enabled = true) {
        scope.launch {
            when {
                isBuilding -> {
                    buildJob?.cancel()
                    buildJob = null
                    isBuilding = false
                    toast.showToast(context.getString(R.string.code_editor_build_cancelled))
                }
                isSearchVisible -> {
                    isSearchVisible = false
                    searchText = ""
                    replaceText = ""
                    viewModel.stopSearch()
                }
                showNewFileDialog -> {
                    showNewFileDialog = false
                    newFileName = ""
                }
                showColorPickerDialog -> {
                    showColorPickerDialog = false
                }
                fileTreeDrawerState.isOpen -> {
                    fileTreeDrawerState.close()
                }
                else -> {
                    viewModel.saveAllFilesSilently()
                    onBack()
                }
            }
        }
    }

    // ========== 构建项目 ==========
    val onBuildProjectAction: () -> Unit = {
        buildJob = scope.launch {
            viewModel.saveAllFilesSilently()
            isBuilding = true
            val result = try {
                this.async<String>(Dispatchers.IO) { buildProject(context, projectPath) }.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogCatcher.e("CodeEditScreen", "构建协程异常", e)
                "error: ${context.getString(R.string.code_editor_build_exception, e.message)}"
            }
            if (result.startsWith("error:")) {
                toast.showToast(context.getString(R.string.code_editor_build_failed, result.substringAfter("error: ")))
            } else {
                apkFilePath = result
                showInstallDialog = true
            }
            isBuilding = false
        }
    }

    // ========== 备份项目 ==========
    val onBackupProject: () -> Unit = {
        scope.launch {
            viewModel.saveAllFilesSilently()
            isBackingUp = true
            val result = try {
                this.async<String>(Dispatchers.IO) { backupProject(context, projectPath) }.await()
            } catch (e: Exception) {
                LogCatcher.e("CodeEditScreen", "备份协程异常", e)
                "error: ${context.getString(R.string.code_editor_backup_failed, e.message)}"
            }
            if (result.startsWith("error:")) {
                toast.showToast(context.getString(R.string.code_editor_backup_failed, result.substringAfter("error: ")))
            } else {
                toast.showToast(context.getString(R.string.code_editor_backup_success, result))
            }
            isBackingUp = false
        }
    }

    // ========== 新建文件/文件夹 ==========
    fun onCreateFileOrFolder() {
        if (newFileName.isBlank()) {
            scope.launch {
                toast.showToast(context.getString(R.string.code_editor_enter_file_name))
            }
            return
        }
        scope.launch {
            try {
                val baseDir = if (viewModel.activeFileState?.file?.exists() == true) {
                    viewModel.activeFileState!!.file.parentFile ?: File(projectPath)
                } else {
                    File(projectPath)
                }
                val targetPath = File(baseDir, newFileName)

                if (newFileType == context.getString(R.string.code_editor_file)) {
                    if (targetPath.exists()) {
                        toast.showToast(context.getString(R.string.code_editor_file_exists))
                        return@launch
                    }
                    targetPath.parentFile?.mkdirs()
                    val success = withContext(Dispatchers.IO) { targetPath.createNewFile() }
                    if (success) {
                        toast.showToast(context.getString(R.string.code_editor_file_created))
                        // 刷新文件树
                        refreshFileTreeKey++
                    } else {
                        toast.showToast(context.getString(R.string.code_editor_file_create_failed))
                    }
                } else { // 文件夹
                    if (targetPath.exists()) {
                        toast.showToast(context.getString(R.string.code_editor_folder_exists))
                        return@launch
                    }
                    val success = withContext(Dispatchers.IO) { targetPath.mkdirs() }
                    if (success) {
                        toast.showToast(context.getString(R.string.code_editor_folder_created))
                        // 刷新文件树
                        refreshFileTreeKey++
                    } else {
                        toast.showToast(context.getString(R.string.code_editor_folder_create_failed))
                    }
                }

                showNewFileDialog = false
                newFileName = ""
            } catch (e: Exception) {
                LogCatcher.e("CodeEditScreen", "创建文件/文件夹失败", e)
                toast.showToast(context.getString(R.string.code_editor_file_create_failed, e.message))
            }
        }
    }

    // ========== 颜色选择器回调 ==========
    val onColorSelected: (Color) -> Unit = { color ->
        val hexColor = colorToHex(color)
        viewModel.insertSymbolToCorrectEditor(hexColor)
        showColorPickerDialog = false
    }

    // ========== 布局助手启动逻辑 ==========
    fun onLaunchLayoutHelper() {
        val currentFile = viewModel.activeFileState?.file
        if (currentFile == null) {
            scope.launch {
                toast.showToast(context.getString(R.string.code_editor_no_active_file))
            }
            return
        }
        if (!currentFile.name.endsWith(".aly", ignoreCase = true)) {
            scope.launch {
                toast.showToast(context.getString(R.string.code_editor_current_file_not_supported))
            }
            return
        }

        val content = viewModel.activeFileState?.content ?: run {
            scope.launch {
                toast.showToast(context.getString(R.string.code_editor_cannot_get_content))
            }
            return
        }

        val layoutHelperPath = "${context.filesDir.absolutePath}/layouthelper/main.lua"
        val layoutHelperFile = File(layoutHelperPath)
        if (!layoutHelperFile.exists()) {
            scope.launch {
                toast.showToast(context.getString(R.string.code_editor_layout_helper_not_installed))
            }
            return
        }

        val intent = Intent(context, LuaActivity::class.java).apply {
            data = Uri.fromFile(layoutHelperFile)
            putExtra("layout_content", content)
            putExtra("luapath", currentFile.absolutePath)
        }

        layoutHelperLauncher.launch(intent)
    }

    // ========== 快捷功能列表（使用资源 ID） ==========
    val quickActions = remember {
        listOf(
            QuickAction(R.string.code_editor_open, "打开") {
                viewModel.incrementQuickActionFrequency("打开")
                scope.launch {
                    if (fileTreeDrawerState.isClosed) fileTreeDrawerState.open()
                }
            },
            QuickAction(R.string.save, "保存") {
                viewModel.incrementQuickActionFrequency("保存")
                scope.launch { viewModel.saveAllModifiedFiles(toast) }
            },
            QuickAction(R.string.code_editor_new, "新建") {
                viewModel.incrementQuickActionFrequency("新建")
                newFileType = context.getString(R.string.code_editor_file)
                newFileName = ""
                showNewFileDialog = true
            },
            QuickAction(R.string.code_editor_format, "格式化") {
                viewModel.incrementQuickActionFrequency("格式化")
                viewModel.formatCode()
            },
            QuickAction(R.string.code_editor_layout_helper, "布局助手") {
                viewModel.incrementQuickActionFrequency("布局助手")
                onLaunchLayoutHelper()
            },
            QuickAction(R.string.code_editor_project_property, "项目属性") {
                scope.launch {
                    viewModel.saveAllFilesSilently()
                    viewModel.incrementQuickActionFrequency("项目属性")
                    currentOverlay = OverlayScreen.ATTRIBUTE
                }
            },
            QuickAction(R.string.code_editor_build, "构建项目") {
                viewModel.incrementQuickActionFrequency("构建项目")
                onBuildProjectAction()
            },
            QuickAction(R.string.code_editor_analyse, "导入分析") {
                viewModel.incrementQuickActionFrequency("导入分析")
                val codeContent = viewModel.activeFileState?.content ?: ""
                currentOverlay = OverlayScreen.ANALYSE(codeContent, projectPath)
            },
            QuickAction(R.string.code_editor_api_viewer, "API阅览器") {
                viewModel.incrementQuickActionFrequency("API阅览器")
                currentOverlay = OverlayScreen.JAVA_API()
            },
            QuickAction(R.string.search, "搜索") {
                viewModel.incrementQuickActionFrequency("搜索")
                if (viewModel.openFiles.isNotEmpty()) {
                    isSearchVisible = !isSearchVisible
                    if (!isSearchVisible) {
                        onCloseSearch()
                    }
                } else {
                    scope.launch {
                        toast.showToast(context.getString(R.string.code_editor_no_active_file))
                    }
                }
            },
            QuickAction(R.string.code_editor_backup, "备份") {
                viewModel.incrementQuickActionFrequency("备份")
                onBackupProject()
            },
            QuickAction(R.string.code_editor_palette, "调色板") {
                viewModel.incrementQuickActionFrequency("调色板")
                showColorPickerDialog = true
            }
        )
    }

    val smartSortingEnabled by remember { derivedStateOf { currentSettings.smartSortingEnabled } }

    var sortedQuickActions by remember { mutableStateOf(quickActions) }
    LaunchedEffect(viewModel.isQuickActionFrequencyLoaded, smartSortingEnabled) {
        if (viewModel.isQuickActionFrequencyLoaded) {
            sortedQuickActions = if (smartSortingEnabled) {
                quickActions.sortedByDescending { viewModel.quickActionFrequencyMap[it.key] ?: 0 }
            } else {
                quickActions
            }
        }
    }

    EdgeSwipeDismissibleDrawer(
        drawerState = fileTreeDrawerState,
        gesturesEnabled = true,
        drawerContent = {
            ProjectFileTree(
                projectPath = projectPath,
                viewModel = viewModel,
                drawerState = fileTreeDrawerState,
                refreshTrigger = refreshFileTreeKey,
                selectedTab = drawerSelectedTab.value,
                onTabChange = { drawerSelectedTab.value = it },
                codeReference = codeReference,
                onClearReference = { codeReference = null },
                onOpenFile = { filePath, startLine, endLine ->
                    val file = File(filePath)
                    if (file.exists()) {
                        viewModel.openFile(file, projectPath)
                        val index = viewModel.openFiles.indexOfFirst { it.file.absolutePath == filePath }
                        if (index != -1) {
                            viewModel.changeActiveFileIndex(index)
                        }
                    }
                },
                onAskUser = { title, options, callback ->
                    askUserDialogState = AskUserDialogState(title, options, callback)
                },
                onConfirmInMain = { title, message, callback ->
                    confirmDialogState = ConfirmDialogState(title, message, callback)
                },
                onNavigateToSettings = {
                    drawerSelectedTab.value = DrawerTab.AI
                    scope.launch {
                        if (fileTreeDrawerState.isClosed) {
                            fileTreeDrawerState.open()
                        }
                    }
                }
            )
        },
        content = {
            AnimatedContent(
                targetState = currentOverlay,
                transitionSpec = { TransitionUtil.createScreenTransition(targetState !is OverlayScreen.NONE) },
                label = "overlay_transition"
            ) { overlay ->
                when (overlay) {
                    is OverlayScreen.ANALYSE -> AnalyseScreen(
                        codeContent = overlay.codeContent,
                        projectPath = overlay.projectPath,
                        onBack = { currentOverlay = OverlayScreen.NONE },
                        toast = toast
                    )

                    is OverlayScreen.JAVA_API -> JavaApiScreen(
                        initialClass = overlay.initialClass,
                        onBack = { currentOverlay = OverlayScreen.NONE },
                        toast = toast
                    )

                    OverlayScreen.ATTRIBUTE -> AttributeScreen(
    projectPath = projectPath,
    onBack = { currentOverlay = OverlayScreen.NONE },
    onSaveComplete = {
        val settingsFile = File(projectPath, "settings.json")
        if (settingsFile.exists()) {
            scope.launch {
                val existingIndex =
                    viewModel.openFiles.indexOfFirst { it.file.absolutePath == settingsFile.absolutePath }
                if (existingIndex != -1) {
                    viewModel.closeFile(existingIndex)
                    delay(100)
                }
                viewModel.openFile(settingsFile, projectPath)
                val newIndex =
                    viewModel.openFiles.indexOfFirst { it.file.absolutePath == settingsFile.absolutePath }
                if (newIndex != -1) {
                    viewModel.changeActiveFileIndex(newIndex)
                }
            }
        }
    },
    toast = toast
)
                    OverlayScreen.NONE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            Scaffold(
                                topBar = {
                                    EditorTopBar(
                                        projectName = project.name,
                                        currentFileName = currentFileName,
                                        drawerState = fileTreeDrawerState,
                                        onDrawerToggle = {
                                            scope.launch {
                                                if (fileTreeDrawerState.isOpen) fileTreeDrawerState.close()
                                                else fileTreeDrawerState.open()
                                            }
                                        },
                                        viewModel = viewModel,
                                        toast = toast,
                                        context = context,
                                        projectPath = projectPath,
                                        isMoreMenuExpanded = isMoreMenuExpanded,
                                        onMoreMenuExpandedChange = { isMoreMenuExpanded = it },
                                        isCompilingFile = isCompilingFile,
                                        onCompileFile = {
                                            scope.launch {
                                                compileCurrentFile(
                                                    viewModel = viewModel,
                                                    toast = toast,
                                                    context = context,
                                                    isCompilingFile = { isCompilingFile = it }
                                                )
                                            }
                                        },
                                        onBuildProject = onBuildProjectAction
                                    )
                                },
                                content = { innerPadding ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                    ) {
                                        EditorContent(
                                            modifier = Modifier.fillMaxSize(),
                                            showInitialLoader = showInitialLoader,
                                            isBuilding = isBuilding,
                                            isAutoSaving = isAutoSaving,
                                            isCompilingFile = isCompilingFile,
                                            viewModel = viewModel,
                                            onTabBarRendered = { tabBarRendered = true },
                                            lastFileToOpen = lastFileToOpen.value,
                                            panelState = panelState,
                                            fileTreeDrawerState = fileTreeDrawerState,
                                            quickActions = sortedQuickActions,
                                            isBackingUp = isBackingUp,
                                            isSearchVisible = isSearchVisible,
                                            searchText = searchText,
                                            onSearchTextChange = onSearchTextChange,
                                            replaceText = replaceText,
                                            onReplaceTextChange = onReplaceTextChange,
                                            caseSensitive = caseSensitive,
                                            onCaseSensitiveChange = onCaseSensitiveChange,
                                            wholeWord = wholeWord,
                                            onWholeWordChange = onWholeWordChange,
                                            useRegex = useRegex,
                                            onUseRegexChange = onUseRegexChange,
                                            onCloseSearch = onCloseSearch,
                                            onSearchNext = onSearchNext,
                                            onSearchPrev = onSearchPrev,
                                            onReplaceCurrent = onReplaceCurrent,
                                            onReplaceAll = onReplaceAll,
                                            toast = toast,
                                            quickActionScrollState = quickActionScrollState,
                                            symbolBarScrollState = symbolBarScrollState,
                                            quickBarVisible = quickBarVisible,
                                            onSwipe = { direction ->
                                                quickBarVisible = when (direction) {
                                                    SwipeDirection.UP -> false
                                                    SwipeDirection.DOWN -> true
                                                }
                                            },
                                            onAiCodeReference = onAiCodeReference
                                        )
                                    }
                                }
                            )

                            InstallApkDialog(
                                showInstallDialog = showInstallDialog,
                                apkFilePath = apkFilePath,
                                onDismiss = {
                                    showInstallDialog = false
                                    apkFilePath = null
                                },
                                onInstall = {
                                    apkFilePath?.let { filePath ->
                                        installApk(context, filePath, toast, scope)
                                    }
                                    showInstallDialog = false
                                    apkFilePath = null
                                }
                            )

                            // Maven下载进度对话框
                            DownloadProgressDialog(
                                showDialog = showDownloadProgress,
                                onDismiss = { 
                                    // 用户可以选择取消构建，这里仅关闭对话框，构建仍在后台进行
                                    // 如果需要取消构建，需要更复杂的协程取消逻辑
                                },
                                progress = downloadProgress,
                                currentFile = currentDownloadFile,
                                currentIndex = currentDownloadIndex,
                                totalFiles = totalDownloadFiles,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes
                            )

                            if (showNewFileDialog) {
                                val baseDir = if (viewModel.activeFileState?.file?.exists() == true) {
                                    viewModel.activeFileState!!.file.parentFile ?: File(projectPath)
                                } else {
                                    File(projectPath)
                                }

                                // 计算相对路径显示，包含项目名
                                val projectName = project.name
                                val relativePath = if (baseDir.absolutePath.startsWith(projectPath)) {
                                    val rel = baseDir.absolutePath.substring(projectPath.length)
                                    if (rel.startsWith(File.separator)) rel.substring(1) else rel
                                } else {
                                    baseDir.absolutePath
                                }
                                val displayPath = if (relativePath.isNotEmpty()) {
                                    ".../$projectName/$relativePath"
                                } else {
                                    ".../$projectName"
                                }

                                AlertDialog(
                                    onDismissRequest = { showNewFileDialog = false },
                                    title = { Text(stringResource(R.string.code_editor_new)) },
                                    text = {
                                        Column {
                                            Text(stringResource(R.string.code_editor_select_type), style = MaterialTheme.typography.bodyMedium)
                                            Spacer(modifier = Modifier.height(8.dp))

                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilterChip(
                                                    selected = newFileType == context.getString(R.string.code_editor_file),
                                                    onClick = { newFileType = context.getString(R.string.code_editor_file) },
                                                    label = { Text(stringResource(R.string.code_editor_file)) }
                                                )
                                                FilterChip(
                                                    selected = newFileType == context.getString(R.string.code_editor_folder),
                                                    onClick = { newFileType = context.getString(R.string.code_editor_folder) },
                                                    label = { Text(stringResource(R.string.code_editor_folder)) }
                                                )
                                            }

                                            Spacer(Modifier.height(16.dp))
                                            Text(stringResource(R.string.code_editor_enter_name), style = MaterialTheme.typography.bodyMedium)
                                            Spacer(Modifier.height(8.dp))

                                            // 输入框 + 右侧 ExposedDropdownMenuBox 包裹的 IconButton
                                            OutlinedTextField(
                                                value = newFileName,
                                                onValueChange = { newFileName = it },
                                                label = { Text(stringResource(R.string.code_editor_enter_name)) },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .focusRequester(focusRequester),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(
                                                    onDone = { onCreateFileOrFolder() }
                                                ),
                                                trailingIcon = {
                                                    if (newFileType == context.getString(R.string.code_editor_file)) {
                                                        // 使用 ExposedDropdownMenuBox 将菜单锚定到 IconButton
                                                        ExposedDropdownMenuBox(
                                                            expanded = suffixMenuExpanded,
                                                            onExpandedChange = { suffixMenuExpanded = it }
                                                        ) {
                                                            IconButton(
                                                                onClick = { suffixMenuExpanded = true }
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.ArrowDropDown,
                                                                    contentDescription = stringResource(R.string.code_editor_choose_suffix)
                                                                )
                                                            }
                                                            ExposedDropdownMenu(
                                                                expanded = suffixMenuExpanded,
                                                                onDismissRequest = { suffixMenuExpanded = false },
                                                                modifier = Modifier.width(140.dp)
                                                            ) {
                                                                val commonExtensions = listOf(
                                                                    ".lua", ".aly", ".json", ".txt", ".md", ".html", ".css", ".js"
                                                                )
                                                                commonExtensions.forEach { ext ->
                                                                    DropdownMenuItem(
                                                                        text = { Text(ext) },
                                                                        onClick = {
                                                                            // 替换后缀逻辑
                                                                            val trimmed = newFileName.trim()
                                                                            val lastDotIndex = trimmed.lastIndexOf('.')
                                                                            newFileName = if (lastDotIndex != -1 && lastDotIndex > 0) {
                                                                                trimmed.take(
                                                                                    lastDotIndex
                                                                                ) + ext
                                                                            } else {
                                                                                trimmed + ext
                                                                            }
                                                                            suffixMenuExpanded = false
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            )

                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(R.string.code_editor_create_in, displayPath),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = { onCreateFileOrFolder() },
                                            enabled = newFileName.isNotBlank()
                                        ) { Text(stringResource(R.string.code_editor_create)) }
                                    },
                                    dismissButton = {
                                        TextButton(
                                            onClick = {
                                                showNewFileDialog = false
                                                newFileName = ""
                                            }
                                        ) { Text(stringResource(R.string.cancel)) }
                                    }
                                )
                            }

                            if (showColorPickerDialog) {
                                ColorPickerDialog(
                                    title = stringResource(R.string.code_editor_palette),
                                    initialColor = selectedColor,
                                    onDismiss = { showColorPickerDialog = false },
                                    onColorSelected = onColorSelected
                                )
                            }

                            // AI 询问用户对话框
                            askUserDialogState?.let { state ->
                                AlertDialog(
                                    onDismissRequest = {
                                        state.callback(null)
                                        askUserDialogState = null
                                    },
                                    title = { Text(state.title) },
                                    text = {
                                        Column {
                                            state.options.forEachIndexed { index, option ->
                                                TextButton(
                                                    onClick = {
                                                        state.callback(option)
                                                        askUserDialogState = null
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(option)
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {},
                                    dismissButton = {
                                        TextButton(onClick = {
                                            state.callback(null)
                                            askUserDialogState = null
                                        }) { Text(stringResource(R.string.cancel)) }
                                    }
                                )
                            }

                            // AI 确认对话框
                            confirmDialogState?.let { state ->
                                AlertDialog(
                                    onDismissRequest = {
                                        state.callback(false)
                                        confirmDialogState = null
                                    },
                                    title = { Text(state.title) },
                                    text = { Text(state.message) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            state.callback(true)
                                            confirmDialogState = null
                                        }) { Text(stringResource(R.string.ok)) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            state.callback(false)
                                            confirmDialogState = null
                                        }) { Text(stringResource(R.string.cancel)) }
                                    }
                                )
                            }

                            if (fileTreeDrawerState.isOpen) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Transparent)
                                        .clickable(
                                            indication = null,
                                            interactionSource = null
                                        ) { scope.launch { fileTreeDrawerState.close() } }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

enum class DrawerTab { FILE_TREE, AI }

data class AskUserDialogState(
    val title: String,
    val options: List<String>,
    val callback: (String?) -> Unit
)

data class ConfirmDialogState(
    val title: String,
    val message: String,
    val callback: (Boolean) -> Unit
)

@Composable
fun ProjectFileTree(
    projectPath: String,
    viewModel: EditorViewModel,
    drawerState: DrawerState,
    refreshTrigger: Int,
    selectedTab: DrawerTab,
    onTabChange: (DrawerTab) -> Unit,
    codeReference: CodeReference?,
    onClearReference: () -> Unit,
    onOpenFile: (filePath: String, startLine: Int, endLine: Int) -> Unit,
    onAskUser: (title: String, options: List<String>, callback: (String?) -> Unit) -> Unit,
    onConfirmInMain: (title: String, message: String, callback: (Boolean) -> Unit) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()

    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        // ── Content area (fills remaining space) ──
        Column(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                DrawerTab.FILE_TREE -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            stringResource(R.string.code_editor_file_tree),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    FileTree(
                        rootPath = projectPath,
                        refreshTrigger = refreshTrigger,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        onFileClick = { file ->
                            viewModel.openFile(file, projectPath)
                            scope.launch { drawerState.close() }
                        },
                        onFileRenamed = { oldFile, _ -> viewModel.handleFileRenamed(oldFile) },
                        onFileDeleted = { file -> viewModel.handleFileDeleted(file) }
                    )
                }
                DrawerTab.AI -> {
                    saveableStateHolder.SaveableStateProvider("ai") {
                        AiChatPanel(
                            projectPath = projectPath,
                            codeReference = codeReference,
                            onClearReference = onClearReference,
                            onOpenFile = onOpenFile,
                            onAskUser = onAskUser,
                            onConfirmInMain = onConfirmInMain,
                            onNavigateToSettings = onNavigateToSettings
                        )
                    }
                }
            }
        }

        // ── Bottom navigation bar ──
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DrawerTabButton(
                selected = selectedTab == DrawerTab.FILE_TREE,
                icon = Icons.Filled.Folder,
                contentDescription = stringResource(R.string.cd_file_tree_tab),
                onClick = { onTabChange(DrawerTab.FILE_TREE) }
            )
            DrawerTabButton(
                selected = selectedTab == DrawerTab.AI,
                icon = Icons.Filled.SmartToy,
                contentDescription = stringResource(R.string.cd_ai_tab),
                onClick = { onTabChange(DrawerTab.AI) }
            )
        }
    }
}

@Composable
private fun RowScope.DrawerTabButton(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val containerColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        Color.Transparent
    val iconColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun EditorContent(
    modifier: Modifier = Modifier,
    showInitialLoader: Boolean,
    isBuilding: Boolean,
    isAutoSaving: Boolean,
    isCompilingFile: Boolean,
    viewModel: EditorViewModel,
    onTabBarRendered: () -> Unit,
    lastFileToOpen: String?,
    panelState: DraggablePanelState,
    fileTreeDrawerState: DrawerState,
    quickActions: List<QuickAction>,
    isBackingUp: Boolean,
    isSearchVisible: Boolean,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    replaceText: String,
    onReplaceTextChange: (String) -> Unit,
    caseSensitive: Boolean,
    onCaseSensitiveChange: (Boolean) -> Unit,
    wholeWord: Boolean,
    onWholeWordChange: (Boolean) -> Unit,
    useRegex: Boolean,
    onUseRegexChange: (Boolean) -> Unit,
    onCloseSearch: () -> Unit,
    onSearchNext: () -> Unit,
    onSearchPrev: () -> Unit,
    onReplaceCurrent: (String) -> Unit,
    onReplaceAll: (String) -> Unit,
    toast: NonBlockingToastState,
    quickActionScrollState: ScrollState,
    symbolBarScrollState: ScrollState,
    // 新增参数
    quickBarVisible: Boolean,
    onSwipe: (SwipeDirection) -> Unit,
    // AI 代码引用回调
    onAiCodeReference: ((filePath: String, fileName: String, startLine: Int, endLine: Int, content: String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val hasOpenFiles = viewModel.openFiles.isNotEmpty()
    val isCompletionLoading by remember { derivedStateOf { viewModel.isCompletionDataLoading } }
    val completionProgress by remember { derivedStateOf { viewModel.completionDataProgress } }

    Column(modifier = modifier) {
        val showProgressBar =
            showInitialLoader || isBuilding || isAutoSaving || isCompilingFile || isCompletionLoading || isBackingUp
        if (showProgressBar) {
            LogCatcher.d("ProgressBar",
                "▶ visible: showInitialLoader=$showInitialLoader isBuilding=$isBuilding " +
                "isAutoSaving=$isAutoSaving isCompilingFile=$isCompilingFile " +
                "isCompletionLoading=$isCompletionLoading isBackingUp=$isBackingUp")
        }
        AnimatedVisibility(visible = showProgressBar) {
            if (isCompletionLoading) {
                // 有真实进度数据 → 确定进度条
                LinearProgressIndicator(
                    progress = { completionProgress },
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = StrokeCap.Butt
                )
            } else {
                // 其他操作（构建/编译/保存/备份等）无进度数据 → 不确定进度条
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = StrokeCap.Butt
                )
            }
        }

        AnimatedVisibility(
            visible = hasOpenFiles && quickBarVisible,
            enter = fadeIn() + expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(300)
            ),
            exit = fadeOut() + shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(200)
            )
        ) {
            QuickActionToolbar(
                actions = quickActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                scrollState = quickActionScrollState
            )
        }

        // 搜索面板
        AnimatedVisibility(visible = isSearchVisible) {
            SearchPanel(
                searchText = searchText,
                onSearchTextChange = onSearchTextChange,
                replaceText = replaceText,
                onReplaceTextChange = onReplaceTextChange,
                caseSensitive = caseSensitive,
                onCaseSensitiveChange = onCaseSensitiveChange,
                wholeWord = wholeWord,
                onWholeWordChange = onWholeWordChange,
                useRegex = useRegex,
                onUseRegexChange = onUseRegexChange,
                onClose = onCloseSearch,
                onSearchNext = onSearchNext,
                onSearchPrev = onSearchPrev,
                onReplaceCurrent = { text -> onReplaceCurrent(text) },
                onReplaceAll = { text -> onReplaceAll(text) }
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LocalDensity.current
            val availableHeight =
                remember(constraints.maxHeight) { constraints.maxHeight.toFloat() }
            LaunchedEffect(availableHeight) {
                if (availableHeight > 0) panelState.updateMaxHeight(
                    availableHeight
                )
            }

            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    FileTabView(
                        viewModel = viewModel,
                        lastFileToOpen = lastFileToOpen,
                        onTabBarRendered = onTabBarRendered,
                        panelState = panelState,
                        onOpenFileTree = {
                            scope.launch { if (fileTreeDrawerState.isClosed) fileTreeDrawerState.open() }
                        },
                        modifier = Modifier.fillMaxSize(),
                        onSwipe = onSwipe, // 传递滑动手势回调
                        onAiCodeReference = onAiCodeReference
                    )
                }
                DraggableSymbolPanel(
                    viewModel = viewModel,
                    panelState = panelState,
                    hasOpenFiles = hasOpenFiles,
                    toast = toast,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private suspend fun loadProjectFiles(
    viewModel: EditorViewModel,
    projectPath: String,
    projectName: String,
    enableTabHistory: Boolean,
    lastFileToOpen: MutableState<String?>
) {
    if (!viewModel.isInitialized) throw IllegalStateException("ViewModel must be initialized before loading project files")
    viewModel.setCurrentProject(projectPath, projectName)

    val historyFiles = viewModel.getAllHistoryFiles()
    val validHistoryFiles = mutableListOf<File>()
    historyFiles.forEach { file ->
        if (file.exists() && file.isFile) validHistoryFiles.add(file)
        else viewModel.removeFileFromHistory(file.absolutePath)
    }

    if (validHistoryFiles.isNotEmpty()) {
        if (enableTabHistory) {
            val lastOpenedFile = viewModel.getLastOpenedFile()
            var targetIndex = 0
            viewModel.openMultipleFiles(validHistoryFiles, projectPath)
            lastOpenedFile?.let { file ->
                if (file.exists() && file.isFile) {
                    lastFileToOpen.value = file.absolutePath
                    val index =
                        validHistoryFiles.indexOfFirst { it.absolutePath == file.absolutePath }
                    if (index != -1) targetIndex = index
                }
            }
            delay(100)
            viewModel.changeActiveFileIndex(targetIndex)
        } else {
            val lastOpenedFile = viewModel.getLastOpenedFile()
            if (lastOpenedFile != null && lastOpenedFile.exists() && lastOpenedFile.isFile) {
                viewModel.openFile(lastOpenedFile, projectPath)
            } else if (validHistoryFiles.isNotEmpty()) {
                viewModel.openFile(validHistoryFiles[0], projectPath)
            }
        }
    } else {
        val mainLuaFile = File(projectPath, "main.lua")
        if (mainLuaFile.exists() && mainLuaFile.isFile) {
            viewModel.openFile(mainLuaFile, projectPath)
        } else {
            val luaFiles =
                File(projectPath).listFiles { _, name -> name.endsWith(".lua", ignoreCase = true) }
            if (luaFiles != null && luaFiles.isNotEmpty()) {
                luaFiles.sortBy { it.name }
                viewModel.openFile(luaFiles[0], projectPath)
            }
        }
    }
    viewModel.cleanupNonExistentFiles()
}

fun colorToHex(color: Color, includeAlpha: Boolean = false): String {
    val alpha = (color.alpha * 255).toInt()
    val red = (color.red * 255).toInt()
    val green = (color.green * 255).toInt()
    val blue = (color.blue * 255).toInt()
    return if (includeAlpha) "#%02X%02X%02X%02X".format(alpha, red, green, blue)
    else "#%02X%02X%02X".format(red, green, blue)
}

@Composable
fun getFileTabIconResource(fileName: String): Int? {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "lua" -> R.drawable.ic_language_lua
        "json" -> R.drawable.ic_code_json
        "aly" -> R.drawable.ic_code_braces
        else -> null
    }
}

@Composable
fun FileTabIcon(
    fileName: String,
    modifier: Modifier = Modifier
) {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val currentColor = LocalContentColor.current
    val iconResId = getFileTabIconResource(fileName)
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        when {
            iconResId != null -> Icon(
                painter = painterResource(id = iconResId),
                contentDescription = "${extension.uppercase()}文件",
                modifier = Modifier.fillMaxSize(),
                tint = currentColor
            )

            else -> {
                val iconVector = when (extension) {
                    "xml" -> Icons.Filled.Code
                    "txt" -> Icons.AutoMirrored.Filled.TextSnippet
                    "html" -> Icons.Filled.Html
                    "css" -> Icons.Filled.Css
                    "js" -> Icons.Filled.Javascript
                    "md" -> Icons.Filled.Description
                    "yml", "yaml" -> Icons.Filled.Settings
                    "properties" -> Icons.Filled.Settings
                    "gradle" -> Icons.Filled.Build
                    "gitignore" -> Icons.Filled.Code
                    "aly" -> Icons.Filled.Code
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                }
                Icon(
                    imageVector = iconVector,
                    contentDescription = "文件",
                    modifier = Modifier.fillMaxSize(),
                    tint = currentColor
                )
            }
        }
    }
}

@Composable
fun DownloadProgressDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    progress: Float,
    currentFile: String,
    currentIndex: Int,
    totalFiles: Int,
    downloadedBytes: Long,
    totalBytes: Long
) {
    if (!showDialog) return

    val formatBytes: (Long) -> String = { bytes ->
        if (bytes <= 0) "--" else {
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            if (mb >= 1) "%.2f MB".format(mb)
            else "%.2f KB".format(kb)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloading_dependencies)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 如果是解析阶段（total为0或1且index为0），显示不确定进度条
                val isResolving = totalFiles <= 1 && currentIndex == 0
                
                if (isResolving) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = currentFile.ifEmpty { "正在准备..." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "正在分析项目依赖，请稍候...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // 正常下载阶段
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // 文件信息
                    Text(
                        text = "正在下载: ${currentFile.takeLast(30)}", // 截断长文件名
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // 计数和大小
                    val downloadedSize = formatBytes(downloadedBytes)
                    val totalSize = formatBytes(totalBytes)
                    Text(
                        text = "$currentIndex / $totalFiles · $downloadedSize / $totalSize",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        dismissButton = {} // 不允许直接关闭，只能取消构建
    )
}