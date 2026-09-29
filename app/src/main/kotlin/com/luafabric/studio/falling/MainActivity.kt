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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Android
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.Dp
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
import com.luafabric.studio.falling.ui.forum.ForumComposeScreen
import com.luafabric.studio.falling.ui.forum.ForumItem
import com.luafabric.studio.falling.ui.forum.ForumOverlay
import com.luafabric.studio.falling.ui.forum.ForumPostDetailScreen
import com.luafabric.studio.falling.ui.forum.ForumRepository
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import com.luafabric.studio.falling.ui.forum.ForumScreen
import com.luafabric.studio.falling.ui.icons.AccountOffOutlineIcon
import com.luafabric.studio.falling.ui.icons.ClockOutlineIcon
import com.luafabric.studio.falling.ui.icons.CogIcon
import com.luafabric.studio.falling.ui.icons.ImageOffOutlineIcon
import com.luafabric.studio.falling.ui.settings.SettingsData
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

import com.luafabric.studio.falling.native.YunJuBridge
import com.luafabric.studio.falling.ui.login.LoginRepository
import com.luafabric.studio.falling.ui.login.LoginScreen
import com.luafabric.studio.falling.ui.login.LoginStore
import com.luafabric.studio.falling.ui.login.ProfileScreen
import com.luafabric.studio.falling.ui.login.YunJuApi
import com.luafabric.studio.falling.ui.login.YunJuResponse
import coil.compose.AsyncImage
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
    NEW_PROJECT,EDITOR,
    LOGIN
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
    ACCOUNT,
    MANUAL,
    SETTINGS,
    ABOUT,
    SPONSOR
}

