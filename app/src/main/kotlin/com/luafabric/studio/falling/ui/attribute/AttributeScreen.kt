package com.luafabric.studio.falling.ui.attribute

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.luafabric.studio.falling.ui.icons.AndroidStudioIcon
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.components.FilePickerDialog
import com.luafabric.studio.falling.ui.components.SelectionMode
import com.luafabric.studio.falling.ui.components.SwitchBar
import com.luafabric.studio.falling.ui.project.CompactUtilCard
import com.luafabric.studio.falling.ui.project.GlobalUtilItem
import com.luafabric.studio.falling.ui.project.globalUtilsOptions
import muling.views.tool.utils.AppInfoUtil
import muling.views.tool.utils.ComposeConfig
import muling.views.tool.utils.JsonUtil
import muling.views.tool.utils.LogCatcher
import muling.views.tool.utils.NonBlockingToastState
import muling.views.tool.utils.ProjectUtil
import muling.views.tool.utils.TransitionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// 权限项数据类（使用 mutableStateOf 以便立即触发重组）
class PermissionItem(
    val name: String,          // 完整权限名，如 "android.permission.READ_EXTERNAL_STORAGE"
    val label: String,         // 显示名称（可能为中文）
    isCheckedInitial: Boolean = false
) {
    var isChecked by mutableStateOf(isCheckedInitial)

    // 获取短名称（用于保存和比较）
    val shortName: String get() = name.substringAfterLast('.')
}

// SDK 版本信息映射（使用资源 ID）
private val sdkDisplayMap = mapOf(
    21 to R.string.sdk_21,
    22 to R.string.sdk_22,
    23 to R.string.sdk_23,
    24 to R.string.sdk_24,
    25 to R.string.sdk_25,
    26 to R.string.sdk_26,
    27 to R.string.sdk_27,
    28 to R.string.sdk_28,
    29 to R.string.sdk_29,
    30 to R.string.sdk_30,
    31 to R.string.sdk_31,
    32 to R.string.sdk_32,
    33 to R.string.sdk_33,
    34 to R.string.sdk_34,
    35 to R.string.sdk_35,
    36 to R.string.sdk_36
)

/**
 * 从项目配置读取已选权限，返回短名称集合（兼容新旧格式；view 读 settings.json，compose 读 b85）
 */
