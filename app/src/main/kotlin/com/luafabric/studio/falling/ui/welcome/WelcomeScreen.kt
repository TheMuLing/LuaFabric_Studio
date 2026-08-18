package com.luafabric.studio.falling.ui.welcome

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.luafabric.studio.falling.R
import muling.views.tool.utils.LogCatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class WelcomeState(
    val currentPage: Int = 0,
    val totalPages: Int = 2,
    val permissionsGranted: Boolean = false,
    val permissionsList: List<PermissionItem> = emptyList()
)

data class PermissionItem(
    val name: String,
    val permission: String,
    val granted: Boolean,
    val requiredForVersion: String,
    val description: String,
    val isSpecialPermission: Boolean = false
)

fun shouldShowWelcomeScreen(context: Context): Boolean {
    val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    return !sharedPrefs.getBoolean("welcome_completed", false)
}

fun saveWelcomeCompleted(context: Context) {
    val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    sharedPrefs.edit().putBoolean("welcome_completed", true).apply()
}

@Composable
fun TransparentSystemBars() {
    val view = LocalView.current
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = (view.context as Activity).window

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val isDarkMode = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !isDarkMode
            isAppearanceLightNavigationBars = !isDarkMode
        }

        onDispose {
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun WelcomeScreen(
    onComplete: () -> Unit,
    onSkipWelcome: () -> Unit
) {
    var welcomeState by remember { mutableStateOf(WelcomeState()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 获取所需权限列表，传入 context
    val requiredPermissions = remember(context) {
        getRequiredPermissionsForVersion(context)
    }

    fun updatePermissionsState() {
        val updatedPermissions = requiredPermissions.map { item ->
            val isGranted = when {
                item.permission == Manifest.permission.MANAGE_EXTERNAL_STORAGE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Environment.isExternalStorageManager()
                    } else {
                        true
                    }
                }

                item.permission == Manifest.permission.INTERNET -> true
                item.permission == "ANDROID_10_STORAGE_PERMISSIONS" -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        val readGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                        val writeGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                        readGranted && writeGranted
                    } else {
                        true
                    }
                }

                else -> {
                    ContextCompat.checkSelfPermission(context, item.permission) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }

            item.copy(granted = isGranted)
        }

        val userOperatedPermissions = updatedPermissions.filter {
            it.permission != Manifest.permission.INTERNET
        }
        val allGranted = userOperatedPermissions.all { it.granted }

        welcomeState = welcomeState.copy(
            permissionsList = updatedPermissions,
            permissionsGranted = allGranted
        )

        LogCatcher.d("WelcomeScreen", "权限状态更新 - 用户操作权限已全部授权: $allGranted")
    }

    val singlePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        LogCatcher.d("WelcomeScreen", "权限请求结果: $isGranted")
        updatePermissionsState()
        scope.launch {
            delay(50)
            updatePermissionsState()
        }
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        LogCatcher.d("WelcomeScreen", "多权限请求结果: $permissionsMap")
        updatePermissionsState()
        scope.launch {
            delay(50)
            updatePermissionsState()
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        LogCatcher.d("WelcomeScreen", "从设置页面返回")
        scope.launch {
            delay(200)
            updatePermissionsState()
        }
    }

    fun requestPermission(permission: String, isSpecialPermission: Boolean = false) {
        when {
            permission == "ANDROID_10_STORAGE_PERMISSIONS" -> {
                val permissionsToRequest = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                multiplePermissionsLauncher.launch(permissionsToRequest)
            }

            permission == Manifest.permission.MANAGE_EXTERNAL_STORAGE &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${context.packageName}")
                    settingsLauncher.launch(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        settingsLauncher.launch(intent)
                    } catch (e2: Exception) {
                        LogCatcher.e("WelcomeScreen", "无法打开文件权限设置页面", e2)
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        })
                    }
                }
            }

            permission == Manifest.permission.INTERNET -> {
                LogCatcher.d("WelcomeScreen", "网络权限是自动授予的，无需请求")
            }

            else -> {
                singlePermissionLauncher.launch(permission)
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        updatePermissionsState()
    }

    val pageTransitionDuration = 300
    val pageTransitionEasing = LinearEasing

    val pageTransitionSpec: AnimatedContentTransitionScope<Int>.() -> ContentTransform = {
        val direction = if (targetState > initialState)
            AnimatedContentTransitionScope.SlideDirection.Left
        else
            AnimatedContentTransitionScope.SlideDirection.Right

        slideIntoContainer(
            towards = direction,
            animationSpec = tween(
                durationMillis = pageTransitionDuration,
                easing = pageTransitionEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = pageTransitionDuration,
                easing = pageTransitionEasing
            )
        ) togetherWith slideOutOfContainer(
            towards = direction,
            animationSpec = tween(
                durationMillis = pageTransitionDuration,
                easing = pageTransitionEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = pageTransitionDuration,
                easing = pageTransitionEasing
            )
        )
    }

    TransparentSystemBars()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = welcomeState.currentPage,
                    transitionSpec = pageTransitionSpec,
                    label = "page_transition"
                ) { page ->
                    when (page) {
                        0 -> WelcomePage1()
                        1 -> PermissionsPage(
                            permissionsList = welcomeState.permissionsList,
                            onRequestPermission = { permission, isSpecial ->
                                requestPermission(permission, isSpecial)
                            },
                            allGranted = welcomeState.permissionsGranted
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                AnimatedPageIndicator(
                    currentPage = welcomeState.currentPage,
                    totalPages = welcomeState.totalPages
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isFirstPage = welcomeState.currentPage == 0

                    AnimatedVisibility(
                        visible = !isFirstPage,
                        enter = fadeIn() + slideInHorizontally(
                            initialOffsetX = { -40 },
                            animationSpec = tween(200)
                        ),
                        exit = fadeOut() + slideOutHorizontally(
                            targetOffsetX = { -40 },
                            animationSpec = tween(200)
                        )
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (welcomeState.currentPage > 0) {
                                    welcomeState = welcomeState.copy(
                                        currentPage = welcomeState.currentPage - 1
                                    )
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.welcome_step_previous),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.welcome_step_previous))
                        }
                    }

                    if (isFirstPage) {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    val isLastPage = welcomeState.currentPage == welcomeState.totalPages - 1
                    val canProceed = when (welcomeState.currentPage) {
                        1 -> welcomeState.permissionsGranted
                        else -> true
                    }

                    FilledTonalButton(
                        onClick = {
                            if (isLastPage) {
                                saveWelcomeCompleted(context)
                                onComplete()
                            } else {
                                welcomeState = welcomeState.copy(
                                    currentPage = welcomeState.currentPage + 1
                                )
                            }
                        },
                        enabled = canProceed,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.height(48.dp)
                    ) {
                        if (isLastPage) {
                            Text(stringResource(R.string.welcome_start))
                        } else {
                            Text(stringResource(R.string.welcome_step_next))
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.welcome_step_next),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomePage1() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            FeatureItemSimple(
                icon = Icons.Filled.Edit,
                title = stringResource(R.string.welcome_feature_editor_title),
                description = stringResource(R.string.welcome_feature_editor_desc)
            )

            FeatureItemSimple(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.welcome_feature_run_title),
                description = stringResource(R.string.welcome_feature_run_desc)
            )

            FeatureItemSimple(
                icon = Icons.Filled.Folder,
                title = stringResource(R.string.welcome_feature_project_title),
                description = stringResource(R.string.welcome_feature_project_desc)
            )
        }
    }
}

