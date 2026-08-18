@file:OptIn(ExperimentalMaterial3Api::class)

package com.luafabric.studio.falling.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.editor.components.CodeEditorView
import com.luafabric.studio.falling.ui.editor.viewmodel.EditorViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTabView(
    viewModel: EditorViewModel,
    lastFileToOpen: String?,
    onTabBarRendered: () -> Unit,
    panelState: DraggablePanelState,
    onOpenFileTree: () -> Unit = {},
    modifier: Modifier = Modifier,
    // 新增滑动手势回调参数
    onSwipe: ((SwipeDirection) -> Unit)? = null
) {
    val openFiles = viewModel.openFiles
    val activeFileIndex = viewModel.activeFileIndex
    val scope = rememberCoroutineScope()
    var expandedTabIndex by remember { mutableStateOf<Int?>(null) }
    val currentFiles by rememberUpdatedState(openFiles)
    val currentIndex by rememberUpdatedState(activeFileIndex)

    // 计算文件名冲突映射
    val filenameConflictMap by remember(openFiles) {
        derivedStateOf {
            val nameCount = mutableMapOf<String, Int>()
            openFiles.forEach { state ->
                val name = state.file.name
                nameCount[name] = (nameCount[name] ?: 0) + 1
            }
            nameCount.mapValues { it.value > 1 }
        }
    }

    // 计算每个文件的显示名称
    fun getDisplayName(editorState: com.luafabric.studio.falling.ui.editor.viewmodel.CodeEditorState, showParent: Boolean): String {
        val fileName = editorState.file.name
        val parentName = editorState.file.parentFile?.name ?: ""

        return when {
            showParent && parentName.isNotEmpty() -> "$parentName/$fileName"
            else -> fileName
        }
    }

    val pagerState = rememberPagerState(
        initialPage = when {
            openFiles.isEmpty() -> 0
            activeFileIndex in openFiles.indices -> activeFileIndex
            else -> 0
        },
        pageCount = { maxOf(1, currentFiles.size) }
    )

    var tabsLayoutComplete by remember { mutableStateOf(false) }
    var tabContentMeasured by remember { mutableStateOf(false) }

    val isTabBarFullyRendered = remember(tabsLayoutComplete, tabContentMeasured, openFiles.size) {
        tabsLayoutComplete && tabContentMeasured && openFiles.isNotEmpty()
    }

    LaunchedEffect(isTabBarFullyRendered, lastFileToOpen) {
        if (isTabBarFullyRendered && lastFileToOpen != null) {
            onTabBarRendered()
            val existingIndex = openFiles.indexOfFirst {
                it.file.absolutePath == lastFileToOpen
            }
            if (existingIndex != -1) {
                if (existingIndex != activeFileIndex) {
                    viewModel.changeActiveFileIndex(existingIndex)
                }
                scope.launch {
                    pagerState.scrollToPage(existingIndex)
                }
            }
        }
    }

    LaunchedEffect(currentIndex, currentFiles.size) {
        if (currentFiles.isNotEmpty() && currentIndex >= 0 && currentIndex < currentFiles.size && pagerState.currentPage != currentIndex) {
            pagerState.scrollToPage(currentIndex)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (currentFiles.isNotEmpty() && page in currentFiles.indices && page != currentIndex) {
                viewModel.changeActiveFileIndex(page)
            }
        }
    }

    // ========== 标签溢出下拉菜单相关状态 ==========
    var tabRowWidth by remember { mutableStateOf(0) }
    var tabPositions by remember { mutableStateOf<List<TabPosition>>(emptyList()) }
    var showTabDropdown by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val showDropdownButton by remember {
        derivedStateOf {
            tabPositions.isNotEmpty() && with(density) {
                tabPositions.last().right.roundToPx() > tabRowWidth
            }
        }
    }
    // ============================================

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background) // 添加这一行
    ) {
        AnimatedContent(
            targetState = openFiles.isEmpty(),
            transitionSpec = {
                (fadeIn(animationSpec = tween(300)) +
                        scaleIn(
                            initialScale = 0.9f,
                            animationSpec = tween(300)
                        )) togetherWith
                        (fadeOut(animationSpec = tween(200)) +
                                scaleOut(
                                    targetScale = 0.95f,
                                    animationSpec = tween(200)
                                ))
            },
            label = "EditorContentSwitch"
        ) { isEmpty: Boolean ->
            if (isEmpty) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.code_editor_no_file_open),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures { onOpenFileTree() }
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.editor_from_left),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = stringResource(R.string.code_editor_file_tree),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = TextDecoration.Underline
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                            Text(
                                text = stringResource(R.string.editor_select_file),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 主要修改：将 Box 改为 Row 水平布局
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 标签栏区域（占据剩余宽度）
                        Box(
                            modifier = Modifier.weight(1f)
                        ) {
                            ScrollableTabRow(
                                selectedTabIndex = pagerState.currentPage.coerceIn(
                                    0,
                                    maxOf(0, openFiles.size - 1)
                                ),
                                edgePadding = 0.dp,
                                divider = {},
                                indicator = { tabPositionsList ->
                                    SideEffect {
                                        tabPositions = tabPositionsList
                                    }

                                    if (tabPositionsList.isNotEmpty() && pagerState.currentPage < tabPositionsList.size) {
                                        Box(
                                            modifier = Modifier
                                                .tabIndicatorOffset(
                                                    currentTabPosition = tabPositionsList[pagerState.currentPage]
                                                )
                                                .padding(horizontal = 16.dp)
                                                .height(3.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(
                                                        topStartPercent = 100,
                                                        topEndPercent = 100,
                                                        bottomStartPercent = 0,
                                                        bottomEndPercent = 0
                                                    )
                                                )
                                        )
                                    }

                                    LaunchedEffect(tabPositionsList.size) {
                                        if (tabPositionsList.isNotEmpty()) {
                                            tabContentMeasured = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        tabsLayoutComplete = true
                                        tabRowWidth = coordinates.size.width
                                    }
                            ) {
                                openFiles.forEachIndexed { index, editorState ->
                                    Box {
                                        val isModified by remember(editorState) {
                                            derivedStateOf { editorState.content != editorState.savedContent }
                                        }
                                        val hasConflict = filenameConflictMap[editorState.file.name] ?: false
                                        val baseDisplayName = getDisplayName(editorState, hasConflict)
                                        val displayName = if (isModified) "*$baseDisplayName" else baseDisplayName

                                        Tab(
                                            selected = pagerState.currentPage == index,
                                            onClick = {
                                                if (pagerState.currentPage == index) {
                                                    expandedTabIndex = index
                                                } else {
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(index)
                                                    }
                                                }
                                            },
                                            selectedContentColor = MaterialTheme.colorScheme.primary,
                                            unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    FileTabIcon(editorState.file.name)
                                                    Text(
                                                        text = displayName,
                                                        maxLines = 1,
                                                        overflow =  TextOverflow.MiddleEllipsis
                                                    )
                                                }
                                            }
                                        )

                                        DropdownMenu(
                                            expanded = expandedTabIndex == index,
                                            onDismissRequest = { expandedTabIndex = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.close)) },
                                                onClick = {
                                                    expandedTabIndex = null
                                                    viewModel.closeFile(index)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.code_editor_close_other)) },
                                                onClick = {
                                                    expandedTabIndex = null
                                                    viewModel.closeOtherFiles(index)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.code_editor_close_all)) },
                                                onClick = {
                                                    expandedTabIndex = null
                                                    viewModel.closeAllFiles()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 溢出按钮区域：只有需要时才显示
                        if (showDropdownButton) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(end = 4.dp)
                            ) {
                                ExposedDropdownMenuBox(
                                    expanded = showTabDropdown,
                                    onExpandedChange = { showTabDropdown = it }
                                ) {
                                    // 旋转动画：根据展开状态旋转180度，使用 graphicsLayer 避免函数歧义
                                    val rotation by animateFloatAsState(
                                        targetValue = if (showTabDropdown) 180f else 0f,
                                        animationSpec = tween(durationMillis = 200),
                                        label = "arrow_rotation"
                                    )

                                    IconButton(
                                        onClick = { /* 展开状态由 ExposedDropdownMenuBox 管理，无需额外操作 */ },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .background(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = stringResource(R.string.code_editor_more_tabs),
                                            modifier = Modifier.graphicsLayer(rotationZ = rotation) // 使用 graphicsLayer 旋转
                                        )
                                    }
                                    ExposedDropdownMenu(
                                        expanded = showTabDropdown,
                                        onDismissRequest = { showTabDropdown = false },
                                        modifier = Modifier.widthIn(min = 160.dp)
                                    ) {
                                        openFiles.forEachIndexed { index, editorState ->
                                            val isModified by remember(editorState) {
                                                derivedStateOf { editorState.content != editorState.savedContent }
                                            }
                                            val hasConflict = filenameConflictMap[editorState.file.name] ?: false
                                            val baseDisplayName = getDisplayName(editorState, hasConflict)
                                            val displayName = if (isModified) "*$baseDisplayName" else baseDisplayName

                                            DropdownMenuItem(
                                                text = { Text(displayName, maxLines = 1, overflow =  TextOverflow.MiddleEllipsis) },
                                                onClick = {
                                                    showTabDropdown = false
                                                    if (pagerState.currentPage != index) {
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(index)
                                                        }
                                                    }
                                                },
                                                leadingIcon = {
                                                    FileTabIcon(editorState.file.name, modifier = Modifier.size(18.dp))
                                                },
                                                colors = if (pagerState.currentPage == index) {
                                                    MenuDefaults.itemColors(
                                                        textColor = MaterialTheme.colorScheme.primary,
                                                        leadingIconColor = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    MenuDefaults.itemColors()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val dividerAlpha by animateFloatAsState(
                        targetValue = if (panelState.expansionRatio >= 0.9f) 0f else 1f,
                        animationSpec = tween(durationMillis = 300),
                        label = "DividerAlpha"
                    )

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.alpha(dividerAlpha)
                    )

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        userScrollEnabled = false,
                        key = { page ->
                            if (page in openFiles.indices && page < openFiles.size) {
                                openFiles[page].file.absolutePath
                            } else {
                                "empty_$page"
                            }
                        }
                    ) { page ->
                        if (page in openFiles.indices) {
                            val isActiveFile = page == activeFileIndex
                            CodeEditorView(
                                modifier = Modifier.fillMaxSize(),
                                state = openFiles[page],
                                viewModel = viewModel,
                                isActiveFile = isActiveFile,
                                expansionRatio = panelState.expansionRatio,
                                onSwipe = onSwipe // 传递滑动手势回调
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
                }
            }
        }
    }
}

@Composable
fun AnimatedDrawerToggle(
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        label = "DrawerToggleProgress",
        animationSpec = tween(durationMillis = 300)
    )

    IconButton(onClick = onClick, modifier = modifier) {
        val color = LocalContentColor.current
        Canvas(
            modifier = Modifier.size(24.dp)
        ) {
            val strokeWidth = 2.dp.toPx()
            val cap = StrokeCap.Square
            val w = size.width
            val h = size.height
            val centerX = w / 2
            val centerY = h / 2
            val menuWidth = 18.dp.toPx()
            val startX = (w - menuWidth) / 2
            val endX = w - startX
            val menuGap = 5.dp.toPx()
            val arrowTipX = endX
            val shrinkOffset = 0.dp.toPx()
            val arrowShaftStartX = startX + shrinkOffset
            val arrowWingStartX = centerX + 1.dp.toPx()
            val arrowWingYOffset = (arrowTipX - arrowWingStartX)

            // 这里的 rotate 是 DrawScope 的扩展函数，已通过 import 显式导入
            rotate(degrees = progress * 180f, pivot = Offset(centerX, centerY)) {
                val midStartX = lerp(startX, arrowShaftStartX, progress)
                drawLine(
                    color = color,
                    strokeWidth = strokeWidth,
                    cap = cap,
                    start = Offset(midStartX, centerY),
                    end = Offset(endX, centerY)
                )
                val topStartX = lerp(startX, arrowWingStartX, progress)
                val topStartY = lerp(centerY - menuGap, centerY - arrowWingYOffset, progress)
                val topEndX = lerp(endX, arrowTipX, progress)
                val topEndY = lerp(centerY - menuGap, centerY, progress)
                drawLine(
                    color = color,
                    strokeWidth = strokeWidth,
                    cap = cap,
                    start = Offset(topStartX, topStartY),
                    end = Offset(topEndX, topEndY)
                )
                val bottomStartX = lerp(startX, arrowWingStartX, progress)
                val bottomStartY = lerp(centerY + menuGap, centerY + arrowWingYOffset, progress)
                val bottomEndX = lerp(endX, arrowTipX, progress)
                val bottomEndY = lerp(centerY + menuGap, centerY, progress)
                drawLine(
                    color = color,
                    strokeWidth = strokeWidth,
                    cap = cap,
                    start = Offset(bottomStartX, bottomStartY),
                    end = Offset(bottomEndX, bottomEndY)
                )
            }
        }
    }
}