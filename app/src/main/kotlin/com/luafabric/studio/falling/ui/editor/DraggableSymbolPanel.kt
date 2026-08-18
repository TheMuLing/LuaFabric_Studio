package com.luafabric.studio.falling.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.editor.viewmodel.EditorViewModel
import com.luafabric.studio.falling.ui.settings.SettingsManager
import muling.views.tool.utils.NonBlockingToastState

// 定义枚举表示待处理的类操作
private enum class ClassAction { COPY_IMPORT, VIEW_API }

@Composable
fun DraggableSymbolPanel(
    viewModel: EditorViewModel,
    panelState: DraggablePanelState,
    hasOpenFiles: Boolean,
    toast: NonBlockingToastState, // 新增 toast 参数
    modifier: Modifier = Modifier
) {
    if (!hasOpenFiles) {
        LaunchedEffect(Unit) {
            panelState.updateMinHeight(0f)
            panelState.height = 0f
        }
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val symbolBarHeightPx = with(density) { 48.dp.toPx() }

    LaunchedEffect(Unit) {
        panelState.updateMinHeight(symbolBarHeightPx)
        panelState.animateToHeight(symbolBarHeightPx)
    }

    val animatedHeight by animateFloatAsState(
        targetValue = panelState.height,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "PanelHeight"
    )

    val expansionRatio = panelState.expansionRatio
    val shadowAlpha by animateFloatAsState(
        targetValue = if (expansionRatio >= 0.95f) 0f else 0.1f * (1f - expansionRatio),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ShadowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { animatedHeight.toDp() })
            .drawBehind {
                if (shadowAlpha > 0.001f) {
                    val shadowHeight = 6.dp.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = shadowAlpha * 0.8f),
                                Color.Black.copy(alpha = shadowAlpha * 0.4f),
                                Color.Black.copy(alpha = shadowAlpha * 0.1f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = -shadowHeight,
                            tileMode = TileMode.Clamp
                        ),
                        topLeft = Offset(0f, -shadowHeight),
                        size = Size(size.width, shadowHeight)
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = shadowAlpha * 0.05f),
                                Color.Transparent
                            ),
                            startY = -shadowHeight * 0.5f,
                            endY = 0f,
                            tileMode = TileMode.Clamp
                        ),
                        topLeft = Offset(0f, -shadowHeight * 0.5f),
                        size = Size(size.width, shadowHeight * 0.5f)
                    )
                }
            }
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(panelState) {
                detectDragGestures(
                    onDragStart = { panelState.onDragStart() },
                    onDragEnd = { panelState.onDragEnd(scope) },
                    onDragCancel = { panelState.onDragEnd(scope) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        panelState.updateHeight(panelState.height - dragAmount.y)
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .padding(top = 1.dp)
        ) {
            // 拖拽手柄：垂直动画（不受内部符号栏影响）
            val showHandle = panelState.isAboveThreshold
            AnimatedVisibility(
                visible = showHandle,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                HandleBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                )
            }

            // 符号栏：当面板未超过阈值时显示
            val showSymbolBar = !panelState.isAboveThreshold
            AnimatedVisibility(
                visible = showSymbolBar,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                SymbolBar(
                    viewModel = viewModel,
                    toast = toast, // 传递 toast
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }

            // 底部区域
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.code_editor_bottom_area_content),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HandleBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        )
    }
}