@Composable
fun PermissionsPage(
    permissionsList: List<PermissionItem>,
    onRequestPermission: (String, Boolean) -> Unit,
    allGranted: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.welcome_permissions_title),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.welcome_permissions_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    stringResource(R.string.welcome_permissions_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp
                )

                Text(
                    if (allGranted) stringResource(R.string.welcome_permissions_all_granted)
                    else stringResource(R.string.welcome_permissions_request),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (allGranted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (allGranted) FontWeight.Bold else FontWeight.Normal
                )
            }

            val userOperatedPermissions = permissionsList.filter {
                it.permission != Manifest.permission.INTERNET
            }

            if (userOperatedPermissions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    userOperatedPermissions.forEach { permission ->
                        PermissionCardItem(
                            permissionItem = permission,
                            onPermissionClick = {
                                if (!permission.granted) {
                                    onRequestPermission(
                                        permission.permission,
                                        permission.isSpecialPermission
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (!allGranted) {
                        PermissionInfoCard()
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureItemSimple(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun PermissionCardItem(
    permissionItem: PermissionItem,
    onPermissionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (permissionItem.granted) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        onClick = {
            if (!permissionItem.granted) {
                onPermissionClick()
            }
        },
        enabled = !permissionItem.granted
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        permissionItem.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (permissionItem.isSpecialPermission) {
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                stringResource(R.string.welcome_permission_special_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                if (permissionItem.granted) {
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                stringResource(R.string.welcome_permission_granted_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                stringResource(R.string.welcome_permission_tap_to_grant),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            Text(
                permissionItem.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Filled.Android,
                    contentDescription = "Android版本",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    stringResource(R.string.welcome_android_version, permissionItem.requiredForVersion),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun PermissionInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    stringResource(R.string.welcome_permission_info_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                stringResource(R.string.welcome_permission_info_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun AnimatedPageIndicator(
    currentPage: Int,
    totalPages: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalPages) { index ->
            val isActive = index == currentPage

            val color by animateColorAsState(
                targetValue = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                },
                animationSpec = tween(durationMillis = 200),
                label = "indicator_color"
            )

            val width by animateDpAsState(
                targetValue = if (isActive) 24.dp else 8.dp,
                animationSpec = tween(durationMillis = 200),
                label = "indicator_width"
            )

            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(MaterialTheme.shapes.small)
                    .background(color)
            )
        }
    }
}

// 修改为非 @Composable 函数，接收 Context 参数
private fun getRequiredPermissionsForVersion(context: Context): List<PermissionItem> {
    val permissions = mutableListOf<PermissionItem>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        permissions.add(
            PermissionItem(
                name = context.getString(R.string.welcome_permission_storage_name_all),
                permission = Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                granted = false,
                requiredForVersion = "11",
                description = context.getString(R.string.welcome_permission_storage_desc_all),
                isSpecialPermission = true
            )
        )
    } else {
        permissions.add(
            PermissionItem(
                name = context.getString(R.string.welcome_permission_storage_name_legacy),
                permission = "ANDROID_10_STORAGE_PERMISSIONS",
                granted = false,
                requiredForVersion = "10",
                description = context.getString(R.string.welcome_permission_storage_desc_legacy)
            )
        )
    }

    permissions.add(
        PermissionItem(
            name = context.getString(R.string.welcome_permission_network_name),
            permission = Manifest.permission.INTERNET,
            granted = true,
            requiredForVersion = "1",
            description = context.getString(R.string.welcome_permission_network_desc)
        )
    )

    return permissions
}