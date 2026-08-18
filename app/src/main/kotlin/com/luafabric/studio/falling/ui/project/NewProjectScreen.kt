@file:OptIn(
    ExperimentalMaterial3Api::class,
    com.google.accompanist.permissions.ExperimentalPermissionsApi::class,
    ExperimentalLayoutApi::class
)

package com.luafabric.studio.falling.ui.project

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.components.MarkdownDialog
import com.luafabric.studio.falling.ui.components.SwitchBar
import muling.views.tool.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 全局工具项数据类 - 使用资源 ID
data class GlobalUtilItem(
    val nameResId: Int,
    val description: String
)

// 全局工具选项列表（使用资源 ID）
val globalUtilsOptions = listOf(
    GlobalUtilItem(R.string.global_util_bitmap, "doc/BitmapUtil.md"),
    GlobalUtilItem(R.string.global_util_glide, "doc/GlideUtil.md"),
    GlobalUtilItem(R.string.global_util_okhttp, "doc/OkHttpUtil.md"),
    GlobalUtilItem(R.string.global_util_ui, "doc/UiUtil.md"),
    GlobalUtilItem(R.string.global_util_recycler, "doc/RecyclerAdapterUtil.md"),
    GlobalUtilItem(R.string.global_util_theme, "doc/ThemeUtil.md")
)

data class TemplateItem(
    val name: String,
    val zipFileName: String,
    val previewPath: String? = null,
    val previewUri: Uri? = null
)

data class NewProjectData(
    val projectName: String,
    val packageName: String,
    val debugMode: Boolean,
    val selectedTemplate: TemplateItem?,
    val iconUri: Uri?,
    val globalUtils: List<String> = emptyList()
)

