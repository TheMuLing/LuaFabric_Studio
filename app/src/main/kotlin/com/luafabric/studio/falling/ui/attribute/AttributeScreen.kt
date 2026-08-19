package com.luafabric.studio.falling.ui.attribute

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
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
import com.luafabric.studio.falling.ui.components.SwitchBar
import com.luafabric.studio.falling.ui.project.CompactUtilCard
import com.luafabric.studio.falling.ui.project.GlobalUtilItem
import com.luafabric.studio.falling.ui.project.globalUtilsOptions
import muling.views.tool.utils.JsonUtil
import muling.views.tool.utils.LogCatcher
import muling.views.tool.utils.NonBlockingToastState
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
 * 从 settings.json 读取已选权限，返回短名称集合（兼容新旧格式）
 */
private fun getSelectedPermissionsFromSettings(projectPath: String): Set<String> {
    return try {
        val file = File(projectPath, "settings.json")
        if (file.exists()) {
            val json = JsonUtil.parseObject(file.readText())
            val perms =
                (json["user_permission"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            perms.map { perm ->
                // 如果包含点，取最后一段；否则原样返回
                if (perm.contains('.')) perm.substringAfterLast('.') else perm
            }.toSet()
        } else emptySet()
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

    var label by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var versionName by remember { mutableStateOf("") }
    var versionCode by remember { mutableStateOf("") }
    var minSdkVersion by remember { mutableStateOf(29) }
    var targetSdkVersion by remember { mutableStateOf(29) }
    var debugMode by remember { mutableStateOf(false) }

    val allPermissions = remember { mutableStateListOf<PermissionItem>() }

    val selectedGlobalUtils = remember { mutableStateMapOf<String, Boolean>() }

    var iconUri by remember { mutableStateOf<Uri?>(null) }
    val iconFile = File(projectPath, "icon.png")
    val hasExistingIcon = iconFile.exists() && iconFile.isFile

    var minSdkMenuExpanded by remember { mutableStateOf(false) }
    var targetSdkMenuExpanded by remember { mutableStateOf(false) }

    var showPermissionSheet by remember { mutableStateOf(false) }
    var permissionSearchQuery by remember { mutableStateOf("") }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { iconUri = it }
        }
    )

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
                    debugMode =
                        ((jsonMap["application"] as? Map<*, *>)?.get("debugmode") as? Boolean)
                            ?: false

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
            }
        }.also { isLoading = false }

        loadAppPermissions(context, allPermissions)

        val selectedSet = getSelectedPermissionsFromSettings(projectPath)
        allPermissions.forEach { perm ->
            perm.isChecked = selectedSet.contains(perm.shortName)
        }
    }

    fun saveSettings() {
        if (isSaving) return
        isSaving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
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
                    jsonMap["application"] = application

                    jsonMap["package"] = packageName
                    jsonMap["versionName"] = versionName
                    jsonMap["versionCode"] = versionCode

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

                    iconUri?.let { uri ->
                        val outputFile = File(projectPath, "icon.png")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(outputFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
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
                                    pickImageLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val imageModel = iconUri ?: if (hasExistingIcon) iconFile else null
                            if (imageModel != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imageModel)
                                        .crossfade(true)
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
                    }
                }

                // 基本信息卡片
                SettingsCard(title = stringResource(R.string.new_project_basic_info_title), icon = Icons.Filled.Info) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(R.string.attribute_app_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text(stringResource(R.string.attribute_package_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = versionName,
                        onValueChange = { versionName = it },
                        label = { Text(stringResource(R.string.attribute_version_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = versionCode,
                        onValueChange = { versionCode = it },
                        label = { Text(stringResource(R.string.attribute_version_code)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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

                // 权限卡片
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

                // 全局工具卡片
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