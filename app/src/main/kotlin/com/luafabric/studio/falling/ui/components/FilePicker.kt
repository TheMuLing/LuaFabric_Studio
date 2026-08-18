package com.luafabric.studio.falling.ui.components

import android.content.Context
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Css
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Javascript
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luafabric.studio.falling.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 选择模式
enum class SelectionMode {
    FILE,       // 选择文件
    DIRECTORY   // 选择目录
}

// 文件类型图标映射
val fileTypeIcons = mapOf(
    ".jpg" to Icons.Filled.Image,
    ".jpeg" to Icons.Filled.Image,
    ".png" to Icons.Filled.Image,
    ".gif" to Icons.Filled.Image,
    ".bmp" to Icons.Filled.Image,
    ".mp4" to Icons.Filled.Movie,
    ".avi" to Icons.Filled.Movie,
    ".mkv" to Icons.Filled.Movie,
    ".mp3" to Icons.Filled.MusicNote,
    ".wav" to Icons.Filled.MusicNote,
    ".flac" to Icons.Filled.MusicNote,
    ".pdf" to Icons.Filled.PictureAsPdf,
    ".doc" to Icons.Filled.Description,
    ".docx" to Icons.Filled.Description,
    ".txt" to Icons.Filled.TextFields,
    ".xml" to Icons.Filled.Code,
    ".json" to Icons.Filled.Code,
    ".java" to Icons.Filled.Code,
    ".kt" to Icons.Filled.Code,
    ".html" to Icons.Filled.Html,
    ".css" to Icons.Filled.Css,
    ".js" to Icons.Filled.Javascript,
    ".zip" to Icons.Filled.FolderZip,
    ".rar" to Icons.Filled.FolderZip,
    ".7z" to Icons.Filled.FolderZip
)

// 路径面包屑数据类
data class PathSegment(
    val name: String,
    val path: String,
    val isClickable: Boolean = true
)

// 文件选择器专用数据类
data class PickerFileItem(
    val fileName: String,
    val filePath: String,
    val isDirectory: Boolean,
    val fileSize: Long = 0L,
    val modifiedDate: Long = 0L,
    val isHidden: Boolean = false
)

/**
 * 文件选择器对话框 - 简化版本
 */
