@file:OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.luafabric.studio.falling

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.getSystemService
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import coil.compose.SubcomposeAsyncImage
import com.luafabric.studio.falling.ui.editor.persistence.EditorStateUtil
import com.luafabric.studio.falling.ui.editor.ai.AiChatHistoryStore
import com.luafabric.studio.falling.ui.about.AboutScreen
import com.luafabric.studio.falling.ui.components.FilePickerDialog
import com.luafabric.studio.falling.ui.components.SelectionMode
import com.luafabric.studio.falling.ui.components.Toast
import com.luafabric.studio.falling.ui.editor.CodeEditScreen
import com.luafabric.studio.falling.ui.editor.InstallApkDialog
import com.luafabric.studio.falling.ui.editor.buildProject
import com.luafabric.studio.falling.ui.editor.installApk
import com.luafabric.studio.falling.ui.manual.ManualScreen
import com.luafabric.studio.falling.ui.project.NewProjectScreen
import com.luafabric.studio.falling.ui.settings.DarkMode
import com.luafabric.studio.falling.ui.settings.SettingsManager
import com.luafabric.studio.falling.ui.settings.SettingsScreen
import com.luafabric.studio.falling.ui.settings.SortOrder
import com.luafabric.studio.falling.ui.settings.ToastPosition
import com.luafabric.studio.falling.ui.sponsor.Sponsorship
import com.luafabric.studio.falling.ui.sponsor.SponsorshipDialog
import com.luafabric.studio.falling.ui.theme.AppThemeWithObserver
import com.luafabric.studio.falling.ui.welcome.TransparentSystemBars
import com.luafabric.studio.falling.ui.welcome.WelcomeScreen
import com.luafabric.studio.falling.ui.welcome.hasShownJoinGroupDialog
import com.luafabric.studio.falling.ui.welcome.markJoinGroupDialogShown
import com.luafabric.studio.falling.ui.welcome.saveWelcomeCompleted
import com.luafabric.studio.falling.ui.welcome.shouldShowWelcomeScreen
import muling.views.tool.utils.*
import io.github.tarifchakder.ktoast.ToastData
import io.github.tarifchakder.ktoast.ToastHost
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// 屏幕枚举
enum class AppScreen {
    MAIN,
    NEW_PROJECT,
    EDITOR
}

// 项目数据类
data class ProjectItem(
    val id: String,
    val name: String,
    val path: String,
    val createdDate: Date = Date(),
    val modifiedDate: Date = Date()
) : java.io.Serializable

// 内置分类内部哨兵（\u0000 非用户可输入字符，杜绝与自定义分类名冲突）
const val CATEGORY_FAVORITE = "\u0000favorite"
const val CATEGORY_ALL = "\u0000all"

// 主内容类型枚举
enum class MainContentType {
    PROJECTS,
    FORUM,
    MANUAL,
    SETTINGS,
    ABOUT,
    SPONSOR
}

enum class ConflictAction {
    OVERWRITE, CLONE,
}

@Composable
fun MainApp() {
    TransparentSystemBars()

    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.MAIN) }
    var currentContentType by rememberSaveable { mutableStateOf(MainContentType.PROJECTS) }
    var selectedProject by rememberSaveable { mutableStateOf<ProjectItem?>(null) }
    var projectItems by remember { mutableStateOf(emptyList<ProjectItem>()) }
    val toast = rememberNonBlockingToastState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val settings = SettingsManager.currentSettings
    val toastPosition = settings.toastPosition

    val toastTransitionSpec: AnimatedContentTransitionScope<ToastData?>.() -> ContentTransform = {
        TransitionUtil.createToastPositionedScaleTransition(toastPosition)
    }

    BackHandler(enabled = currentScreen != AppScreen.MAIN) {
        when (currentScreen) {
            AppScreen.NEW_PROJECT -> {
                currentScreen = AppScreen.MAIN
                scope.launch {
                    val projectsPath = FileUtil.getProjectsPath(context)
                    ProjectUtil.loadProjectsFromDirectory(projectsPath) { newItems ->
                        projectItems = newItems
                    }
                }
            }
            AppScreen.EDITOR -> {
                currentScreen = AppScreen.MAIN
                scope.launch {
                    val projectsPath = FileUtil.getProjectsPath(context)
                    ProjectUtil.loadProjectsFromDirectory(projectsPath) { newItems ->
                        projectItems = newItems
                    }
                }
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                val isForward = targetState != AppScreen.MAIN
                TransitionUtil.createScreenTransition(isForward)
            },
            label = "screen_transition"
        ) { targetScreen ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .consumeWindowInsets(WindowInsets.ime)
            ) {
                when (targetScreen) {
                    AppScreen.MAIN -> MainScreen(
                        currentContentType = currentContentType,
                        onCurrentContentTypeChange = { currentContentType = it },
                        onNavigateToNewProject = { currentScreen = AppScreen.NEW_PROJECT },
                        onNavigateToEditor = { project ->
                            selectedProject = project
                            currentScreen = AppScreen.EDITOR
                        },
                        projectItems = projectItems,
                        onProjectItemsChanged = { newItems -> projectItems = newItems },
                        toast = toast
                    )
                    AppScreen.NEW_PROJECT -> {
                        NewProjectScreen(
                            onBack = {
                                currentScreen = AppScreen.MAIN
                                scope.launch {
                                    val projectsPath = FileUtil.getProjectsPath(context)
                                    ProjectUtil.loadProjectsFromDirectory(projectsPath) { newItems ->
                                        projectItems = newItems
                                    }
                                }
                            },
                            onCreateProject = { newProjectData ->
                                LogCatcher.i("MainApp", "项目创建成功: ${newProjectData.projectName}")
                                scope.launch {
                                    val projectsPath = FileUtil.getProjectsPath(context)
                                    ProjectUtil.loadProjectsFromDirectory(projectsPath) { newItems ->
                                        projectItems = newItems
                                    }
                                }
                            },
                            toast = toast
                        )
                    }
                    AppScreen.EDITOR -> {
                        selectedProject?.let { project ->
                            Column {
                                CodeEditScreen(
                                    project = project,
                                    onBack = {
                                        currentScreen = AppScreen.MAIN
                                        scope.launch {
                                            val projectsPath = FileUtil.getProjectsPath(context)
                                            ProjectUtil.loadProjectsFromDirectory(projectsPath) { newItems ->
                                                projectItems = newItems
                                            }
                                        }
                                    },
                                    toast = toast,
                                    onOpenSponsor = {
                                        currentContentType = MainContentType.SPONSOR
                                        currentScreen = AppScreen.MAIN
                                    }
                                )
                            }
                        } ?: run { SideEffect { currentScreen = AppScreen.MAIN } }
                    }
                }
            }
        }

        SettingsManager.pendingSponsorPrompt?.let {
            SponsorshipDialog(
                onSponsor = {
                    SettingsManager.pendingSponsorPrompt = null
                    currentContentType = MainContentType.SPONSOR
                    currentScreen = AppScreen.MAIN
                },
                onDismiss = {
                    SettingsManager.pendingSponsorPrompt = null
                }
            )
        }

        ToastHost(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 64.dp,
                    bottom = 64.dp,
                    start = 24.dp,
                    end = 24.dp
                ),
            alignment = when (toastPosition) {
                ToastPosition.TOP -> Alignment.TopCenter
                ToastPosition.BOTTOM -> Alignment.BottomCenter
            },
            hostState = toast.originalToastState,
            transitionSpec = toastTransitionSpec,
            toast = { toastData -> Toast(toastData) }
        )
    }
}