enum class NewProjectPage {
    TEMPLATE_SELECTION,
    PROJECT_INFO
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun NewProjectScreen(
    onBack: () -> Unit,
    onCreateProject: (NewProjectData) -> Unit,
    toast: NonBlockingToastState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var currentPage by remember { mutableStateOf(NewProjectPage.TEMPLATE_SELECTION) }
    var projectName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var debugMode by remember { mutableStateOf(true) }
    var selectedTemplate by remember { mutableStateOf<TemplateItem?>(null) }
    var projectIconUri by remember { mutableStateOf<Uri?>(null) }
    var selectedGlobalUtils by remember { mutableStateOf<Set<String>>(emptySet()) }
    var templates by remember { mutableStateOf<List<TemplateItem>>(emptyList()) }
    var isLoadingTemplates by remember { mutableStateOf(true) }
    var isCreating by remember { mutableStateOf(false) }
    var showExtendedFab by remember { mutableStateOf(true) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                projectIconUri = it
                LogCatcher.i("NewProjectScreen", "选择了项目图标: ${it}")
            }
        }
    )

    fun showToast(message: String) {
        scope.launch {
            toast.showToast(message)
        }
    }

    fun createProject() {
        if (projectName.isBlank()) {
            showToast(context.getString(R.string.new_project_name_empty_error))
            return
        }

        if (packageName.isBlank()) {
            showToast(context.getString(R.string.new_project_package_empty_error))
            return
        }

        if (selectedTemplate == null) {
            showToast(context.getString(R.string.new_project_template_not_selected_error))
            return
        }

        val projectsDir = FileUtil.getProjectsDirectory(context)
        val projectDir = File(projectsDir, projectName)
        if (projectDir.exists()) {
            showToast(context.getString(R.string.new_project_name_exists_error))
            return
        }

        if (!ProjectUtil.isValidPackageName(packageName)) {
            showToast(context.getString(R.string.new_project_invalid_package_error))
            return
        }

        isCreating = true

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    LogCatcher.i("NewProjectScreen", "开始创建项目: $projectName")

                    projectDir.mkdirs()

                    selectedTemplate?.let { template ->
                        LogCatcher.i("NewProjectScreen", "解压模板: ${template.name}")
                        ProjectUtil.extractTemplate(
                            context = context,
                            template = template,
                            projectDir = projectDir,
                            projectName = projectName,
                            packageName = packageName,
                            debugMode = debugMode
                        )
                    }

                    projectIconUri?.let { uri ->
                        LogCatcher.i("NewProjectScreen", "复制项目图标")
                        ProjectUtil.copyIconToProject(context, uri, projectDir)
                    }

                    LogCatcher.i("NewProjectScreen", "保存设置文件")
                    ProjectUtil.saveSettingsFile(
                        projectDir = projectDir,
                        projectName = projectName,
                        packageName = packageName,
                        debugMode = debugMode,
                        globalUtils = selectedGlobalUtils.toList()
                    )

                    ProjectUtil.updateMainLuaFile(projectDir, projectName)
                }

                withContext(Dispatchers.Main) {
                    showToast(context.getString(R.string.new_project_create_success))
                    onCreateProject(
                        NewProjectData(
                            projectName = projectName,
                            packageName = packageName,
                            debugMode = debugMode,
                            selectedTemplate = selectedTemplate,
                            iconUri = projectIconUri,
                            globalUtils = selectedGlobalUtils.toList()
                        )
                    )
                    onBack()
                }
            } catch (e: Exception) {
                LogCatcher.e("NewProjectScreen", "创建项目失败", e)
                withContext(Dispatchers.Main) {
                    showToast(context.getString(R.string.new_project_create_failed, e.message))
                }
            } finally {
                isCreating = false
            }
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        LogCatcher.i("NewProjectScreen", "从设置页面返回")
        scope.launch {
            delay(500)
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }

            if (hasPermission) {
                createProject()
            } else {
                toast.showToast(context.getString(R.string.new_project_permission_needed))
            }
        }
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        LogCatcher.i("NewProjectScreen", "权限请求结果: $permissionsMap")
        scope.launch {
            delay(300)
            if (permissionsMap.values.all { it }) {
                createProject()
            } else {
                toast.showToast(context.getString(R.string.new_project_permission_denied))
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = "package:${context.packageName}".toUri()
                settingsLauncher.launch(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    settingsLauncher.launch(intent)
                } catch (e2: Exception) {
                    LogCatcher.e("NewProjectScreen", "无法打开文件权限设置页面", e2)
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                    })
                }
            }
        } else {
            val permissionsToRequest = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            multiplePermissionsLauncher.launch(permissionsToRequest)
        }
    }

    LaunchedEffect(Unit) {
        val projectsDir = FileUtil.getProjectsDirectory(context)
        val defaultName = ProjectUtil.generateDefaultProjectName(projectsDir)
        projectName = defaultName
        packageName = ProjectUtil.generatePackageName(defaultName)
    }

    LaunchedEffect(Unit) {
        ProjectUtil.loadTemplates(context) { templateList ->
            templates = templateList
            isLoadingTemplates = false
        }
    }

    LaunchedEffect(projectName) {
        if (projectName.isNotBlank()) {
            packageName = ProjectUtil.generatePackageName(projectName)
        }
    }

    BackHandler {
        when (currentPage) {
            NewProjectPage.TEMPLATE_SELECTION -> onBack()
            NewProjectPage.PROJECT_INFO -> currentPage = NewProjectPage.TEMPLATE_SELECTION
        }
    }

    val pageOrder = listOf(NewProjectPage.TEMPLATE_SELECTION, NewProjectPage.PROJECT_INFO)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        when (currentPage) {
                            NewProjectPage.TEMPLATE_SELECTION -> stringResource(R.string.new_project_title_select_template)
                            NewProjectPage.PROJECT_INFO -> stringResource(R.string.new_project_title_project_info)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (currentPage) {
                            NewProjectPage.TEMPLATE_SELECTION -> onBack()
                            NewProjectPage.PROJECT_INFO -> currentPage = NewProjectPage.TEMPLATE_SELECTION
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            when (currentPage) {
                NewProjectPage.PROJECT_INFO -> {
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (checkStoragePermission()) {
                                createProject()
                            } else {
                                requestStoragePermission()
                            }
                        },
                        icon = {
                            if (isCreating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Create, contentDescription = stringResource(R.string.create_project))
                            }
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
                        expanded = showExtendedFab
                    )
                }
                else -> {}
            }
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                val currentIndex = pageOrder.indexOf(initialState)
                val targetIndex = pageOrder.indexOf(targetState)

                TransitionUtil.createPageTransition(
                    currentIndex = currentIndex,
                    targetIndex = targetIndex
                )
            },
            label = "new_project_pages"
        ) { targetPage ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (targetPage) {
                    NewProjectPage.TEMPLATE_SELECTION -> {
                        TemplateSelectionPage(
                            templates = templates,
                            isLoadingTemplates = isLoadingTemplates,
                            selectedTemplate = selectedTemplate,
                            onTemplateSelected = { template ->
                                selectedTemplate = template
                                currentPage = NewProjectPage.PROJECT_INFO
                            },
                            toast = toast
                        )
                    }

                    NewProjectPage.PROJECT_INFO -> {
                        ProjectInfoPage(
    projectName = projectName,
    onProjectNameChanged = { projectName = it },
    packageName = packageName,
    onPackageNameChanged = { packageName = it },
    debugMode = debugMode,
    onDebugModeChanged = { debugMode = it },
    projectIconUri = projectIconUri,
    onProjectIconSelected = { uri -> projectIconUri = uri },
    selectedTemplate = selectedTemplate,
    selectedGlobalUtils = selectedGlobalUtils,
    onGlobalUtilsChanged = { selectedGlobalUtils = it },
    pickImageLauncher = pickImageLauncher,
    focusManager = focusManager,
    onFabExpandedChange = { expanded ->
        showExtendedFab = expanded
    }
)
                    }
                }
            }
        }
    }
}