@Composable
fun FilePickerDialog(
    initialPath: String = Environment.getExternalStorageDirectory().absolutePath,
    selectionMode: SelectionMode = SelectionMode.FILE,
    title: String = when (selectionMode) {
        SelectionMode.FILE -> stringResource(R.string.file_picker_select_file)
        SelectionMode.DIRECTORY -> stringResource(R.string.file_picker_select_directory)
    },
    allowedExtensions: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onFileSelected: (String) -> Unit = {},
    onDirectorySelected: (String) -> Unit = {}
) {
    val context = LocalContext.current
    // 当前路径状态
    var currentPath by remember { mutableStateOf(initialPath) }

    // 文件和目录列表状态
    var fileItems by remember { mutableStateOf<List<PickerFileItem>>(emptyList()) }

    // 选中项状态（用于文件选择）
    var selectedItem by remember { mutableStateOf<PickerFileItem?>(null) }

    // 列表状态
    val listState = rememberLazyListState()

    // 面包屑滚动状态
    val breadcrumbScrollState = rememberScrollState()

    // 加载文件和目录
    LaunchedEffect(currentPath) {
        loadFilesAndDirectories(
            path = currentPath,
            allowedExtensions = allowedExtensions,
            onItemsLoaded = { items ->
                fileItems = items
            }
        )
    }

    // 生成路径面包屑
    val pathSegments = remember(currentPath) {
        generatePathSegments(currentPath, context)
    }

    // 滚动面包屑到最后
    LaunchedEffect(pathSegments.size) {
        if (breadcrumbScrollState.maxValue > 0) {
            breadcrumbScrollState.animateScrollTo(breadcrumbScrollState.maxValue)
        }
    }

    // 计算是否可以返回上一级
    val canGoBack = currentPath != "/" &&
            currentPath != Environment.getExternalStorageDirectory().absolutePath

    // 对话框
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .shadow(0.dp, MaterialTheme.shapes.extraLarge),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 标题
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // 关闭按钮
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 分隔线
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                )

                // 路径面包屑
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 面包屑分隔符
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = stringResource(R.string.cd_project_folder),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )

                        // 路径面包屑
                        HorizontalPathBreadcrumbs(
                            segments = pathSegments,
                            onSegmentClick = { segment ->
                                if (segment.isClickable) {
                                    currentPath = segment.path
                                    selectedItem = null
                                }
                            },
                            scrollState = breadcrumbScrollState
                        )
                    }
                }

                // 文件列表区域（带垂直滚动条）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // 文件列表
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 显示返回上一级项
                        if (canGoBack) {
                            item {
                                FileItemCard(
                                    item = PickerFileItem(
                                        fileName = "..",
                                        filePath = File(currentPath).parent ?: "/",
                                        isDirectory = true,
                                        modifiedDate = 0L
                                    ),
                                    isSelected = false,
                                    onClick = {
                                        val parent = File(currentPath).parent
                                        if (parent != null) {
                                            currentPath = parent
                                            selectedItem = null
                                        }
                                    },
                                    selectionMode = selectionMode,
                                    context = context
                                )
                            }
                        }

                        // 文件列表项
                        items(fileItems) { item ->
                            FileItemCard(
                                item = item,
                                isSelected = selectedItem?.filePath == item.filePath,
                                onClick = {
                                    if (item.isDirectory) {
                                        currentPath = item.filePath
                                        selectedItem = null
                                    } else {
                                        if (selectionMode == SelectionMode.FILE) {
                                            selectedItem =
                                                if (selectedItem?.filePath == item.filePath)
                                                    null else item
                                        }
                                    }
                                },
                                selectionMode = selectionMode,
                                context = context
                            )
                        }
                    }
                }

                // 底部操作栏
                AnimatedVisibility(
                    visible = (selectionMode == SelectionMode.FILE && selectedItem != null) ||
                            (selectionMode == SelectionMode.DIRECTORY),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 当前选中项信息
                        if (selectionMode == SelectionMode.FILE && selectedItem != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                                        alpha = 0.12f
                                    )
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            getFileIcon(selectedItem!!.fileName),
                                            contentDescription = stringResource(R.string.file_picker_file_type),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.file_picker_clear_selection),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = selectedItem!!.fileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!selectedItem!!.isDirectory) {
                                            Text(
                                                text = "${formatFileSize(selectedItem!!.fileSize, context)} • ${
                                                    formatDate(
                                                        selectedItem!!.modifiedDate
                                                    )
                                                }",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { selectedItem = null },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Clear,
                                            contentDescription = stringResource(R.string.file_picker_clear_selection),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 操作按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 取消按钮
                            OutlinedButton(
                                onClick = {
                                    if (selectionMode == SelectionMode.FILE) {
                                        selectedItem = null
                                    }
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 确定按钮
                            Button(
                                onClick = {
                                    when (selectionMode) {
                                        SelectionMode.FILE -> {
                                            selectedItem?.let {
                                                onFileSelected(it.filePath)
                                            }
                                        }

                                        SelectionMode.DIRECTORY -> {
                                            onDirectorySelected(currentPath)
                                        }
                                    }
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .weight(1.5f),
                                shape = MaterialTheme.shapes.medium,
                                enabled = (selectionMode == SelectionMode.FILE && selectedItem != null) ||
                                        (selectionMode == SelectionMode.DIRECTORY)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (selectionMode == SelectionMode.FILE)
                                            Icons.Filled.CheckCircle
                                        else
                                            Icons.Filled.FolderOpen,
                                        contentDescription = when (selectionMode) {
                                            SelectionMode.FILE -> stringResource(R.string.file_picker_select_file)
                                            SelectionMode.DIRECTORY -> stringResource(R.string.file_picker_select_this_directory)
                                        },
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = when (selectionMode) {
                                            SelectionMode.FILE -> stringResource(R.string.file_picker_select_file)
                                            SelectionMode.DIRECTORY -> stringResource(R.string.file_picker_select_this_directory)
                                        },
                                        style = MaterialTheme.typography.labelLarge,
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
}

// 水平路径面包屑组件
@Composable
fun HorizontalPathBreadcrumbs(
    segments: List<PathSegment>,
    onSegmentClick: (PathSegment) -> Unit,
    scrollState: ScrollState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        segments.forEachIndexed { index, segment ->
            // 面包屑项（使用Card包装）
            Card(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        enabled = segment.isClickable,
                        onClick = { onSegmentClick(segment) }
                    ),
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (index == segments.size - 1) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = segment.name,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (index == segments.size - 1) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (!segment.isClickable)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else if (index == segments.size - 1)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            // 分隔符
            if (index < segments.size - 1) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// 生成路径面包屑
private fun generatePathSegments(currentPath: String, context: Context): List<PathSegment> {
    val segments = mutableListOf<PathSegment>()

    // 根路径
    if (currentPath == "/") {
        segments.add(PathSegment(context.getString(R.string.file_picker_root), "/", isClickable = false))
        return segments
    }

    // 外部存储根目录
    val externalStorage = Environment.getExternalStorageDirectory().absolutePath
    if (currentPath == externalStorage) {
        segments.add(PathSegment(context.getString(R.string.file_picker_primary_storage), externalStorage, isClickable = false))
        return segments
    }

    // 构建路径段
    val pathParts = currentPath.split(File.separatorChar).filter { it.isNotEmpty() }

    // 如果是外部存储的子目录
    if (currentPath.startsWith(externalStorage)) {
        val relativePath = currentPath.substring(externalStorage.length)
        val relativeParts = relativePath.split(File.separatorChar).filter { it.isNotEmpty() }

        segments.add(PathSegment(context.getString(R.string.file_picker_primary_storage), externalStorage))

        var accumulatedPath = externalStorage
        for (part in relativeParts) {
            accumulatedPath += File.separator + part
            segments.add(
                PathSegment(
                    name = part,
                    path = accumulatedPath,
                    isClickable = segments.size <= relativeParts.size // 最后一项可点击
                )
            )
        }
    } else {
        // 其他路径
        var accumulatedPath = ""
        for (part in pathParts) {
            if (accumulatedPath.isEmpty()) {
                accumulatedPath = File.separator + part
            } else {
                accumulatedPath += File.separator + part
            }

            segments.add(
                PathSegment(
                    name = part,
                    path = accumulatedPath,
                    isClickable = segments.size <= pathParts.size // 最后一项可点击
                )
            )
        }
    }

    return segments
}

// 文件项卡片
@Composable
fun FileItemCard(
    item: PickerFileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    selectionMode: SelectionMode,
    context: Context,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 1.dp
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (item.isDirectory)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected && selectionMode == SelectionMode.FILE && !item.isDirectory) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.file_picker_select_file),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Icon(
                        if (item.isDirectory)
                            if (item.fileName == "..") Icons.AutoMirrored.Filled.ArrowBack
                            else Icons.Outlined.Folder
                        else
                            getFileIcon(item.fileName),
                        contentDescription = if (item.isDirectory) stringResource(R.string.cd_project_folder) else stringResource(R.string.file_picker_file_type),
                        tint = if (item.isDirectory)
                            if (item.fileName == "..")
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 文件信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (item.fileName == "..") FontWeight.SemiBold else FontWeight.Medium,
                        color = if (item.fileName == "..")
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (item.isHidden && item.fileName != "..") {
                        Badge(
                            modifier = Modifier.size(20.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = stringResource(R.string.file_picker_hidden),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!item.isDirectory && item.fileName != "..") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = formatFileSize(item.fileSize, context),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                        )
                        Text(
                            text = formatDate(item.modifiedDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 如果是目录，显示进入箭头
            if (item.isDirectory && item.fileName != "..") {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.open_drawer),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// 获取文件图标
fun getFileIcon(fileName: String): androidx.compose.ui.graphics.vector.ImageVector {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return fileTypeIcons[".$extension"] ?: Icons.Outlined.Description
}

// 加载文件和目录
fun loadFilesAndDirectories(
    path: String,
    allowedExtensions: List<String>,
    onItemsLoaded: (List<PickerFileItem>) -> Unit
) {
    try {
        val directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            onItemsLoaded(emptyList())
            return
        }

        val files = directory.listFiles() ?: emptyArray()
        val items = mutableListOf<PickerFileItem>()

        for (file in files) {
            // 过滤隐藏文件
            if (file.name.startsWith(".")) continue

            // 扩展名过滤
            if (!file.isDirectory && allowedExtensions.isNotEmpty()) {
                val extension = file.name.substringAfterLast('.', "").lowercase()
                if (extension !in allowedExtensions.map { it.lowercase() }) {
                    continue
                }
            }

            items.add(
                PickerFileItem(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    isDirectory = file.isDirectory,
                    fileSize = if (file.isFile) file.length() else 0L,
                    modifiedDate = file.lastModified(),
                    isHidden = file.isHidden
                )
            )
        }

        // 排序：目录在前，按名称升序
        items.sortWith(Comparator { item1, item2 ->
            if (item1.isDirectory != item2.isDirectory) {
                return@Comparator if (item1.isDirectory) -1 else 1
            }
            item1.fileName.compareTo(item2.fileName, ignoreCase = true)
        })

        onItemsLoaded(items)
    } catch (e: Exception) {
        e.printStackTrace()
        onItemsLoaded(emptyList())
    }
}

// 文件大小格式化
private fun formatFileSize(size: Long, context: Context): String {
    return when {
        size < 1024 -> "$size ${context.getString(R.string.file_size_unit_b)}"
        size < 1024 * 1024 -> "${String.format("%.1f", size / 1024.0)} ${context.getString(R.string.file_size_unit_kb)}"
        size < 1024 * 1024 * 1024 -> "${String.format("%.1f", size / (1024.0 * 1024.0))} ${context.getString(R.string.file_size_unit_mb)}"
        else -> "${String.format("%.1f", size / (1024.0 * 1024.0 * 1024.0))} ${context.getString(R.string.file_size_unit_gb)}"
    }
}

// 日期格式化
private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(date)
}