@Composable
fun MainScreen(
    currentContentType: MainContentType,
    onCurrentContentTypeChange: (MainContentType) -> Unit,
    onNavigateToNewProject: () -> Unit,
    onNavigateToEditor: (ProjectItem) -> Unit,
    projectItems: List<ProjectItem>,
    onProjectItemsChanged: (List<ProjectItem>) -> Unit,
    toast: NonBlockingToastState
) {

    val packageInfo = AppInfoUtil.getPackageInfo()
    val appVersionName = packageInfo?.versionName ?: "1.0.0"
    packageInfo?.versionCode ?: 1
    val copyrightYear = BuildConfig.COPYRIGHT_YEAR

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsManager = SettingsManager
    val currentSettings = settingsManager.currentSettings

    // 搜索相关状态
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // 排序和置顶状态（从设置中读取）
    var sortOrder by remember { mutableStateOf(currentSettings.sortOrder) }
    var pinnedSet by remember { mutableStateOf(currentSettings.pinnedProjects) }

    // 监听设置变化
    LaunchedEffect(currentSettings.sortOrder, currentSettings.pinnedProjects) {
        sortOrder = currentSettings.sortOrder
        pinnedSet = currentSettings.pinnedProjects
    }

    // 更多菜单状态
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // ===== 项目分类 tabs =====
    var selectedCategory by remember { mutableStateOf(CATEGORY_ALL) }
    var showCreateCategory by remember { mutableStateOf(false) }
    var tabMenuIndex by remember { mutableStateOf(-1) }
    var moveProject by remember { mutableStateOf<ProjectItem?>(null) }

    // 项目 → 分类 (缺省=所有)
    val categoryOf: (String) -> String = { id ->
        currentSettings.projectCategory[id] ?: CATEGORY_ALL
    }
    // 所有可显示分类（哨兵收藏/所有 + 自定义有序）
    val categoryTabs by remember(currentSettings.categories) {
        mutableStateOf(listOf(CATEGORY_FAVORITE, CATEGORY_ALL) + currentSettings.categories)
    }

    // ---- 导入源码相关状态 ----
    var showFilePicker by remember { mutableStateOf(false) }
    var selectedImportFile by remember { mutableStateOf<File?>(null) }
    var importSettingsData by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictLabel by remember { mutableStateOf("") }
    var conflictPath by remember { mutableStateOf("") }
    var conflictAction by remember { mutableStateOf<ConflictAction?>(null) }
    // --------------------------

    val projectsPath by remember(currentSettings.projectStoragePath) {
        derivedStateOf {
            FileUtil.getProjectsPath(context)
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteProjectId by remember { mutableStateOf("") }
    var deleteProjectName by remember { mutableStateOf("") }
    var deleteProjectPath by remember { mutableStateOf("") }

    // ---- 构建项目状态 ----
    var buildingProject by remember { mutableStateOf<ProjectItem?>(null) }
    var buildResultApkPath by remember { mutableStateOf<String?>(null) }

    val onProjectBuild: (ProjectItem) -> Unit = { project ->
        scope.launch {
            buildingProject = project
            Sponsorship.recordBuild(context)
            val result = try {
                async<String>(Dispatchers.IO) { buildProject(context, project.path) }.await()
            } catch (e: Exception) {
                LogCatcher.e("MainScreen", "构建项目协程异常", e)
                "error: ${context.getString(R.string.code_editor_build_exception, e.message)}"
            }
            if (result.startsWith("error:")) {
                toast.showToast(context.getString(R.string.code_editor_build_failed, result.substringAfter("error: ")))
            } else {
                buildResultApkPath = result
            }
            buildingProject = null
        }
    }
    // --------------------------

    // 用于处理从设置/关于页返回时的异步操作
    var shouldReturnToProjects by remember { mutableStateOf(false) }

    // 监听返回标志，处理从设置/关于页返回时的异步操作
    LaunchedEffect(shouldReturnToProjects) {
        if (shouldReturnToProjects) {
            // 先关闭抽屉（如果打开）
            if (drawerState.isOpen) {
                drawerState.close()
            }
            // 切换回项目页面
            onCurrentContentTypeChange(MainContentType.PROJECTS)
            shouldReturnToProjects = false
        }
    }

    LaunchedEffect(currentSettings.projectStoragePath) {
        ProjectUtil.loadProjectsFromDirectory(projectsPath, onProjectItemsChanged)
    }

    LaunchedEffect(Unit) {
        ProjectUtil.loadProjectsFromDirectory(projectsPath, onProjectItemsChanged)
    }

    // 搜索过滤后的项目列表
    val filteredProjects by remember(projectItems, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                projectItems
            } else {
                projectItems.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                            it.path.contains(searchQuery, ignoreCase = true)
                }
            }
        }
    }

    // 分组并排序后的项目列表（先按当前分类tab过滤，再置顶分组+排序）
    val displayedProjects by remember(
        filteredProjects, sortOrder, pinnedSet, selectedCategory, currentSettings.projectCategory
    ) {
        derivedStateOf {
            // "所有"标签显示全部项目；其余标签才按分类过滤
            val inCat = if (selectedCategory == CATEGORY_ALL) {
                filteredProjects
            } else {
                filteredProjects.filter { categoryOf(it.id) == selectedCategory }
            }
            // 分为两组：置顶和未置顶
            val pinned = inCat.filter { it.id in pinnedSet }
            val unpinned = inCat.filter { it.id !in pinnedSet }

            // 定义排序比较器
            val comparator = when (sortOrder) {
                SortOrder.NAME_ASC -> compareBy<ProjectItem> { it.name.lowercase() }
                SortOrder.NAME_DESC -> compareByDescending<ProjectItem> { it.name.lowercase() }
                SortOrder.DATE_MODIFIED_NEWEST -> compareByDescending<ProjectItem> { it.modifiedDate }
                SortOrder.DATE_MODIFIED_OLDEST -> compareBy<ProjectItem> { it.modifiedDate }
            }

            pinned.sortedWith(comparator) + unpinned.sortedWith(comparator)
        }
    }

    // 新增状态：待删除的项目ID集合（用于退出动画）
    var pendingDeletionIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 项目列表的标题栏折叠由 exitUntilCollapsedScrollBehavior 处理，
    // 顶部下拉刷新由 TwoStagePullBox 自定义手势接管（先展开标题栏再刷新）
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    var showExtendedFab by remember { mutableStateOf(true) }

    LaunchedEffect(
        lazyListState.firstVisibleItemIndex,
        lazyListState.firstVisibleItemScrollOffset
    ) {
        val isScrolled = lazyListState.firstVisibleItemIndex > 0 ||
                lazyListState.firstVisibleItemScrollOffset > 0
        showExtendedFab = !isScrolled
    }

    val pageOrder =
        listOf(
            MainContentType.PROJECTS,
            MainContentType.FORUM,
            MainContentType.MANUAL,
            MainContentType.SPONSOR,
            MainContentType.SETTINGS,
            MainContentType.ABOUT
        )

    fun showToast(message: String) {
        toast.showToast(message)
    }

    // 下拉刷新：重扫项目目录并强制卡片重新加载元数据
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableIntStateOf(0) }

    fun refreshProjects() {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch {
            ProjectUtil.loadProjectsFromDirectory(projectsPath, onProjectItemsChanged)
            refreshNonce++
            isRefreshing = false
        }
    }

    // 重命名原 deleteProject 为 performDelete，用于实际删除操作（无动画）
    fun performDelete(projectId: String) {
        scope.launch {
            val project = projectItems.find { it.id == projectId }
            project?.let {
                try {
                    val projectDir = File(it.path)
                    if (projectDir.exists() && projectDir.isDirectory) {
                        projectDir.deleteRecursively()
                        EditorStateUtil.cleanProjectState(context, it.path)
                        // 连带删除该项目独立的 AI 对话记录
                        AiChatHistoryStore.deleteAllForProject(context, it.path)

                        // 从置顶集合中移除该项目
                        if (projectId in pinnedSet) {
                            val newPinnedSet = pinnedSet - projectId
                            val newSettings = currentSettings.copy(pinnedProjects = newPinnedSet)
                            SettingsManager.updateSettings(newSettings)
                            // 保存设置（在 IO 线程执行）
                            withContext(Dispatchers.IO) {
                                SettingsManager.saveSettings(context)
                            }
                        }

                        showToast(context.getString(R.string.project_deleted, it.name))
                        ProjectUtil.loadProjectsFromDirectory(projectsPath, onProjectItemsChanged)
                    }
                } catch (e: Exception) {
                    LogCatcher.e("MainScreen", "删除项目失败", e)
                    showToast(context.getString(R.string.delete_failed, e.message))
                }
            }
        }
    }

    // 压缩目录的辅助函数
    suspend fun zipDirectory(sourceDir: File, targetZip: File) {
        withContext(Dispatchers.IO) {
            ZipOutputStream(FileOutputStream(targetZip)).use { zos ->
                sourceDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relativePath = file.relativeTo(sourceDir).path
                        val entry = ZipEntry(relativePath)
                        zos.putNextEntry(entry)
                        file.inputStream().use { input ->
                            input.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    // 分享项目函数
    fun shareProject(project: ProjectItem) {
        scope.launch {
            showToast(context.getString(R.string.preparing_share))
            val zipFile = withContext(Dispatchers.IO) {
                try {
                    // 创建临时缓存目录（内部目录名可保留硬编码）
                    val cacheDir = File(context.cacheDir, "shared_projects")
                    cacheDir.mkdirs()

                    // 生成唯一的 ZIP 文件名
                    val timestamp = System.currentTimeMillis()
                    val zipFileName = "${project.name}_${timestamp}.zip"
                    val zipFile = File(cacheDir, zipFileName)

                    // 压缩项目目录
                    zipDirectory(File(project.path), zipFile)

                    zipFile
                } catch (e: Exception) {
                    LogCatcher.e("MainScreen", "压缩项目失败", e)
                    null
                }
            }

            if (zipFile != null && zipFile.exists()) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_project)))
            } else {
                showToast(context.getString(R.string.share_failed_cannot_compress))
            }
        }
    }

    // 更新排序并保存
    fun updateSortOrder(newOrder: SortOrder) {
        val newSettings = currentSettings.copy(sortOrder = newOrder)
        settingsManager.updateSettings(newSettings)
        settingsManager.saveSettings(context)
        sortMenuExpanded = false
    }

    // 切换项目置顶状态（arrow，列表顶端分组）
    fun togglePinned(projectId: String) {
        val newPinnedSet = if (projectId in pinnedSet) {
            pinnedSet - projectId
        } else {
            pinnedSet + projectId
        }
        val newSettings = currentSettings.copy(pinnedProjects = newPinnedSet)
        settingsManager.updateSettings(newSettings)
        settingsManager.saveSettings(context)
    }

    // 设置项目分类归属（CATEGORY_ALL 即移除记录=所有）
    fun setProjectCategory(projectId: String, category: String) {
        val map = currentSettings.projectCategory.toMutableMap()
        if (category == CATEGORY_ALL) map.remove(projectId) else map[projectId] = category
        settingsManager.updateSettings(currentSettings.copy(projectCategory = map))
        settingsManager.saveSettings(context)
    }

    // 切换收藏（收藏分类成员）
    fun toggleFavorite(projectId: String) {
        setProjectCategory(
            projectId,
            if (categoryOf(projectId) == CATEGORY_FAVORITE) CATEGORY_ALL else CATEGORY_FAVORITE
        )
    }

    // 新建分类
    fun createCategory(name: String) {
        val list = currentSettings.categories + name
        settingsManager.updateSettings(currentSettings.copy(categories = list))
        settingsManager.saveSettings(context)
        selectedCategory = name
    }

    // 删除自定义分类；其成员项目回落"所有"
    fun deleteCategory(name: String) {
        val list = currentSettings.categories.filter { it != name }
        val map = currentSettings.projectCategory.filterValues { it != name }
        settingsManager.updateSettings(currentSettings.copy(categories = list, projectCategory = map))
        settingsManager.saveSettings(context)
        if (selectedCategory == name) selectedCategory = CATEGORY_ALL
    }

    // 当搜索激活时自动请求焦点
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(currentContentType) {
        if (currentContentType != MainContentType.PROJECTS) {
            isSearchActive = false
            searchQuery = ""
        }
    }

    // ---- 导入源码辅助函数 ----
    suspend fun handleImportFileSelected(
        file: File,
        toast: NonBlockingToastState
    ) {
        withContext(Dispatchers.IO) {
            try {
                ZipFile(file).use { zip ->
                    val entry = zip.getEntry("settings.json")
                    if (entry == null) {
                        withContext(Dispatchers.Main) {
                            toast.showToast(context.getString(R.string.config_file_not_found))
                        }
                        return@withContext
                    }
                    val content = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val settings = JsonUtil.parseObject(content)
                    withContext(Dispatchers.Main) {
                        importSettingsData = settings
                        showImportConfirmDialog = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    toast.showToast(context.getString(R.string.parse_failed, e.message))
                }
            }
        }
    }

    suspend fun performImport(
        zipFile: File,
        targetDir: File,
        toast: NonBlockingToastState,
        onComplete: () -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                targetDir.mkdirs()
                FileUtil.extractZip(zipFile, targetDir)
                withContext(Dispatchers.Main) {
                    toast.showToast(context.getString(R.string.import_success))
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    toast.showToast(context.getString(R.string.import_failed, e.message))
                }
            }
        }
    }
    // --------------------------

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                // 抽屉顶部登录卡：波纹覆盖分割线以上整个头区
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* 纯占位 */ }
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 圆形头像占位（暂无用户体系）
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = stringResource(R.string.sign_in_now),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.sign_in_now),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(onClick = { /* 纯占位 */ }) {
                            Text(stringResource(R.string.sign_in))
                        }
                    }
                }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NavigationDrawerItem(
                        label = {
                            Text(stringResource(R.string.projects), fontWeight = FontWeight.Medium)
                        },
                        selected = currentContentType == MainContentType.PROJECTS,
                        onClick = {
                            onCurrentContentTypeChange(MainContentType.PROJECTS)
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = stringResource(R.string.cd_project_folder),
                                tint = if (currentContentType == MainContentType.PROJECTS)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    NavigationDrawerItem(
                        label = {
                            Text(stringResource(R.string.forum), fontWeight = FontWeight.Medium)
                        },
                        selected = currentContentType == MainContentType.FORUM,
                        onClick = {
                            onCurrentContentTypeChange(MainContentType.FORUM)
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            Icon(
                                Icons.Filled.Forum,
                                contentDescription = stringResource(R.string.forum),
                                tint = if (currentContentType == MainContentType.FORUM)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    NavigationDrawerItem(
                        label = {
                            Text(stringResource(R.string.manual), fontWeight = FontWeight.Medium)
                        },
                        selected = currentContentType == MainContentType.MANUAL,
                        onClick = {
                            onCurrentContentTypeChange(MainContentType.MANUAL)
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            Icon(
                                Icons.Filled.MenuBook,
                                contentDescription = stringResource(R.string.manual),
                                tint = if (currentContentType == MainContentType.MANUAL)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    NavigationDrawerItem(
                        label = {
                            Text(stringResource(R.string.sponsor), fontWeight = FontWeight.Medium)
                        },
                        selected = currentContentType == MainContentType.SPONSOR,
                        onClick = {
                            onCurrentContentTypeChange(MainContentType.SPONSOR)
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            Icon(
                                Icons.Filled.MonetizationOn,
                                contentDescription = stringResource(R.string.sponsor),
                                tint = if (currentContentType == MainContentType.SPONSOR)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    NavigationDrawerItem(
                        label = {
                            Text(stringResource(R.string.settings), fontWeight = FontWeight.Medium)
                        },
                        selected = currentContentType == MainContentType.SETTINGS,
                        onClick = {
                            onCurrentContentTypeChange(MainContentType.SETTINGS)
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = if (currentContentType == MainContentType.SETTINGS)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    NavigationDrawerItem(
                        label = {
                            Text(stringResource(R.string.about), fontWeight = FontWeight.Medium)
                        },
                        selected = currentContentType == MainContentType.ABOUT,
                        onClick = {
                            onCurrentContentTypeChange(MainContentType.ABOUT)
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = stringResource(R.string.about),
                                tint = if (currentContentType == MainContentType.ABOUT)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.copyright, copyrightYear),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (isDebuggableBuild(context)) {
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.debug_build_chip),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.version, appVersionName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    ) {
        BackHandler(
            enabled = currentContentType != MainContentType.PROJECTS || drawerState.isOpen,
            onBack = {
                scope.launch {
                    if (drawerState.isOpen) {
                        drawerState.close()
                    } else if (currentContentType != MainContentType.PROJECTS) {
                        shouldReturnToProjects = true
                    }
                }
            }
        )

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        if (isSearchActive && currentContentType == MainContentType.PROJECTS) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = { 
        Text(
            text = stringResource(R.string.search_placeholder),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        ) 
    },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                                        }
                                    }
                                }
                            )
                        } else {
                            Text(
                                text = when (currentContentType) {
                                    MainContentType.PROJECTS -> AppInfoUtil.getAppName(LocalContext.current)
                                    MainContentType.FORUM -> stringResource(R.string.forum)
                                    MainContentType.MANUAL -> stringResource(R.string.manual)
                                    MainContentType.SETTINGS -> stringResource(R.string.settings)
                                    MainContentType.ABOUT -> stringResource(R.string.about)
                                    MainContentType.SPONSOR -> stringResource(R.string.sponsor)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_menu))
                        }
                    },
                    actions = {
                        when (currentContentType) {
                            MainContentType.PROJECTS -> {
                                // 刷新按钮
                                IconButton(onClick = { refreshProjects() }) {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = stringResource(R.string.refresh)
                                    )
                                }

                                // 搜索按钮
                                IconButton(onClick = {
                                    isSearchActive = !isSearchActive
                                    if (!isSearchActive) {
                                        searchQuery = ""
                                    }
                                }) {
                                    Icon(
                                        if (isSearchActive) Icons.Filled.Clear else Icons.Filled.Search,
                                        contentDescription = if (isSearchActive) stringResource(R.string.close_search) else stringResource(R.string.search)
                                    )
                                }

                                // 排序按钮
                                Box {
                                    IconButton(onClick = { sortMenuExpanded = true }) {
                                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort))
                                    }
                                    DropdownMenu(
                                        expanded = sortMenuExpanded,
                                        onDismissRequest = { sortMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_name_asc)) },
                                            onClick = { updateSortOrder(SortOrder.NAME_ASC) },
                                            leadingIcon = if (sortOrder == SortOrder.NAME_ASC) {
                                                { Icon(Icons.AutoMirrored.Filled.Sort, null) }
                                            } else null
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_name_desc)) },
                                            onClick = { updateSortOrder(SortOrder.NAME_DESC) },
                                            leadingIcon = if (sortOrder == SortOrder.NAME_DESC) {
                                                { Icon(Icons.AutoMirrored.Filled.Sort, null) }
                                            } else null
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_date_newest)) },
                                            onClick = { updateSortOrder(SortOrder.DATE_MODIFIED_NEWEST) },
                                            leadingIcon = if (sortOrder == SortOrder.DATE_MODIFIED_NEWEST) {
                                                { Icon(Icons.AutoMirrored.Filled.Sort, null) }
                                            } else null
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_date_oldest)) },
                                            onClick = { updateSortOrder(SortOrder.DATE_MODIFIED_OLDEST) },
                                            leadingIcon = if (sortOrder == SortOrder.DATE_MODIFIED_OLDEST) {
                                                { Icon(Icons.AutoMirrored.Filled.Sort, null) }
                                            } else null
                                        )
                                    }
                                }

                                Box {
                                    IconButton(onClick = { moreMenuExpanded = true }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                                    }
                                    DropdownMenu(
                                        expanded = moreMenuExpanded,
                                        onDismissRequest = { moreMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.import_source)) },
                                            onClick = {
                                                moreMenuExpanded = false
                                                showFilePicker = true
                                            }
                                        )
                                    }
                                }
                            }

                            MainContentType.MANUAL, MainContentType.SETTINGS, MainContentType.ABOUT, MainContentType.SPONSOR, MainContentType.FORUM -> {
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {
                when (currentContentType) {
                    MainContentType.PROJECTS -> {
                        ExtendedFloatingActionButton(
                            onClick = onNavigateToNewProject,
                            icon = {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.cd_add),
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            text = {
                                AnimatedVisibility(
                                    visible = showExtendedFab,
                                    enter = TransitionUtil.createFABTransition(),
                                    exit = TransitionUtil.createFABExitTransition()
                                ) {
                                    Text(stringResource(R.string.create_project))
                                }
                            },
                            expanded = showExtendedFab,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    MainContentType.MANUAL, MainContentType.SETTINGS, MainContentType.ABOUT, MainContentType.SPONSOR, MainContentType.FORUM -> {
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.End
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .consumeWindowInsets(WindowInsets.ime)
            ) {
                AnimatedContent(
                    targetState = currentContentType,
                    transitionSpec = {
                        val currentIndex = pageOrder.indexOf(initialState)
                        val targetIndex = pageOrder.indexOf(targetState)
                        TransitionUtil.createPageTransition(
                            currentIndex = currentIndex,
                            targetIndex = targetIndex
                        )
                    },
                    label = "content_transition"
                ) { targetContentType ->
                    when (targetContentType) {
                        MainContentType.PROJECTS -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // 标题下方、列表上方的分类 tabs（内置收藏/所有 + 自建，右侧固定 + 按钮）
                                CategoryTabBar(
                                    tabs = categoryTabs,
                                    selected = selectedCategory,
                                    onSelect = { selectedCategory = it },
                                    tabMenuIndex = tabMenuIndex,
                                    onTabMenuChange = { tabMenuIndex = it },
                                    onDeleteCategory = { deleteCategory(it) },
                                    onAddClick = { showCreateCategory = true }
                                )
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (displayedProjects.isEmpty()) {
                                        SideEffect {
                                            showExtendedFab = true
                                        }
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                Icons.Outlined.FolderOpen,
                                                contentDescription = stringResource(R.string.cd_project_folder),
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = stringResource(
                                                    if (projectItems.isEmpty()) R.string.no_projects
                                                    else R.string.category_empty
                                                ),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(R.string.create_first_project),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            state = lazyListState,
                                            contentPadding = PaddingValues(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(
                                                items = displayedProjects,
                                                key = { it.id } // 使用唯一ID作为key，确保动画正确
                                            ) { project ->
                                                ProjectCard(
                                                    project = project,
                                                    isFavorite = categoryOf(project.id) == CATEGORY_FAVORITE,
                                                    isPinned = project.id in pinnedSet,
                                                    isPendingDeletion = project.id in pendingDeletionIds, // 传递待删除状态
                                                    onToggleFavorite = { toggleFavorite(project.id) },
                                                    onTogglePinned = { togglePinned(project.id) },
                                                    onMoveToCategory = { moveProject = project },
                                                    onDeleteClick = {
                                                        deleteProjectId = project.id
                                                        deleteProjectName = project.name
                                                        deleteProjectPath = project.path
                                                        showDeleteDialog = true
                                                    },
                                                    onShareClick = { shareProject(project) },
                                                    onBuildClick = { onProjectBuild(project) },
                                                    onClick = { onNavigateToEditor(project) },
                                                    refreshTrigger = refreshNonce,
                                                    modifier = Modifier.animateItem() // 排序时的移动动画
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        MainContentType.FORUM -> {
                            // 源码论坛页，先行置空
                            Box(modifier = Modifier.fillMaxSize())
                        }

                        MainContentType.MANUAL -> {
                            ManualScreen(toast = toast)
                        }

                        MainContentType.SETTINGS -> {
                            SettingsScreen(
                                onBack = {
                                    shouldReturnToProjects = true
                                },
                                currentSettings = currentSettings,
                                onSettingsChanged = { newSettings ->
                                    settingsManager.updateSettings(newSettings)
                                    settingsManager.saveSettings(context)
                                },
                                toast = toast
                            )
                        }

                        MainContentType.ABOUT -> {
                            AboutScreen(
                                onBack = {
                                    shouldReturnToProjects = true
                                }
                            )
                        }

                        MainContentType.SPONSOR -> {
                            SponsorScreen(
                                context = context,
                                toast = toast,
                                scope = scope
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- 创建新分类弹窗 ----
    if (showCreateCategory) {
        CreateCategoryDialog(
            existingNames = listOf(
                stringResource(R.string.favorite),
                stringResource(R.string.category_all)
            ) + currentSettings.categories,
            onDismiss = { showCreateCategory = false },
            onCreate = { name ->
                showCreateCategory = false
                createCategory(name)
            }
        )
    }

    // ---- 移动到分类弹窗 ----
    moveProject?.let { p ->
        CategoryPickDialog(
            options = categoryTabs,
            selected = categoryOf(p.id),
            subtitle = p.name,
            onDismiss = { moveProject = null },
            onPick = { cat ->
                moveProject = null
                setProjectCategory(p.id, cat)
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_project_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_project_confirm), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.project_name_label, deleteProjectName), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.project_path_label, deleteProjectPath), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.delete_project_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        if (deleteProjectId.isNotEmpty()) {
                            pendingDeletionIds = pendingDeletionIds + deleteProjectId
                            scope.launch {
                                delay(300)
                                pendingDeletionIds = pendingDeletionIds - deleteProjectId
                                performDelete(deleteProjectId)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ---- 构建项目弹窗 ----
    if (buildingProject != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.code_editor_build)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.code_editor_building, buildingProject?.name ?: ""),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {}
        )
    }

    if (buildResultApkPath != null) {
        InstallApkDialog(
            showInstallDialog = true,
            apkFilePath = buildResultApkPath,
            onDismiss = {
                buildResultApkPath = null
            },
            onInstall = {
                buildResultApkPath?.let { filePath ->
                    installApk(context, filePath, toast, scope)
                }
                buildResultApkPath = null
            }
        )
    }

    // ---- 导入源码弹窗 ----
    if (showFilePicker) {
        FilePickerDialog(
            initialPath = Environment.getExternalStorageDirectory().absolutePath,
            selectionMode = SelectionMode.FILE,
            title = stringResource(R.string.import_source),
            allowedExtensions = listOf("zip", "alp"),
            onDismiss = { showFilePicker = false },
            onFileSelected = { filePath ->
                showFilePicker = false
                selectedImportFile = File(filePath)
                scope.launch {
                    handleImportFileSelected(
                        selectedImportFile!!,
                        toast
                    )
                }
            }
        )
    }

    if (showImportConfirmDialog && importSettingsData != null) {
        val unknown = stringResource(R.string.unknown)
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = { Text(stringResource(R.string.import_source_title)) },
            text = {
                val settings = importSettingsData!!
                val label = (settings["application"] as? Map<*, *>)?.get("label") as? String ?: unknown
                val packageName = settings["package"] as? String ?: unknown
                val versionName = settings["versionName"] as? String ?: unknown
                val filePath = selectedImportFile?.absolutePath ?: unknown
                Column {
                    Text(stringResource(R.string.project_name_label, label))
                    Text(stringResource(R.string.import_source_package_name, packageName))
                    Text(stringResource(R.string.version_label, versionName))
                    Text(stringResource(R.string.import_source_file_path, filePath))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirmDialog = false
                    val label =
                        ((importSettingsData!!["application"] as? Map<*, *>)?.get("label") as? String)?.trim()
                    if (label.isNullOrBlank()) {
                        scope.launch { toast.showToast(context.getString(R.string.invalid_project_name)) }
                        return@TextButton
                    }
                    val targetDir = File(projectsPath, label)
                    if (targetDir.exists()) {
                        conflictLabel = label
                        conflictPath = targetDir.absolutePath
                        showConflictDialog = true
                    } else {
                        scope.launch {
                            performImport(selectedImportFile!!, targetDir, toast) {
                                scope.launch {
                                    ProjectUtil.loadProjectsFromDirectory(
                                        projectsPath,
                                        onProjectItemsChanged
                                    )
                                }
                            }
                        }
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showConflictDialog) {
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            title = { Text(stringResource(R.string.project_exists_title)) },
            text = {
                Text(stringResource(R.string.project_exists_message, conflictLabel))
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showConflictDialog = false
                        conflictAction = ConflictAction.OVERWRITE
                        val targetDir = File(projectsPath, conflictLabel)
                        scope.launch {
                            targetDir.deleteRecursively()
                            performImport(selectedImportFile!!, targetDir, toast) {
                                scope.launch {
                                    ProjectUtil.loadProjectsFromDirectory(
                                        projectsPath,
                                        onProjectItemsChanged
                                    )
                                }
                            }
                        }
                    }) { Text(stringResource(R.string.overwrite)) }
                    TextButton(onClick = {
                        showConflictDialog = false
                        conflictAction = ConflictAction.CLONE
                        var cloneDir = File(projectsPath, "${conflictLabel}_clone")
                        var counter = 1
                        while (cloneDir.exists()) {
                            counter++
                            cloneDir = File(projectsPath, "${conflictLabel}_clone$counter")
                        }
                        scope.launch {
                            performImport(selectedImportFile!!, cloneDir, toast) {
                                scope.launch {
                                    ProjectUtil.loadProjectsFromDirectory(
                                        projectsPath,
                                        onProjectItemsChanged
                                    )
                                }
                            }
                        }
                    }) { Text(stringResource(R.string.clone)) }
                    TextButton(onClick = { showConflictDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
            dismissButton = {}
        )
    }
}

@SuppressLint("UnrememberedMutableState")
@Composable
fun ProjectCard(
    project: ProjectItem,
    isFavorite: Boolean,
    isPinned: Boolean,
    isPendingDeletion: Boolean,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    onMoveToCategory: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onBuildClick: () -> Unit,
    onClick: () -> Unit,
    refreshTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    var manifestInfo by remember { mutableStateOf<ManifestInfo?>(null) }
    var template by remember { mutableStateOf<String?>(null) }
    var projectSizeBytes by remember { mutableStateOf<Long?>(null) }
    var iconPathState by remember { mutableStateOf("icon.png") } // 相对项目根，随 settings.json 刷新
    val iconFile = remember(project.path, iconPathState) {
        File(project.path, iconPathState)
    }
    val hasIcon by derivedStateOf {
        iconFile.exists() && iconFile.isFile
    }

    // settings.json 变更指纹：属性保存后 mtime 变化 → 下方 LaunchedEffect 重读 iconPath，主页图标即时刷新
    val settingsStamp = File(project.path, "settings.json").lastModified()

    LaunchedEffect(project.path, refreshTrigger, settingsStamp) {
        withContext(Dispatchers.IO) {
            val projectDir = File(project.path)
            if (projectDir.exists() && projectDir.isDirectory) {
                try {
                    projectSizeBytes = projectDir.walkTopDown()
                        .filter { it.isFile }
                        .sumOf { it.length() }
                } catch (e: Exception) {
                    LogCatcher.e("ProjectCard", "计算项目体积失败", e)
                }
                val settingsFile = File(projectDir, "settings.json")
                if (settingsFile.exists() && settingsFile.isFile) {
                    try {
                        val jsonString = settingsFile.readText()
                        val jsonMap = JsonUtil.parseObject(jsonString)

                        val label =
                            (jsonMap["application"] as? Map<*, *>)?.get("label") as? String
                        val packageName = jsonMap["package"] as? String
                        val versionName = jsonMap["versionName"] as? String
                        val debugMode =
                            (jsonMap["application"] as? Map<*, *>)?.get("debugmode") as? Boolean

                        manifestInfo = ManifestInfo(
                            label = label,
                            packageName = packageName,
                            versionName = versionName,
                            debugMode = debugMode
                        )
                        template = jsonMap["template"] as? String
                        // 图标路径读取（净化越界/绝对路径），来源 settings.json，随 stamp/refresh 刷新
                        val rawIconPath = (jsonMap["iconPath"] as? String ?: "icon.png").trim()
                        iconPathState = if (rawIconPath.isEmpty() || rawIconPath.startsWith("/") || rawIconPath.contains("..")) {
                            "icon.png"
                        } else {
                            rawIconPath
                        }
                    } catch (e: Exception) {
                        LogCatcher.e("ProjectCard", "加载项目设置失败", e)
                    }
                } else if (ProjectUtil.isComposeProject(projectDir)) {
                    // Compose 项目：配置在 build.gradle.b85（创建时 encode），主页卡片读 b85 展示
                    try {
                        val cfg = ProjectUtil.loadProjectConfig(projectDir)
                        val label = cfg?.get("name") as? String
                        val packageName = cfg?.get("packageId") as? String
                        val versionName = cfg?.get("versionName") as? String
                        val b85Raw = File(projectDir, muling.views.tool.utils.ComposeConfig.FILE_NAME)
                            .readBytes()
                        val debugMode =
                            muling.views.tool.utils.ComposeConfig.debugFlag(b85Raw)
                        manifestInfo = ManifestInfo(
                            label = label,
                            packageName = packageName,
                            versionName = versionName,
                            debugMode = debugMode
                        )
                        // Compose 模板徽标：b85 无 template 字段（格式与 gen_conf.py 定稿对齐），主页展示固定模板名
                        template = "Compose.zip"
                        iconPathState = ProjectUtil.projectIconPath(projectDir)
                    } catch (e: Exception) {
                        LogCatcher.e("ProjectCard", "加载 Compose 项目配置失败", e)
                    }
                }
            }
        }
    }

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerLow
        ),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 1.dp
        )
    ) {
        AnimatedVisibility(
            visible = !isPendingDeletion,
            enter = fadeIn(animationSpec = tween(300)) + expandVertically(
                animationSpec = tween(300)
            ),
            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(
                animationSpec = tween(300)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasIcon) {
                                SubcomposeAsyncImage(
                                    model = iconFile,
                                    contentDescription = stringResource(R.string.cd_project_icon),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    loading = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(colorScheme.primary.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = colorScheme.primary
                                            )
                                        }
                                    },
                                    error = {
                                        DefaultProjectIcon()
                                    }
                                )
                            } else {
                                DefaultProjectIcon()
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = manifestInfo?.label ?: project.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Column {
                                manifestInfo?.let { info ->
                                    info.packageName?.let { packageName ->
                                        Text(
                                            text = packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    info.versionName?.let { versionName ->
                                        Text(
                                            text = stringResource(R.string.version_label, versionName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (manifestInfo == null) {
                                Text(
                                    text = project.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isFavorite) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = stringResource(R.string.cd_pinned),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.code_editor_more),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.favorite)) },
                                    onClick = {
                                        showMenu = false
                                        onToggleFavorite()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                            contentDescription = null
                                        )
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.pin)) },
                                    onClick = {
                                        showMenu = false
                                        onTogglePinned()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.ArrowUpward,
                                            contentDescription = null,
                                            tint = if (isPinned) colorScheme.primary else colorScheme.onSurfaceVariant
                                        )
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.move_to_category)) },
                                    onClick = {
                                        showMenu = false
                                        onMoveToCategory()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.MoveToInbox,
                                            contentDescription = null
                                        )
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.project_build)) },
                                    onClick = {
                                        showMenu = false
                                        onBuildClick()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_android_studio),
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share)) },
                                    onClick = {
                                        showMenu = false
                                        onShareClick()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Share,
                                            contentDescription = null
                                        )
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.delete), color = colorScheme.error)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDeleteClick()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = colorScheme.outline.copy(alpha = 0.1f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        projectSizeBytes?.let { bytes ->
                            val sizeText = if (bytes >= 1048576L) {
                                String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)
                            } else {
                                String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
                            }
                            Text(
                                text = stringResource(R.string.project_size, sizeText),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = stringResource(R.string.modified_time, dateFormat.format(project.modifiedDate)),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (manifestInfo?.debugMode == true) {
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(colorScheme.error.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.debug_mode),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        template?.let { templateName ->
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(colorScheme.primary.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.project_template_chip,
                                        templateName.removeSuffix(".zip")
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTabBar(
    tabs: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    tabMenuIndex: Int,
    onTabMenuChange: (Int) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onAddClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 可横向滚动 tabs（左侧，weight 撑开）
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .height(46.dp)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, token ->
                val isSel = token == selected
                val isBuiltin = token == CATEGORY_FAVORITE || token == CATEGORY_ALL
                Box {
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (isSel) cs.primaryContainer else cs.surfaceContainerHigh)
                            .combinedClickable(
                                onClick = { onSelect(token) },
                                onLongClick = if (isBuiltin) null else ({ onTabMenuChange(index) })
                            )
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = when (token) {
                                CATEGORY_FAVORITE -> stringResource(R.string.favorite)
                                CATEGORY_ALL -> stringResource(R.string.category_all)
                                else -> token
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSel) cs.onPrimaryContainer else cs.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    // 自定义分类长按删除菜单
                    DropdownMenu(
                        expanded = tabMenuIndex == index,
                        onDismissRequest = { onTabMenuChange(-1) }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = cs.error) },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = cs.error)
                            },
                            onClick = {
                                onTabMenuChange(-1)
                                onDeleteCategory(token)
                            }
                        )
                    }
                }
            }
        }
        // 右侧固定 + 按钮：有圆角方形 primaryContainer 底，不随 tabs 滚动
        Box(
            modifier = Modifier
                .padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
                .size(36.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(cs.primaryContainer)
                .clickable(onClick = onAddClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.cd_add_category),
                tint = cs.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CreateCategoryDialog(
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun vibrate() {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        try {
            v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }
    fun doShake() {
        scope.launch {
            repeat(3) {
                shake.animateTo(10f, tween(50)); shake.animateTo(-10f, tween(50))
            }
            shake.animateTo(0f, tween(50))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_category)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> if (input.length <= 10) { text = input; if (isError) isError = false } },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                isError = isError,
                supportingText = {
                    Text(
                        if (isError) stringResource(R.string.category_exists)
                        else "${text.length}/10"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shake.value }
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    val name = text.trim()
                    if (name.isEmpty()) return@TextButton
                    if (name in existingNames) {
                        isError = true
                        vibrate()
                        doShake()
                    } else {
                        onCreate(name)
                    }
                }
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun CategoryPickDialog(
    options: List<String>,
    selected: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_category)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.project_name_label, subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                options.forEach { token ->
                    val label = when (token) {
                        CATEGORY_FAVORITE -> stringResource(R.string.favorite)
                        CATEGORY_ALL -> stringResource(R.string.category_all)
                        else -> token
                    }
                    val isSel = token == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (isSel) cs.primaryContainer else Color.Transparent)
                            .clickable { onPick(token) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            color = if (isSel) cs.onPrimaryContainer else cs.onSurface,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun DefaultProjectIcon() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                MaterialTheme.shapes.medium
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = stringResource(R.string.cd_project_folder),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}

data class ManifestInfo(
    val label: String? = null,
    val packageName: String? = null,
    val versionName: String? = null,
    val debugMode: Boolean? = null
)

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    WindowCompat.setDecorFitsSystemWindows(window, false)

    val isVersionChanged = intent.getBooleanExtra("isVersionChanged", false)
    val newVersionName = intent.getStringExtra("newVersionName")
    val oldVersionName = intent.getStringExtra("oldVersionName")

    if (isVersionChanged) {
        LogCatcher.i("MainActivity", "检测到版本变更: $oldVersionName -> $newVersionName")
    }

    setContent {
        AppThemeWithObserver {
            SideEffect {
                val window = this@MainActivity.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                val currentSettings = SettingsManager.currentSettings
                val useDarkTheme = when (currentSettings.darkMode) {
                    DarkMode.FOLLOW_SYSTEM -> {
                        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                                Configuration.UI_MODE_NIGHT_YES
                    }
                    DarkMode.LIGHT -> false
                    DarkMode.DARK -> true
                }
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }

            val currentSettings = SettingsManager.currentSettings
            LaunchedEffect(currentSettings.projectStoragePath) {
                SettingsManager.ensureProjectDirectoryExists()
            }

            var shouldShowWelcome by remember { mutableStateOf(shouldShowWelcomeScreen(this@MainActivity)) }
            var showJoinGroupDialog by remember { mutableStateOf(false) }

            Crossfade(targetState = shouldShowWelcome, animationSpec = tween(500)) { showWelcome ->
                if (showWelcome) {
                    WelcomeScreen(
                        onComplete = {
                            saveWelcomeCompleted(this@MainActivity)
                            // 首次完成向导进入主页时，弹出一次「加入官方交流群」
                            if (!hasShownJoinGroupDialog(this@MainActivity)) {
                                markJoinGroupDialogShown(this@MainActivity)
                                showJoinGroupDialog = true
                            }
                            shouldShowWelcome = false
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .consumeWindowInsets(WindowInsets.ime)
                    ) {
                        MainApp()
                    }
                }
            }

            // ---- 首次进入「加入官方交流群」弹窗（一次性） ----
            if (showJoinGroupDialog) {
                AlertDialog(
                    onDismissRequest = { showJoinGroupDialog = false },
                    title = { Text(stringResource(R.string.join_group_title)) },
                    text = {
                        Image(
                            painter = painterResource(R.drawable.join_group_qr),
                            contentDescription = stringResource(R.string.join_group_title),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            contentScale = ContentScale.Fit
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showJoinGroupDialog = false
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(
                                        "http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=A0zdKeRglFVLbmhTgmqLN4xAHbaPI67F" +
                                            "&authKey=lvRArDVBtTWnxOzPK%2F8d4MBrTb8LxbNZkWN0J3oZmehAnJVGRVUjXwzBRcB0as8J" +
                                            "&noverify=0&group_code=1106643491"
                                    )
                                )
                                this@MainActivity.startActivity(intent)
                            }
                        ) {
                            Text(stringResource(R.string.join))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showJoinGroupDialog = false }) {
                            Text(stringResource(R.string.no_thanks))
                        }
                    }
                )
            }
        }
    }
}

    override fun onDestroy() {
        super.onDestroy()
        com.luafabric.studio.falling.ui.editor.viewmodel.CompletionDataManager.clear()
        System.gc()
    }
}

/**
 * 通过 ApplicationInfo 运行时 API 判断当前安装包是否为 debuggable（debug 构建），
 * 不硬编码构建类型
 */
private fun isDebuggableBuild(context: Context): Boolean =
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

private const val SPONSOR_QR_ASSET = "sponsor/sponsor_qr.png"
private const val SPONSOR_QR_FILE_NAME = "sponsor_qr.png"
private const val SPONSOR_QR_MIME = "image/png"
private const val SPONSOR_QR_SUB_DIR = "LuaFabric_Studio"
private const val SPONSOR_QR_RELATIVE_PATH = "Pictures/LuaFabric_Studio/sponsor_qr.png"
private const val WECHAT_PACKAGE = "com.tencent.mm"



@Composable
private fun SponsorScreen(
    context: Context,
    toast: NonBlockingToastState,
    scope: CoroutineScope
) {
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        qrBitmap = withContext(Dispatchers.IO) {
            context.assets.open(SPONSOR_QR_ASSET).use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 280.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val bitmap = qrBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.sponsor_qr_desc),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.sponsor_slogan),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (saving) return@Button
                saving = true
                // 只有点"投喂我"才算赞助，标记跳过下一轮；幂等，多次点击只记一次
                Sponsorship.onFeed(context)
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        try {
                            saveSponsorQrToGallery(context)
                        } catch (e: Exception) {
                            false
                        }
                    }
                    saving = false
                    if (saved) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE)
                        if (launchIntent != null) {
                            runCatching { context.startActivity(launchIntent) }
                        } else {
                            toast.showToast(context.getString(R.string.wechat_not_installed))
                        }
                        toast.showToast(
                            context.getString(R.string.sponsor_saved_toast, SPONSOR_QR_RELATIVE_PATH)
                        )
                    } else {
                        toast.showToast(context.getString(R.string.sponsor_save_failed))
                    }
                }
            },
            enabled = !saving && qrBitmap != null,
            modifier = Modifier.height(48.dp)
        ) {
            Icon(
                Icons.Filled.SetMeal,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.sponsor_feed_button))
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

private fun saveSponsorQrToGallery(context: Context): Boolean {
    val input = context.assets.open(SPONSOR_QR_ASSET)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, SPONSOR_QR_FILE_NAME)
            put(MediaStore.Images.Media.MIME_TYPE, SPONSOR_QR_MIME)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$SPONSOR_QR_SUB_DIR")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: run {
                input.close()
                return false
            }
        try {
            resolver.openOutputStream(uri)?.use { output ->
                input.use { it.copyTo(output) }
            } ?: return false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return false
        }
    } else {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            SPONSOR_QR_SUB_DIR
        )
        if (!dir.exists() && !dir.mkdirs()) {
            input.close()
            return false
        }
        val target = File(dir, SPONSOR_QR_FILE_NAME)
        return input.use { source ->
            target.outputStream().use { output ->
                source.copyTo(output)
            }
            true
        }
    }
}