private fun getSelectedPermissionsFromSettings(projectPath: String): Set<String> {
    return try {
        val projectDir = File(projectPath)
        val perms: List<String> = if (ProjectUtil.isComposeProject(projectDir)) {
            val cfg = ProjectUtil.loadProjectConfig(projectDir)
            (cfg?.get("user_permission") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        } else {
            val file = File(projectPath, "settings.json")
            if (!file.exists()) emptyList()
            else {
                val json = JsonUtil.parseObject(file.readText())
                (json["user_permission"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            }
        }
        perms.map { perm ->
            // 如果包含点，取最后一段；否则原样返回
            if (perm.contains('.')) perm.substringAfterLast('.') else perm
        }.toSet()
    } catch (e: Exception) {
        emptySet()
    }
}

/**
 * 加载应用声明的权限及其标签
 */
private suspend fun loadAppPermissions(context: Context, list: SnapshotStateList<PermissionItem>) {
    withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val packageName = context.packageName
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)

            val requestedPermissions = packageInfo.requestedPermissions ?: return@withContext
            val permissionItems = mutableListOf<PermissionItem>()

            for (permName in requestedPermissions) {
                // 排除不需要的权限
                if (permName.contains("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")) continue

                val label = try {
                    val permInfo = pm.getPermissionInfo(permName, 0)
                    permInfo.loadLabel(pm).toString()
                } catch (e: Exception) {
                    permName.substringAfterLast('.')
                }
                permissionItems.add(PermissionItem(permName, label))
            }

            permissionItems.sortBy { it.label }

            withContext(Dispatchers.Main) {
                list.clear()
                list.addAll(permissionItems)
            }
        } catch (e: Exception) {
            LogCatcher.e("AttributeScreen", "加载权限失败", e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AttributeScreen(
    projectPath: String,
    onBack: () -> Unit,
    onSaveComplete: () -> Unit,
    toast: NonBlockingToastState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scrollState = rememberScrollState()
    var showExtendedFab by remember { mutableStateOf(true) }

    LaunchedEffect(scrollState.value) {
        showExtendedFab = scrollState.value <= 50
    }

    val sdkVersions = sdkDisplayMap.keys.sorted()

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Compose 项目（build.gradle.b85）无 settings.json：权限/全局工具卡片隐藏，仅 b85 可表达字段可编辑
    val isCompose = ProjectUtil.isComposeProject(File(projectPath))

    var label by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var versionName by remember { mutableStateOf("") }
    var versionCode by remember { mutableStateOf("") }
    var minSdkVersion by remember { mutableStateOf(29) }
    var targetSdkVersion by remember { mutableStateOf(29) }
    var debugMode by remember { mutableStateOf(false) }
    var encryptEnabled by remember { mutableStateOf(true) }
    var mergeDexEnabled by remember { mutableStateOf(true) }
    var entryFile by remember { mutableStateOf("main.lua") }
    var showEntryPicker by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showGalleryFilePicker by remember { mutableStateOf(false) }
    var iconRefreshTick by remember { mutableStateOf(0) }

    val allPermissions = remember { mutableStateListOf<PermissionItem>() }

    val selectedGlobalUtils = remember { mutableStateMapOf<String, Boolean>() }

    var iconPath by remember { mutableStateOf("icon.png") } // 相对项目根；图标路径框的值
    val iconFile = File(projectPath, iconPath)
    val hasExistingIcon = iconFile.exists() && iconFile.isFile

    var minSdkMenuExpanded by remember { mutableStateOf(false) }
    var targetSdkMenuExpanded by remember { mutableStateOf(false) }

    var showPermissionSheet by remember { mutableStateOf(false) }
    var permissionSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val settingsFile = File(projectPath, "settings.json")
            if (settingsFile.exists()) {
                try {
                    val jsonString = settingsFile.readText()
                    val jsonMap = JsonUtil.parseObject(jsonString)

                    label = (jsonMap["application"] as? Map<*, *>)?.get("label") as? String ?: File(
                        projectPath
                    ).name
                    packageName = jsonMap["package"] as? String ?: ""
                    versionName = jsonMap["versionName"] as? String ?: "1.0"
                    versionCode = jsonMap["versionCode"] as? String ?: "1"
                    entryFile = jsonMap["entryFile"] as? String ?: "main.lua"
                    // 打开自检：入口/图标路径越界或绝对路径 → 恢复默认并落盘，防止脏值持续生效
                    val rawEntry = jsonMap["entryFile"] as? String ?: "main.lua"
                    entryFile =
                        if (!rawEntry.startsWith("/") && !rawEntry.contains("..")) rawEntry else "main.lua"
                    val rawIcon = (jsonMap["iconPath"] as? String ?: "icon.png").trim()
                    iconPath = if (rawIcon.isEmpty() || rawIcon.startsWith("/") || rawIcon.contains("..")) {
                        "icon.png"
                    } else {
                        rawIcon
                    }
                    if (entryFile != rawEntry || iconPath != rawIcon.ifEmpty { "icon.png" }) {
                        val safeMap =
                            (jsonMap as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
                        safeMap["entryFile"] = entryFile
                        safeMap["iconPath"] = iconPath
                        try {
                            settingsFile.writeText(JsonUtil.toFormattedString(safeMap, 4))
                            LogCatcher.i("AttributeScreen", "入口/图标路径越界，已恢复默认")
                        } catch (e: Exception) {
                            LogCatcher.e("AttributeScreen", "回写修复后的路径失败", e)
                        }
                    }
                    debugMode =
                        ((jsonMap["application"] as? Map<*, *>)?.get("debugmode") as? Boolean)
                            ?: false
                    encryptEnabled =
                        ((jsonMap["application"] as? Map<*, *>)?.get("encrypt") as? Boolean)
                            ?: true
                    mergeDexEnabled =
                        ((jsonMap["application"] as? Map<*, *>)?.get("mergeDex") as? Boolean)
                            ?: true

                    val usesSdk = jsonMap["uses_sdk"] as? Map<*, *>
                    minSdkVersion = (usesSdk?.get("minSdkVersion") as? String)?.toIntOrNull() ?: 29
                    targetSdkVersion =
                        (usesSdk?.get("targetSdkVersion") as? String)?.toIntOrNull() ?: 29

                    val globalUtils = jsonMap["global_utils"] as? List<*> ?: emptyList<Any>()
                    val globalUtilsSet = globalUtils.mapNotNull { it as? String }.toSet()
                    selectedGlobalUtils.clear()
                    globalUtilsSet.forEach { util -> selectedGlobalUtils[util] = true }
                } catch (e: Exception) {
                    LogCatcher.e("AttributeScreen", "加载 settings.json 失败", e)
                }
            } else if (ProjectUtil.isComposeProject(File(projectPath))) {
                // Compose 项目：配置在 build.gradle.b85（无 settings.json），仅读写 b85 可表达字段
                val cfg = ProjectUtil.loadProjectConfig(File(projectPath))
                if (cfg != null) {
                    label = cfg["name"] as? String ?: File(projectPath).name
                    packageName = cfg["packageId"] as? String ?: ""
                    versionName = cfg["versionName"] as? String ?: "1.0"
                    versionCode = cfg["versionCode"]?.toString() ?: "1"
                    val rawEntry = cfg["entry"] as? String ?: "main.lua"
                    entryFile =
                        if (!rawEntry.startsWith("/") && !rawEntry.contains("..")) rawEntry else "main.lua"
                    iconPath = ProjectUtil.projectIconPath(File(projectPath))
                    minSdkVersion = (cfg["minSdk"] as? Number)?.toInt() ?: 29
                    targetSdkVersion = (cfg["targetSdk"] as? Number)?.toInt() ?: 29
                    debugMode = ComposeConfig.debugFlag(
                        File(projectPath, ComposeConfig.FILE_NAME).readBytes()
                    ) ?: false
                    encryptEnabled = (cfg["encrypt"] as? Boolean) ?: true
                    mergeDexEnabled = (cfg["mergeDex"] as? Boolean) ?: true
                }
            }
        }.also { isLoading = false }

        loadAppPermissions(context, allPermissions)

        val selectedSet = withContext(Dispatchers.IO) {
            getSelectedPermissionsFromSettings(projectPath)
        }
        allPermissions.forEach { perm ->
            perm.isChecked = selectedSet.contains(perm.shortName)
        }
    }

    // 仅落盘，无 UI 行为；供 FAB 保存与相册图标即时同步共用
    suspend fun persistSettings() {
        withContext(Dispatchers.IO) {
            if (ProjectUtil.isComposeProject(File(projectPath))) {
                // Compose 项目：写回 b85（可表达字段）；版本号转 Long 供编码器（int32 兼容），无效回落 1
                ProjectUtil.updateComposeConfigFile(
                    File(projectPath),
                    linkedMapOf(
                        "name" to label,
                        "packageId" to packageName,
                        "versionName" to versionName,
                        "versionCode" to (versionCode.toLongOrNull() ?: 1L),
                        "minSdk" to minSdkVersion.toLong(),
                        "targetSdk" to targetSdkVersion.toLong(),
                        "entry" to entryFile,
                        "icon" to iconPath,
                        "encrypt" to encryptEnabled,
                        "mergeDex" to mergeDexEnabled,
                        "user_permission" to allPermissions.filter { it.isChecked }.map { it.shortName }
                    ),
                    debugMode
                )
                return@withContext
            }
            val settingsFile = File(projectPath, "settings.json")
            val jsonMap: MutableMap<String, Any?> = if (settingsFile.exists()) {
                val parsed = JsonUtil.parseObject(settingsFile.readText())
                (parsed as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
            } else {
                mutableMapOf()
            }

            val application = (jsonMap["application"] as? Map<String, Any?>)?.toMutableMap()
                ?: mutableMapOf<String, Any?>()
            application["label"] = label
            application["debugmode"] = debugMode
            application["encrypt"] = encryptEnabled
            application["mergeDex"] = mergeDexEnabled
            jsonMap["application"] = application

            jsonMap["package"] = packageName
            jsonMap["versionName"] = versionName
            jsonMap["versionCode"] = versionCode
            jsonMap["entryFile"] = entryFile
            jsonMap["iconPath"] = iconPath

            val usesSdk = (jsonMap["uses_sdk"] as? Map<String, Any?>)?.toMutableMap()
                ?: mutableMapOf<String, Any?>()
            usesSdk["minSdkVersion"] = minSdkVersion.toString()
            usesSdk["targetSdkVersion"] = targetSdkVersion.toString()
            jsonMap["uses_sdk"] = usesSdk

            // 保存权限（只保存短名称）
            val checkedPermissions =
                allPermissions.filter { it.isChecked }.map { it.shortName }
            jsonMap["user_permission"] = checkedPermissions

            val checkedUtils = selectedGlobalUtils.filterValues { it }.keys.toList()
            jsonMap["global_utils"] = checkedUtils

            val updatedJson = JsonUtil.toFormattedString(jsonMap, 4)
            settingsFile.writeText(updatedJson)
        }
    }

    fun saveSettings() {
        if (isSaving) return
        isSaving = true
        scope.launch {
            try {
                persistSettings()
                withContext(Dispatchers.Main) {
                    toast.showToast(context.getString(R.string.attribute_save_success))
                    onSaveComplete()
                    onBack()
                }
            } catch (e: Exception) {
                LogCatcher.e("AttributeScreen", "保存失败", e)
                toast.showToast(context.getString(R.string.attribute_save_failed, e.message))
            } finally {
                isSaving = false
            }
        }
    }

    // 已移除系统相册(PhotoPicker)：大图点击改走内置文件选择器，见下方 FilePickerDialog
    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.code_editor_project_property)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { saveSettings() },
                icon = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save))
                    }
                },
                text = {
                    AnimatedVisibility(
                        visible = showExtendedFab,
                        enter = TransitionUtil.createFABTransition(),
                        exit = TransitionUtil.createFABExitTransition()
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                expanded = showExtendedFab
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 图标卡片（带提示文字）
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
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.cd_project_icon),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .clickable {
                                    showGalleryFilePicker = true
                                }
                                .border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val imageModel = if (hasExistingIcon) iconFile else null
                            if (imageModel != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imageModel)
                                        .crossfade(true)
                                        .size(256) // 缩小采样，避免超大图标解码致内存压力（卡顿/点击失效）
                                        // iconPath + tick 双参与缓存键：路径变更（换图）与同路径覆盖都强制刷新预览，
                                        // 避免重开页面沿用旧字节（固定 key 会命中上次会话缓存）
                                        .memoryCacheKey("project_icon_${iconPath}_${iconRefreshTick}")
                                        .diskCacheKey("project_icon_${iconPath}_${iconRefreshTick}")
                                        .build(),
                                    contentDescription = stringResource(R.string.cd_project_icon),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Image,
                                    contentDescription = stringResource(R.string.cd_project_icon),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.attribute_icon_change_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        // 图标路径（只读，编辑按钮仅改路径引用；更换图标才复制到根目录）
                        OutlinedTextField(
                            value = iconPath,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.attribute_icon_path)) },
                            shape = MaterialTheme.shapes.small,
                            trailingIcon = {
                                IconButton(onClick = { showIconPicker = true }) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            singleLine = true
                        )
                    }
                }

                // 基本信息卡片
                SettingsCard(title = stringResource(R.string.new_project_basic_info_title), icon = Icons.Filled.Info) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(R.string.attribute_app_name)) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text(stringResource(R.string.attribute_package_name)) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = versionName,
                        onValueChange = { versionName = it },
                        label = { Text(stringResource(R.string.attribute_version_name)) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = versionCode,
                        onValueChange = { versionCode = it },
                        label = { Text(stringResource(R.string.attribute_version_code)) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = entryFile,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.attribute_entry_file)) },
                        shape = MaterialTheme.shapes.small,
                        trailingIcon = {
                            // 独立按钮通道：避免 TextField 内部手势吞掉整框 clickable 导致点击偶发无反应
                            IconButton(onClick = { showEntryPicker = true }) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showEntryPicker = true },
                        singleLine = true
                    )
                }

                // SDK 版本卡片
                SettingsCard(title = stringResource(R.string.attribute_sdk_title), icon = Icons.Filled.Settings) {
                    ExposedDropdownMenuBox(
                        expanded = minSdkMenuExpanded,
                        onExpandedChange = { minSdkMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = stringResource(sdkDisplayMap[minSdkVersion] ?: R.string.sdk_21),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.attribute_min_sdk)) },
                            shape = MaterialTheme.shapes.small,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = minSdkMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = minSdkMenuExpanded,
                            onDismissRequest = { minSdkMenuExpanded = false }
                        ) {
                            sdkVersions.forEach { sdk ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(sdkDisplayMap[sdk]!!)) },
                                    onClick = {
                                        minSdkVersion = sdk
                                        minSdkMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = targetSdkMenuExpanded,
                        onExpandedChange = { targetSdkMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = stringResource(sdkDisplayMap[targetSdkVersion] ?: R.string.sdk_29),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.attribute_target_sdk)) },
                            shape = MaterialTheme.shapes.small,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetSdkMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = targetSdkMenuExpanded,
                            onDismissRequest = { targetSdkMenuExpanded = false }
                        ) {
                            sdkVersions.forEach { sdk ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(sdkDisplayMap[sdk]!!)) },
                                    onClick = {
                                        targetSdkVersion = sdk
                                        targetSdkMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 调试模式卡片
                SettingsCard(title = stringResource(R.string.debug_mode), icon = Icons.Filled.Edit) {
                    SwitchBar(
                        checked = debugMode,
                        onCheckedChange = { debugMode = it },
                        text = stringResource(R.string.attribute_debug_enable),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 构建选项卡片
                SettingsCard(
                    title = stringResource(R.string.attribute_build_options),
                    icon = AndroidStudioIcon
                ) {
                    SwitchBar(
                        checked = encryptEnabled,
                        onCheckedChange = { encryptEnabled = it },
                        text = stringResource(
                            R.string.attribute_encrypt_build,
                            AppInfoUtil.getAppName(context)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SwitchBar(
                        checked = mergeDexEnabled,
                        onCheckedChange = { mergeDexEnabled = it },
                        text = stringResource(R.string.attribute_merge_dex),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 权限卡片（view 读 settings.json，compose 读 b85 user_permission）
                SettingsCard(title = stringResource(R.string.attribute_permission_title), icon = Icons.Filled.Lock) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.attribute_permission_count, allPermissions.count { it.isChecked }),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedIconButton(
                            onClick = { showPermissionSheet = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.attribute_permission_manage))
                        }
                    }
                    if (allPermissions.any { it.isChecked }) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy((-2).dp) // 减小垂直间距
                        ) {
                            allPermissions.filter { it.isChecked }.forEach { perm ->
                                AssistChip(
                                    onClick = { /* 可跳转到权限管理 */ },
                                    label = { Text(perm.label, maxLines = 1) },
                                    leadingIcon = null // 移除图标
                                )
                            }
                        }
                    }
                }

                // 全局工具卡片（Compose 项目 global_utils 恒空 → 隐藏）
                if (!isCompose) {
                SettingsCard(title = stringResource(R.string.attribute_global_utils_title), icon = Icons.Filled.Edit) {
                    Text(
                        text = stringResource(R.string.attribute_global_utils_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        globalUtilsOptions.forEach { util ->
                            val isSelected = selectedGlobalUtils[stringResource(util.nameResId)] == true
                            CompactUtilCard(
                                util = util,
                                selected = isSelected,
                                onSelectedChange = { selected ->
                                    val utilName = context.getString(util.nameResId)
                                    if (selected) {
                                        selectedGlobalUtils[utilName] = true
                                    } else {
                                        selectedGlobalUtils.remove(utilName)
                                    }
                                },
                                modifier = Modifier
                            )
                        }
                    }
                }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showPermissionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPermissionSheet = false }
        ) {
            PermissionSelectionSheet(
                allPermissions = allPermissions,
                searchQuery = permissionSearchQuery,
                onSearchQueryChange = { permissionSearchQuery = it },
                onConfirm = { showPermissionSheet = false }
            )
        }
    }

    // 大图点击 → 内置文件选择器，默认 /sdcard/DCIM，选中后复制到项目根目录（保持原名+净化）
    if (showGalleryFilePicker) {
        FilePickerDialog(
            initialPath = File(Environment.getExternalStorageDirectory(), "DCIM").absolutePath,
            selectionMode = SelectionMode.FILE,
            title = stringResource(R.string.cd_project_icon),
            allowedExtensions = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp"),
            onDismiss = { showGalleryFilePicker = false },
            onFileSelected = { path ->
                showGalleryFilePicker = false
                scope.launch {
                    val copiedName = withContext(Dispatchers.IO) {
                        copyGalleryImageToProject(context, File(path), projectPath)
                    }
                    if (copiedName != null) {
                        iconPath = copiedName
                        iconRefreshTick++ // 换缓存键刷新预览（亦触发重组重算 hasExistingIcon）
                        persistSettings()
                        toast.showToast(context.getString(R.string.attribute_icon_updated))
                    } else {
                        toast.showToast(context.getString(R.string.attribute_icon_copy_failed))
                    }
                }
            }
        )
    }

    if (showEntryPicker) {
        FilePickerDialog(
            initialPath = projectPath,
            selectionMode = SelectionMode.FILE,
            title = stringResource(R.string.attribute_entry_picker_title),
            allowedExtensions = listOf("lua"),
            rootPath = projectPath, // 锁根：禁止导航到项目外
            onDismiss = { showEntryPicker = false },
            onFileSelected = { path ->
                val rel = runCatching {
                    File(path).relativeTo(File(projectPath)).path.replace('\\', '/')
                }.getOrNull()
                // 仅以 relativeTo 结果判定：不同根抛异常->null 拒绝；同根父目录逃逸->rel 含 .. 拒绝
                if (rel != null && !rel.contains("..")) {
                    entryFile = rel
                    showEntryPicker = false
                } else {
                    toast.showToast(context.getString(R.string.attribute_entry_outside_project))
                }
            }
        )
    }

    if (showIconPicker) {
        FilePickerDialog(
            initialPath = projectPath,
            selectionMode = SelectionMode.FILE,
            title = stringResource(R.string.attribute_icon_picker_title),
            allowedExtensions = listOf("png", "jpg", "jpeg", "webp", "gif"),
            rootPath = projectPath, // 锁根：只在项目内选择
            onDismiss = { showIconPicker = false },
            onFileSelected = { path ->
                showIconPicker = false
                val rel = runCatching {
                    File(path).relativeTo(File(projectPath)).path.replace('\\', '/')
                }.getOrNull()
                // 编辑入口：仅修改路径引用、不复制；复制仅由「相册更换图标」（大图点击）完成
                if (rel != null && !rel.contains("..")) {
                    iconPath = rel
                    iconRefreshTick++ // 换缓存键刷新预览（亦触发重组重算 hasExistingIcon）
                    toast.showToast(context.getString(R.string.attribute_icon_updated))
                }
            }
        )
    }
}