@Composable
fun SymbolBar(
    viewModel: EditorViewModel,
    toast: NonBlockingToastState, // 接收 toast
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val symbols = listOf(
        "function()", "(", ")", "[", "]", "{", "}", "\"", "=", ":",
        ".", ",", ";", "_", "+", "-", "*", "/", "\\", "%",
        "#", "^", "$", "?", "&", "|", "<", ">", "~", "'"
    )

    val smartSortingEnabled by remember { derivedStateOf { SettingsManager.currentSettings.smartSortingEnabled } }
    val selectedClassName = viewModel.selectedClassName
    val selectedCandidates = viewModel.selectedClassCandidates

    var classNameMenuExpanded by remember { mutableStateOf(false) }
    var showAmbiguousDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<ClassAction?>(null) }

    // 格式选择对话框状态
    var showFormatDialog by remember { mutableStateOf(false) }
    var pendingFullName by remember { mutableStateOf<String?>(null) }

    val baseSortedSymbols = remember(viewModel.symbolFrequencyMap, smartSortingEnabled) {
        if (smartSortingEnabled) {
            symbols.sortedWith { a, b ->
                when {
                    a == "function()" -> -1
                    b == "function()" -> 1
                    else -> {
                        val freqA = viewModel.symbolFrequencyMap[a] ?: 0
                        val freqB = viewModel.symbolFrequencyMap[b] ?: 0
                        freqB.compareTo(freqA)
                    }
                }
            }
        } else {
            symbols
        }
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
    ) {
        // 使用 AnimatedContent 实现符号栏内容的滑动切换
        AnimatedContent(
            targetState = selectedClassName,
            transitionSpec = {
                // 进入：从左边滑入（-fullWidth -> 0）
                // 退出：滑向右边（0 -> fullWidth）
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300)) togetherWith
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(300)
                        ) + fadeOut(animationSpec = tween(300))
            },
            label = "symbol_bar_transition"
        ) { targetSelected ->
            val contentList = if (targetSelected != null) {
                listOf(targetSelected) + baseSortedSymbols.filter { it != targetSelected }
            } else {
                baseSortedSymbols
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                contentList.forEach { symbol ->
                    if (symbol == targetSelected) {
                        Box {
                            SymbolButton(
                                symbol = symbol,
                                onClick = { classNameMenuExpanded = true }
                            )
                            DropdownMenu(
                                expanded = classNameMenuExpanded,
                                onDismissRequest = { classNameMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.java_api_copy_import)) },
                                    onClick = {
                                        classNameMenuExpanded = false
                                        if (selectedCandidates != null) {
                                            if (selectedCandidates.size == 1) {
                                                // 直接打开格式选择对话框
                                                pendingFullName = selectedCandidates.first()
                                                showFormatDialog = true
                                            } else {
                                                // 先显示歧义对话框，选择后再打开格式对话框
                                                pendingAction = ClassAction.COPY_IMPORT
                                                showAmbiguousDialog = true
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.java_api_view_api)) },
                                    onClick = {
                                        classNameMenuExpanded = false
                                        if (selectedCandidates != null) {
                                            if (selectedCandidates.size == 1) {
                                                viewModel.requestNavigateToApiClass(selectedCandidates.first())
                                            } else {
                                                pendingAction = ClassAction.VIEW_API
                                                showAmbiguousDialog = true
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        SymbolButton(
                            symbol = symbol,
                            onClick = {
                                viewModel.incrementSymbolFrequency(symbol)
                                viewModel.insertSymbolToCorrectEditor(symbol)
                            }
                        )
                    }
                }
            }
        }
    }

    // 歧义选择对话框
    if (showAmbiguousDialog && selectedCandidates != null && selectedCandidates.isNotEmpty()) {
        var selectedFullName by remember { mutableStateOf(selectedCandidates.first()) }

        AlertDialog(
            onDismissRequest = { showAmbiguousDialog = false },
            title = { Text(stringResource(R.string.java_api_ambiguous_title)) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .fillMaxWidth()
                ) {
                    items(selectedCandidates) { fullName ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFullName = fullName }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedFullName == fullName,
                                onClick = { selectedFullName = fullName }
                            )
                            Text(
                                text = fullName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAmbiguousDialog = false
                        when (pendingAction) {
                            ClassAction.COPY_IMPORT -> {
                                pendingFullName = selectedFullName
                                showFormatDialog = true
                            }
                            ClassAction.VIEW_API -> {
                                viewModel.requestNavigateToApiClass(selectedFullName)
                            }
                            null -> { /* ignore */ }
                        }
                        pendingAction = null
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAmbiguousDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 格式选择对话框
    if (showFormatDialog && pendingFullName != null) {
        val fullName = pendingFullName!!
        AlertDialog(
            onDismissRequest = {
                showFormatDialog = false
                pendingFullName = null
            },
            title = { Text(stringResource(R.string.select_copy_format)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 选项1: import "全类名"
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val importStatement = "import \"$fullName\""
                                clipboardManager.setText(AnnotatedString(importStatement))
                                toast.showToast(context.getString(R.string.java_api_import_statement_copied))
                                showFormatDialog = false
                                pendingFullName = null
                            }
                            .padding(12.dp),
                    ) {
                        Text(
                            text = "import \"$fullName\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 选项2: local 类名 = luajava.bindClass "全类名"
                    val shortName = fullName.substringAfterLast('.')
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val bindStatement = "local $shortName = luajava.bindClass \"$fullName\""
                                clipboardManager.setText(AnnotatedString(bindStatement))
                                toast.showToast(context.getString(R.string.java_api_import_statement_copied))
                                showFormatDialog = false
                                pendingFullName = null
                            }
                            .padding(12.dp),
                    ) {
                        Text(
                            text = "local $shortName = luajava.bindClass \"$fullName\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFormatDialog = false
                    pendingFullName = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SymbolButton(
    symbol: String,
    onClick: () -> Unit
) {
    val displaySymbol = when (symbol) {
        "function", "function()", "func", "func()" -> "fun()"
        "Tab", "↹", "⇔", "↔" -> "↹"
        else -> symbol
    }

    Surface(
        modifier = Modifier
            .height(36.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displaySymbol,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}