@Composable
fun TemplateSelectionPage(
    templates: List<TemplateItem>,
    isLoadingTemplates: Boolean,
    selectedTemplate: TemplateItem?,
    onTemplateSelected: (TemplateItem) -> Unit,
    toast: NonBlockingToastState
) {
    val context = LocalContext.current

    if (isLoadingTemplates) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.new_project_loading_templates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (templates.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Filled.Dashboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = stringResource(R.string.new_project_no_templates),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.new_project_no_templates_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(templates) { template ->
                    TemplateCard(
                        template = template,
                        isSelected = selectedTemplate == template,
                        onClick = { onTemplateSelected(template) },
                        context = context
                    )
                }
            }
        }
    }
}

@Composable
fun TemplateCard(
    template: TemplateItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    context: android.content.Context
) {
    var previewImage by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(template) {
        withContext(Dispatchers.IO) {
            try {
                previewImage = ProjectUtil.loadTemplatePreview(context, template)
            } catch (e: Exception) {
                LogCatcher.e("TemplateCard", "加载模板预览失败: ${template.name}", e)
                previewImage = null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.65f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.large
            )
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 8.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                when (previewImage) {
                    is Uri -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(previewImage)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.new_project_template_preview_content_desc),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Filled.Dashboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = template.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@SuppressLint("FrequentlyChangingValue")
@Composable
fun ProjectInfoPage(
    projectName: String,
    onProjectNameChanged: (String) -> Unit,
    packageName: String,
    onPackageNameChanged: (String) -> Unit,
    debugMode: Boolean,
    onDebugModeChanged: (Boolean) -> Unit,
    projectIconUri: Uri?,
    onProjectIconSelected: (Uri?) -> Unit,
    selectedTemplate: TemplateItem?,
    selectedGlobalUtils: Set<String>,
    onGlobalUtilsChanged: (Set<String>) -> Unit,
    pickImageLauncher: androidx.activity.compose.ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onFabExpandedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollState.value) {
        val expanded = scrollState.value <= 50
        onFabExpandedChange(expanded)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            selectedTemplate?.let { template ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Dashboard,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.new_project_template_selected, template.name),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.cd_project_icon),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .clickable {
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (projectIconUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(projectIconUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.cd_project_icon),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_studio),
                                contentDescription = stringResource(R.string.new_project_default_icon_content_desc),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.new_project_icon_select_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.new_project_basic_info_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.new_project_name_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = onProjectNameChanged,
                        label = { Text(stringResource(R.string.new_project_name_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        leadingIcon = {
                            Icon(Icons.Filled.Title, contentDescription = null)
                        },
                        trailingIcon = {
                            if (projectName.isNotBlank()) {
                                IconButton(
                                    onClick = { onProjectNameChanged("") }
                                ) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = stringResource(R.string.clear),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.new_project_package_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = onPackageNameChanged,
                        label = { Text(stringResource(R.string.new_project_package_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        leadingIcon = {
                            Icon(Icons.Filled.Code, contentDescription = null)
                        },
                        trailingIcon = {
                            if (packageName.isNotBlank()) {
                                IconButton(
                                    onClick = { onPackageNameChanged("") }
                                ) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = stringResource(R.string.clear),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                SwitchBar(
                    checked = debugMode,
                    onCheckedChange = onDebugModeChanged,
                    text = stringResource(R.string.debug_mode),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.new_project_global_utils_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(R.string.new_project_global_utils_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    globalUtilsOptions.forEach { util ->
                        CompactUtilCard(
                            util = util,
                            selected = selectedGlobalUtils.contains(stringResource(util.nameResId)),
                            onSelectedChange = { selected ->
                                val utilName = context.getString(util.nameResId)
                                val newSet = selectedGlobalUtils.toMutableSet()
                                if (selected) {
                                    newSet.add(utilName)
                                } else {
                                    newSet.remove(utilName)
                                }
                                onGlobalUtilsChanged(newSet)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactUtilCard(
    util: GlobalUtilItem,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMarkdownDialog by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        },
        label = "card_background"
    )

    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        },
        label = "card_border"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "card_content"
    )

    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            )
            .combinedClickable(
                onClick = { onSelectedChange(!selected) },
                onLongClick = { showMarkdownDialog = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(util.nameResId),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showMarkdownDialog) {
        val mdFileName = util.description.substringAfter("doc/")

        MarkdownDialog(
            mdFileName = mdFileName,
            onDismissRequest = { showMarkdownDialog = false }
        )
    }
}