// 从本地文件复制图片到项目根目录，保持原文件名与后缀（净化路径分隔符）；失败返回 null
private fun copyGalleryImageToProject(
    context: Context,
    sourceFile: File,
    projectPath: String
): String? {
    return try {
        if (!sourceFile.exists() || !sourceFile.isFile) return null
        val safeName = sourceFile.name
            .takeIf { it.isNotBlank() && !it.contains("..") && !it.contains('\\') }
            ?: "icon.png"
        val outputFile = File(projectPath, safeName)
        sourceFile.inputStream().use { input ->
            FileOutputStream(outputFile).use { output -> input.copyTo(output) }
        }
        safeName
    } catch (e: Exception) {
        LogCatcher.e("AttributeScreen", "复制图标失败", e)
        null
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSelectionSheet(
    allPermissions: SnapshotStateList<PermissionItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val filteredPermissions = allPermissions.filter {
        it.label.contains(searchQuery, ignoreCase = true) || it.name.contains(
            searchQuery,
            ignoreCase = true
        )
    }.sortedWith(compareByDescending<PermissionItem> { it.isChecked }.thenBy { it.label })
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 顶部内容（标题、搜索框、全选按钮）不占权重
        Text(
            text = stringResource(R.string.attribute_permission_select_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.attribute_permission_search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear))
                    }
                }
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { allPermissions.forEach { it.isChecked = true } }) {
                Text(stringResource(R.string.analyse_select_all))
            }
            TextButton(onClick = { allPermissions.forEach { it.isChecked = false } }) {
                Text(stringResource(R.string.analyse_deselect_all))
            }
        }

        // 列表部分使用 weight(1f) 占据剩余空间
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(filteredPermissions, key = { it.name }) { permission ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { permission.isChecked = !permission.isChecked }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = permission.isChecked,
                        onCheckedChange = { permission.isChecked = it }
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = permission.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = permission.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 底部按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onConfirm,
                modifier = Modifier.height(48.dp)
            ) {
                Text(stringResource(R.string.ok))
            }
        }
    }
}