// 侧滑栏 4 个功能页：进入后左上角三条线切换为左箭头，点击关闭当前界面返回项目页
private val CLOSEABLE_FUNCTION_PAGES =
    setOf(MainContentType.MANUAL, MainContentType.SETTINGS, MainContentType.ABOUT, MainContentType.SPONSOR)

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
    var loggedInUser by remember { mutableStateOf<YunJuResponse?>(null) }
    // 启动后台重校验期间 true：侧滑栏显示未登录外观但禁止点击，校验返回后放开
    var loginChecking by remember { mutableStateOf(false) }
    val toast = rememberNonBlockingToastState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val settings = SettingsManager.currentSettings
    val toastPosition = settings.toastPosition

    // 启动静默重新校验：保持登录开启且有存账号密码时，POST user_dl 验证登录态
    LaunchedEffect(Unit) {
        android.util.Log.d("LoginRecheck", "recheck start")
        val saved = try {
            LoginStore.read(context)
        } catch (e: Throwable) {
            android.util.Log.e("LoginRecheck", "read failed", e)
            null
        } ?: return@LaunchedEffect
        android.util.Log.d(
            "LoginRecheck",
            "saved: keep=${saved.keepLoggedIn} remember=${saved.rememberAccount} qq=${saved.qq.take(4)} pass=${saved.pass.take(2)} userJson=${saved.user != null}"
        )
        if (saved.keepLoggedIn && saved.qq.isNotBlank() && saved.pass.isNotBlank()) {
            loginChecking = true
            try {
                val result = LoginRepository.login(saved.qq, saved.pass)
                android.util.Log.d(
                    "LoginRecheck",
                    "resp: success=${result.success} code=${result.code} msg=${result.message} user=${result.user?.name}"
                )
                if (result.success && result.user != null) {
                    loggedInUser = result.user
                    // 后端返回最新用户信息（coin/level/exp 等）→ 落库刷新，账户页数据实时
                    LoginStore.updateUser(context, result.user)
                } else {
                    // 官方 code 语义：-2 账号封禁 / -1 登录失败 / 0 登录功能关闭 → 凭据确证无效才清
                    // 未知 code / 响应畸形（code == null）→ 无法证伪，保留本地凭据，防静默丢失
                    when (result.code) {
                        "-2", "-1", "0" -> {
                            android.util.Log.d("LoginRecheck", "clear by code=${result.code}")
                            LoginStore.clear(context)
                        }
                    }
                }
            } catch (e: Exception) {
                // 网络异常：静默保持本地登录态，不因离线清空
                android.util.Log.d("LoginRecheck", "network exception: ${e.javaClass.simpleName} ${e.message}")
            } finally {
                loginChecking = false
            }
        }
    }

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
            AppScreen.LOGIN -> {
                currentScreen = AppScreen.MAIN
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
                        toast = toast,
                        loggedInUser = loggedInUser,
                        loginChecking = loginChecking,
                        onOpenLogin = { currentScreen = AppScreen.LOGIN },
                        onUserUpdated = { loggedInUser = it }
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
                    AppScreen.LOGIN -> {
                        LoginScreen(
                            onBack = { currentScreen = AppScreen.MAIN },
                            onLoginSuccess = { user ->
                                loggedInUser = user
                                currentScreen = AppScreen.MAIN
                            },
                            onWelcome = { name ->
                                toast.showToast(context.getString(R.string.login_welcome, name))
                            },
                            toast = toast
                        )
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
    toast: NonBlockingToastState,
    loggedInUser: YunJuResponse?,
    loginChecking: Boolean,
    onOpenLogin: () -> Unit,
    onUserUpdated: (YunJuResponse?) -> Unit
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

    // 签到请求进行中：点击后立即禁用，后端返回后无论成败恢复可用
    var signingIn by remember { mutableStateOf(false) }

    // 论坛设置弹层（仅 FORUM 页可触发）：cog 图标点开后展示三显示开关
    var showForumSettings by remember { mutableStateOf(false) }

    // 论坛独立界面覆盖层：发帖页 / 帖子详情页全屏承载，覆盖顶栏与底部导航（null=论坛列表页）
    var forumOverlay by remember { mutableStateOf<ForumOverlay?>(null) }

    // 论坛覆盖层激活时吞掉系统返回键：先关覆盖层回论坛列表，而非直接退出/关抽屉
    BackHandler(enabled = forumOverlay != null) {
        forumOverlay = null
    }

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

    // 云居 DAU 上报：每次进入项目列表触发一次（一天可多次）。
    // native 层做 VPN/WLAN 门控 + HTTPS POST，任何失败静默仅记 LogCat(tag=YunJu)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                YunJuBridge.nativeTjAdd(context.applicationContext)
            } catch (e: Exception) {
                LogCatcher.e("YunJu", "nativeTjAdd 异常", e)
            }
        }
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

    // ---- 底部导航栏滚动隐藏/显示 ----
    // 上滑(内容向下滚)隐藏，下滑(内容向上滚)显示；项目/论坛列表都联动
    // 导航栏高度常量：与 bottomBar 固定占位 + 内容区浮层导航栏配合，显隐不改变内容区高度
    val navBarHeightDp = 80.dp
    val forumListState = rememberLazyListState()

    // ---- 源码论坛数据缓存：仅在首次进入与手动刷新时请求，切换导航页不重复请求 ----
    val forumScope = rememberCoroutineScope()
    var forumCache by remember { mutableStateOf<Map<Int, List<ForumItem>>>(emptyMap()) }
    var forumRefreshing by remember { mutableStateOf(false) }
    var forumInitialized by remember { mutableStateOf(false) }
    fun ensureForumPosts(forumId: Int) {
        if (forumCache.containsKey(forumId)) return
        forumScope.launch {
            val list = withContext(Dispatchers.IO) {
                ForumRepository.loadPosts(context, forumId)
            }
            forumCache = forumCache + (forumId to list)
        }
    }
    fun refreshForumPosts(forumId: Int) {
        forumRefreshing = true
        forumScope.launch {
            val list = withContext(Dispatchers.IO) {
                ForumRepository.loadPosts(context, forumId)
            }
            forumCache = forumCache + (forumId to list)
            forumRefreshing = false
        }
    }
    val density = LocalDensity.current.density
    val navBarVisibleState = remember { mutableStateOf(true) }
    val navBarVisible by navBarVisibleState
    // fab 距底间距：导航栏可见时停在其上方，隐藏时沉到底部（平滑过渡）
    val fabGap by animateDpAsState(
        targetValue = if (navBarVisible) navBarHeightDp + 16.dp else 16.dp,
        label = "fabGap"
    )
    var navBarPrevIndex by remember { mutableIntStateOf(0) }
    var navBarPrevOffset by remember { mutableIntStateOf(0) }
    var navBarBackFrames by remember { mutableIntStateOf(0) }
    // 拖动回滚的物理距离累计：短列表贴底后下拉只会被 stretch 过滚动吸收（offset 不变），
    // 但原始拖动位移仍会经 onPreScroll 送达，据此显示导航栏
    val dragBackPx = remember { mutableIntStateOf(0) }
    val dragShowThresholdPx = (16 * density).toInt()
    val navScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (source == NestedScrollSource.Drag) {
                    if (dy < 0) {
                        navBarVisibleState.value = false
                        dragBackPx.intValue = 0
                    } else if (dy > 0) {
                        val acc = dragBackPx.intValue + dy.toInt()
                        if (acc >= dragShowThresholdPx) {
                            navBarVisibleState.value = true
                            dragBackPx.intValue = 0
                        } else {
                            dragBackPx.intValue = acc
                        }
                    }
                } else if (source == NestedScrollSource.Fling && dy < 0) {
                    // 向前(内容向下)甩动：立即隐藏；回弹甩动(Fling 且 dy>0)不参与显示，避免闪烁
                    navBarVisibleState.value = false
                    dragBackPx.intValue = 0
                }
                return Offset.Zero
            }
        }
    }
    // 切分类期间抑制导航栏跟踪：scrollToItem 造成的索引骤变不等价于用户滚动
    var navBarTracking by remember { mutableStateOf(true) }
    // 切分类：重置列表到顶 + 复位导航栏，避免继承旧滚动位置导致列表"自动下滑/跳位"，
    // 及索引骤变误触发底部导航栏/efab 显隐反应
    LaunchedEffect(selectedCategory) {
        navBarTracking = false
        navBarVisibleState.value = true
        navBarBackFrames = 0
        navBarPrevIndex = 0
        navBarPrevOffset = 0
        dragBackPx.intValue = 0
        lazyListState.scrollToItem(0)
        navBarTracking = true
    }
    LaunchedEffect(currentContentType) {
        navBarVisibleState.value = true
        navBarBackFrames = 0
        // 源码论坛：仅刚打开软件后首次进入时自动请求一次初始化，之后切换导航页不再重新请求
        if (currentContentType == MainContentType.FORUM && !forumInitialized) {
            forumInitialized = true
            ensureForumPosts(1)
        }
        val listState = when (currentContentType) {
            MainContentType.PROJECTS -> lazyListState
            MainContentType.FORUM -> forumListState
            else -> return@LaunchedEffect
        }
        snapshotFlow {
            listState.firstVisibleItemIndex to
                (listState.firstVisibleItemScrollOffset to listState.canScrollForward)
        }.collect { (idx, pair) ->
            val (off, canScrollDown) = pair
            // 切分类重置期间：同步 prev 基准后忽略，不触发显隐
            if (!navBarTracking) {
                navBarPrevIndex = idx
                navBarPrevOffset = off
                return@collect
            }
            if (idx != navBarPrevIndex || off != navBarPrevOffset) {
                val movedBack = idx < navBarPrevIndex ||
                    (idx == navBarPrevIndex && off < navBarPrevOffset)
                val movedForward = idx > navBarPrevIndex ||
                    (idx == navBarPrevIndex && off > navBarPrevOffset)
                // 纯方向驱动：canScrollForward 在短列表（内容仅略高于视口）中于未到物理底部
                // 时就会变 false，若以其为隐藏门控会在整段范围持续隐藏、回滚永远不显示。
                if (movedForward) {
                    navBarVisibleState.value = false
                    navBarBackFrames = 0
                } else if (movedBack) {
                    // 连续 2 帧回滚再显示，抑制到底回弹/抖动产生的单帧反向 blip
                    navBarBackFrames += 1
                    if (navBarBackFrames >= 2) {
                        navBarVisibleState.value = true
                        navBarBackFrames = 0
                    }
                }
                android.util.Log.d(
                    "NavScroll",
                    "idx=$idx off=$off canF=$canScrollDown back=${navBarBackFrames} visible=$navBarVisible"
                )
            }
            navBarPrevIndex = idx
            navBarPrevOffset = off
        }
    }

    val pageOrder =
        listOf(
            MainContentType.PROJECTS,
            MainContentType.FORUM,
            MainContentType.ACCOUNT,
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

    // 签到：侧边栏顶部签到按钮（仅登录后显示），POST user_qiandao，成功刷新 sign 状态
    val onSignInClick: () -> Unit = {
        val user = loggedInUser
        if (user != null && !signingIn) {
            // 点击后立即禁用，直到后端返回（无论成功与否都恢复可用）
            signingIn = true
            scope.launch {
                try {
                    val (ok, msg) = LoginRepository.signIn(user.qq)
                    toast.showToast(msg.ifBlank {
                        context.getString(if (ok) R.string.login_sign_success else R.string.login_sign_failed)
                    })
                    if (ok) {
                        // 签到成功后拉取用户实时信息（金币/经验/等级），失败弹提示并降级使用本地数据
                        val fresh = LoginRepository.fetchUserInfo(user.qq)
                        val updated = (fresh ?: user).copy(sign = "true")
                        onUserUpdated(updated)
                        runCatching { LoginStore.updateUser(context, updated) }
                        if (fresh == null) {
                            toast.showToast(context.getString(R.string.login_sign_coin_sync_failed))
                        }
                    }
                } catch (e: Exception) {
                    toast.showToast(context.getString(R.string.login_network_error))
                } finally {
                    signingIn = false
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 论坛覆盖层激活时禁用抽屉边缘手势，避免详情/发帖页被侧滑拉出抽屉
        gesturesEnabled = forumOverlay == null,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                // 抽屉顶部登录/用户卡：整区可点击（校验期间禁点）。未登录→跳登录界面；已登录→个人主页
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .clickable(enabled = !loginChecking) {
                            if (loggedInUser == null) {
                                onOpenLogin()
                            } else {
                                // 已登录 → 跳底部导航「账户」页
                                onCurrentContentTypeChange(MainContentType.ACCOUNT)
                                scope.launch { drawerState.close() }
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (loggedInUser == null) {
                                // 未登录：头像占位（后台重校验期间同款外观但禁点）
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
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                // 已登录：qlogo 头像 + 左(昵称/等级) + 右(金币数/金币)
                                AsyncImage(
                                    model = YunJuApi.avatarUrl(loggedInUser.qq),
                                    contentDescription = loggedInUser.name,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                                // 左列：昵称 + 等级，居左贴头像
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = loggedInUser.name.ifBlank { loggedInUser.qq },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (loggedInUser.level.isNotBlank()) {
                                        Text(
                                            text = stringResource(R.string.login_level_format, loggedInUser.level),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                // 右列：金币数（高亮色区分）+ 金币
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = loggedInUser.coin,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.drawer_coin_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 签到卡片：仅登录后显示（位于分割线上方，另起一行）
                if (loggedInUser != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 左侧容器色圆形容器 + calender 图标 + 文字「签到」
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.sign_in),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            // 左侧内容与右侧签到按钮之间撑开
                            Spacer(modifier = Modifier.weight(1f))
                            // 右侧签到按钮：已签到或请求中禁用，文本切为「已签到」
                            Button(
                                onClick = onSignInClick,
                                enabled = loggedInUser.sign != "true" && !signingIn,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Text(
                                    text = stringResource(
                                        if (loggedInUser.sign == "true") {
                                            R.string.drawer_sign_card_signed
                                        } else {
                                            R.string.sign_in
                                        }
                                    )
                                )
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
                            Text(stringResource(R.string.manual), fontWeight = FontWeight.Medium)
                        },
                        selected = currentContentType == MainContentType.MANUAL,
                        onClick = {
                            onCurrentContentTypeChange(MainContentType.MANUAL)
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            DrawerItemIcon(
                                icon = Icons.Filled.MenuBook,
                                selected = currentContentType == MainContentType.MANUAL,
                                contentDescription = stringResource(R.string.manual)
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
                            DrawerItemIcon(
                                icon = Icons.Filled.MonetizationOn,
                                selected = currentContentType == MainContentType.SPONSOR,
                                contentDescription = stringResource(R.string.sponsor)
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
                            DrawerItemIcon(
                                icon = Icons.Filled.Settings,
                                selected = currentContentType == MainContentType.SETTINGS,
                                contentDescription = stringResource(R.string.settings)
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
                            DrawerItemIcon(
                                icon = Icons.Filled.Info,
                                selected = currentContentType == MainContentType.ABOUT,
                                contentDescription = stringResource(R.string.about)
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
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    if (BuildConfig.DEBUG) R.string.build_chip_test else R.string.build_chip_release
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
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
                // 论坛独立界面覆盖层激活时隐藏顶栏（覆盖层为全窗，参展后自备独立页头）
                if (forumOverlay == null) {
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
                                    MainContentType.ACCOUNT -> stringResource(R.string.account)
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
                        if (currentContentType in CLOSEABLE_FUNCTION_PAGES) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (drawerState.isOpen) drawerState.close()
                                        onCurrentContentTypeChange(MainContentType.PROJECTS)
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_menu))
                            }
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

                            MainContentType.FORUM -> {
                                // 论坛设置：cog 图标（纯波纹无容器色），点击弹论坛设置
                                IconButton(onClick = { showForumSettings = true }) {
                                    Icon(
                                        CogIcon,
                                        contentDescription = stringResource(R.string.forum_settings_title)
                                    )
                                }
                            }

                            MainContentType.MANUAL, MainContentType.SETTINGS, MainContentType.ABOUT, MainContentType.SPONSOR, MainContentType.ACCOUNT -> {
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
                }
            }
        ) { paddingValues ->
            // 外层全窗 Box：论坛独立界面覆盖层放置于此，不受 Scaffold padding 约束，
            // 可覆盖顶栏与底部导航；内层保留原有 padding/ime 逻辑
            Box(modifier = Modifier.fillMaxSize()) {
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
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .nestedScroll(navScrollConnection),
                                            state = lazyListState,
                                            contentPadding = PaddingValues(
                                                start = 16.dp, top = 16.dp, end = 16.dp,
                                                // 底部为浮层导航栏让位：内容可滚至导航栏上方，不裁切也无永久空缺
                                                bottom = 16.dp + navBarHeightDp
                                            ),
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
                            ForumScreen(
                                toast = toast,
                                listState = forumListState,
                                postsCache = forumCache,
                                isRefreshing = forumRefreshing,
                                onEnsureLoaded = { ensureForumPosts(it) },
                                onRefresh = { refreshForumPosts(it) },
                                fabGap = fabGap,
                                loggedInUser = loggedInUser,
                                // 未登录点击互动时跳转「账户」页
                                onRequireLogin = { onCurrentContentTypeChange(MainContentType.ACCOUNT) },
                                onOpenCompose = { forumOverlay = ForumOverlay.Compose },
                                onOpenDetail = { forumOverlay = ForumOverlay.Detail(it) }
                            )
                        }

                        MainContentType.ACCOUNT -> {
                            // 账户页：内嵌于主框架，未登录显示去登录引导
                            ProfileScreen(
                                user = loggedInUser,
                                onLogout = {
                                    scope.launch {
                                        LoginStore.clear(context)
                                        onUserUpdated(null)
                                    }
                                    toast.showToast(context.getString(R.string.profile_logout_success))
                                },
                                onLoginClick = onOpenLogin
                            )
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

                // 底部导航栏浮层：与 bottomBar 占位同尺寸，覆盖内容底部，不参与布局高度
                // → 显隐动画不改变内容区高度，列表无“裁切”观感
                if (currentContentType == MainContentType.PROJECTS ||
                    currentContentType == MainContentType.FORUM ||
                    currentContentType == MainContentType.ACCOUNT
                ) {
                    // Scaffold 默认 contentWindowInsets=systemBars 会把内容区抬起 insets 高度，
                    // 浮层直接 align 底部会悬空 insets 高度（不贴底）；offset 下移贴屏幕底，
                    // 高度 = navBar + inset，inset 区域延伸同色背景覆盖系统三键/手势区
                    val navBottomInsetDp = with(LocalDensity.current) {
                        WindowInsets.navigationBars.getBottom(this).toDp()
                    }
                    AnimatedVisibility(
                        visible = navBarVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        // 主体固定 navBarHeightDp，底部系统栏区域延伸同色背景：
                        // 消除三键/手势区颜色不一致，避免 Material3 默认 insets 叠加导致高度过高
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(navBarHeightDp + navBottomInsetDp)
                                .offset(y = navBottomInsetDp)
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            NavigationBar(
                                modifier = Modifier.height(navBarHeightDp),
                                windowInsets = WindowInsets(0, 0, 0, 0)
                            ) {
                            NavigationBarItem(
                                selected = currentContentType == MainContentType.PROJECTS,
                                onClick = { onCurrentContentTypeChange(MainContentType.PROJECTS) },
                                icon = {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = stringResource(R.string.projects)
                                    )
                                },
                                label = {
                                    if (currentContentType == MainContentType.PROJECTS) {
                                        Text(stringResource(R.string.projects))
                                    }
                                }
                            )
                            NavigationBarItem(
                                selected = currentContentType == MainContentType.FORUM,
                                onClick = { onCurrentContentTypeChange(MainContentType.FORUM) },
                                icon = {
                                    Icon(
                                        Icons.Filled.Forum,
                                        contentDescription = stringResource(R.string.forum)
                                    )
                                },
                                label = {
                                    if (currentContentType == MainContentType.FORUM) {
                                        Text(stringResource(R.string.forum))
                                    }
                                }
                            )
                            NavigationBarItem(
                                selected = currentContentType == MainContentType.ACCOUNT,
                                onClick = { onCurrentContentTypeChange(MainContentType.ACCOUNT) },
                                icon = {
                                    Icon(
                                        Icons.Filled.AccountCircle,
                                        contentDescription = stringResource(R.string.account)
                                    )
                                },
                                label = {
                                    if (currentContentType == MainContentType.ACCOUNT) {
                                        Text(stringResource(R.string.account))
                                    }
                                }
                            )
                            }
                        }
                    }
                }

                // 新建项目 FAB：导航栏可见时停在其上方，隐藏时沉底（fabGap 平滑过渡）
                if (currentContentType == MainContentType.PROJECTS) {
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
                        // 圆角跟随 luafabric 主题设置，与源码论坛发帖 FAB 保持一致
                        shape = RoundedCornerShape(forumThemeRadius()),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 16.dp)
                            .padding(bottom = fabGap)
                    )
                }
            }

            // 论坛独立界面覆盖层：发帖页 / 详情页全屏承载，置于底部导航与 FAB 之上；
            // clickable 空实现吞掉点击，防止触摸穿透到底层导航/FAB；返回后回到论坛列表
            if (forumOverlay != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .imePadding()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    when (val ov = forumOverlay) {
                        is ForumOverlay.Compose -> ForumComposeScreen(
                            toast = toast,
                            onBack = { forumOverlay = null }
                        )
                        is ForumOverlay.Detail -> ForumPostDetailScreen(
                            post = ov.post,
                            activeUser = loggedInUser,
                            toast = toast,
                            onRequireLogin = { onCurrentContentTypeChange(MainContentType.ACCOUNT) },
                            onBack = { forumOverlay = null }
                        )
                        null -> {}
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

    // ---- 论坛设置弹层 ----
    if (showForumSettings) {
        ForumSettingsSheet(
            currentSettings = currentSettings,
            onToggle = { newSettings ->
                settingsManager.updateSettings(newSettings)
                settingsManager.saveSettings(context)
            },
            onDismiss = { showForumSettings = false }
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
    // 主题圆角由 MaterialTheme.shapes.medium 派生，直接复用其 CornerSize 而非换算像素
    val mediumCorner: CornerSize =
        (MaterialTheme.shapes.medium as? RoundedCornerShape)?.topStart ?: CornerSize(12.dp)
    val smallCorner: CornerSize = CornerSize(6.dp)
    // 按钮总高 = 文本行高(lineHeight sp→dp) + 上下 9dp 内边距
    val chipHeight = with(LocalDensity.current) {
        MaterialTheme.typography.labelLarge.lineHeight.toDp() + 18.dp
    }
    // 收藏：左上左下大圆角（组首贴屏幕），右上右下小圆角；所有：与之镜像（左小右大）；
    // 二者拼接成一体、右侧分割线与其他分类隔开；其余自定义分类为普通全圆角胶囊
    fun chipShape(token: String): RoundedCornerShape =
        when (token) {
            CATEGORY_FAVORITE -> RoundedCornerShape(
                topStart = mediumCorner, topEnd = smallCorner,
                bottomEnd = smallCorner, bottomStart = mediumCorner
            )
            CATEGORY_ALL -> RoundedCornerShape(
                topStart = smallCorner, topEnd = mediumCorner,
                bottomEnd = mediumCorner, bottomStart = smallCorner
            )
            else -> RoundedCornerShape(mediumCorner)
        }
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
                // 选中底/文字色平滑过渡，消除切换分类时底色突变的闪烁观感
                val chipBg by animateColorAsState(
                    if (isSel) cs.primaryContainer else cs.surfaceContainerHigh,
                    label = "categoryChipBg"
                )
                val chipFg by animateColorAsState(
                    if (isSel) cs.onPrimaryContainer else cs.onSurfaceVariant,
                    label = "categoryChipFg"
                )
                Box {
                    Box(
                        modifier = Modifier
                            .clip(chipShape(token))
                            .background(chipBg)
                            .combinedClickable(
                                onClick = { onSelect(token) },
                                onLongClick = if (isBuiltin) {
                                    null
                                } else {
                                    { onTabMenuChange(index) }
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = if (isBuiltin) {
                                stringResource(
                                    if (token == CATEGORY_FAVORITE) R.string.favorite else R.string.category_all
                                )
                            } else {
                                token
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = chipFg,
                            maxLines = 1
                        )
                    }
                    // 自定义分类长按删除菜单
                    if (!isBuiltin) {
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
                // 「所有」右侧细分割线：与类别按钮同高，把 收藏/所有 与其他分类隔开
                if (token == CATEGORY_ALL) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(chipHeight)
                            .background(cs.outlineVariant)
                    )
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
            imageVector = Icons.Filled.Android,
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

/** 论坛/项目页 FAB 圆角：跟随 luafabric 主题 shapeSizeIndex（与 ForumScreen.themeRadius 一致） */
private fun forumThemeRadius(): Dp = when (SettingsManager.currentSettings.shapeSizeIndex) {
    0 -> 4.dp
    1 -> 8.dp
    2 -> 12.dp
    3 -> 16.dp
    else -> 12.dp
}

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

/** 抽屉功能项图标：选中态 primaryContainer 圆底 + primary 图标；未选中无底色、onSurfaceVariant 图标 */
@Composable
private fun DrawerItemIcon(
    icon: ImageVector,
    selected: Boolean,
    contentDescription: String?
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** 论坛设置底部弹层：三显示开关（隐藏非己头像 / 快速帖子列表 / 相对发帖日期），均默认关闭 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForumSettingsSheet(
    currentSettings: SettingsData,
    onToggle: (SettingsData) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.forum_settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            ForumSettingsSwitchRow(
                icon = AccountOffOutlineIcon,
                title = stringResource(R.string.forum_settings_hide_avatar),
                desc = stringResource(R.string.forum_settings_hide_avatar_desc),
                checked = currentSettings.forumHideOtherAvatars,
                onCheckedChange = { onToggle(currentSettings.copy(forumHideOtherAvatars = it)) }
            )
            ForumSettingsSwitchRow(
                icon = ImageOffOutlineIcon,
                title = stringResource(R.string.forum_settings_hide_images),
                desc = stringResource(R.string.forum_settings_hide_images_desc),
                checked = currentSettings.forumHideOtherImages,
                onCheckedChange = { onToggle(currentSettings.copy(forumHideOtherImages = it)) }
            )
            ForumSettingsSwitchRow(
                icon = ClockOutlineIcon,
                title = stringResource(R.string.forum_settings_relative_date),
                desc = stringResource(R.string.forum_settings_relative_date_desc),
                checked = currentSettings.forumRelativeDate,
                onCheckedChange = { onToggle(currentSettings.copy(forumRelativeDate = it)) }
            )
            Spacer(modifier = Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

/** 论坛设置弹层内单行开关：图标 + 标题/副标题 + Switch */
@Composable
private fun ForumSettingsSwitchRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}