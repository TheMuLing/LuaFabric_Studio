package com.luafabric.studio.falling.ui.analyse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.androlua.LuaLexer
import com.androlua.LuaTokenTypes
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.langs.lua.tools.CompleteHashmapUtils
import muling.views.tool.utils.LogCatcher
import muling.views.tool.utils.NonBlockingToastState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 导入分析屏幕
 *
 * @param codeContent 当前编辑器中的代码文本
 * @param projectPath 项目路径（可选，用于递归分析导入的文件，暂未使用）
 * @param onBack 返回回调
 * @param toast KToast状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyseScreen(
    codeContent: String,
    projectPath: String? = null,
    onBack: () -> Unit,
    toast: NonBlockingToastState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // 菜单展开状态
    var menuExpanded by remember { mutableStateOf(false) }

    // 状态
    var isLoading by remember { mutableStateOf(true) }
    val classItems = remember { mutableStateListOf<ClassItem>() }
    val selectionState = remember { mutableStateMapOf<String, Boolean>() }

    // 歧义选择对话框状态
    var showAmbiguousDialog by remember { mutableStateOf(false) }
    var ambiguousItem by remember { mutableStateOf<ClassItem?>(null) }
    var onAmbiguousChoice by remember { mutableStateOf<(String?) -> Unit>({}) }

    // 加载类映射并分析代码
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // 1. 加载 classMap.dat
                val classMap = CompleteHashmapUtils.loadHashMapFromFile2(context, "classMap.dat")
                    ?: emptyMap()
                // 2. 分析代码
                val result = analyzeCodeForClasses(codeContent, classMap)
                // 3. 将结果按短类名分组，合并同名类
                val grouped = result.groupBy { it.substringAfterLast('.') }
                val items = grouped.map { (shortName, fullNames) ->
                    ClassItem(
                        shortName = shortName,
                        candidates = fullNames.distinct().sorted()
                    )
                }.sortedBy { it.shortName }

                classItems.clear()
                classItems.addAll(items)
            } catch (e: Exception) {
                LogCatcher.e("AnalyseScreen", "分析失败", e)
                scope.launch { toast.showToast(context.getString(R.string.analyse_analysis_failed, e.message)) }
            } finally {
                isLoading = false
            }
        }
    }

    // 全选所有项
    fun selectAll() {
        classItems.forEach { item ->
            selectionState[item.shortName] = true
        }
    }

    // 取消全选
    fun deselectAll() {
        classItems.forEach { item ->
            selectionState[item.shortName] = false
        }
    }

    // 反选
    fun invertSelection() {
        classItems.forEach { item ->
            val current = selectionState[item.shortName] ?: false
            selectionState[item.shortName] = !current
        }
    }

    // 处理复制选中
    fun copySelected() {
        val selectedItems = classItems.filter { selectionState[it.shortName] == true }

        if (selectedItems.isEmpty()) {
            scope.launch { toast.showToast(context.getString(R.string.analyse_please_select_classes)) }
            return
        }

        // 检查是否有歧义项（多个候选）
        val ambiguousItems = selectedItems.filter { it.candidates.size > 1 }

        when {
            ambiguousItems.isEmpty() -> {
                // 无歧义，直接复制
                val imports = selectedItems.flatMap { it.candidates }
                    .joinToString("\n") { "import \"$it\"" }
                clipboardManager.setText(AnnotatedString(imports))
                scope.launch { toast.showToast(context.getString(R.string.analyse_import_copied, imports.lines().size)) }
            }

            ambiguousItems.size == 1 -> {
                // 只有一个歧义项，弹出对话框让用户选择
                val item = ambiguousItems.first()
                ambiguousItem = item
                showAmbiguousDialog = true
                onAmbiguousChoice = { selectedFullName ->
                    if (selectedFullName != null) {
                        // 将选中的完整类名与其他无歧义项合并
                        val otherImports = selectedItems
                            .filter { it.shortName != item.shortName }
                            .flatMap { it.candidates }
                            .joinToString("\n") { "import \"$it\"" }
                        val selectedImport = "import \"$selectedFullName\""
                        val finalImports = if (otherImports.isNotEmpty()) {
                            "$selectedImport\n$otherImports"
                        } else {
                            selectedImport
                        }
                        clipboardManager.setText(AnnotatedString(finalImports))
                        scope.launch { toast.showToast(context.getString(R.string.analyse_import_copied, finalImports.lines().size)) }
                    }
                    showAmbiguousDialog = false
                    ambiguousItem = null
                }
            }

            else -> {
                // 多个歧义项，简化处理：提示用户逐一选择
                scope.launch { toast.showToast(context.getString(R.string.analyse_multiple_ambiguous)) }
            }
        }
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.code_editor_analyse)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.analyse_select_all))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.analyse_select_all)) },
                                onClick = {
                                    selectAll()
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.analyse_invert_selection)) },
                                onClick = {
                                    invertSelection()
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.analyse_deselect_all)) },
                                onClick = {
                                    deselectAll()
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                    // 复制按钮
                    IconButton(onClick = { copySelected() }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.analyse_copy_selected))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (classItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.analyse_no_classes_detected),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(classItems) { item ->
                        ClassListItem(
                            item = item,
                            isChecked = selectionState[item.shortName] ?: false,
                            onCheckedChange = { checked: Boolean ->
                                selectionState[item.shortName] = checked
                            }
                        )
                    }
                }
            }
        }
    }

    // 歧义选择对话框（支持滚动）
    if (showAmbiguousDialog && ambiguousItem != null) {
        val item = ambiguousItem!!
        var selectedFullName by remember { mutableStateOf(item.candidates.first()) }

        AlertDialog(
            onDismissRequest = {
                showAmbiguousDialog = false
                ambiguousItem = null
            },
            title = { Text(stringResource(R.string.java_api_ambiguous_title)) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 300.dp) // 限制最大高度，超长可滚动
                        .fillMaxWidth()
                ) {
                    items(item.candidates) { fullName ->
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
                    onClick = { onAmbiguousChoice(selectedFullName) }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAmbiguousDialog = false
                        ambiguousItem = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 类项数据 - 支持多个候选完整类名
 */
data class ClassItem(
    val shortName: String,
    val candidates: List<String>
) {
    // 为了方便，取第一个作为主要完整名用于显示
    val primaryFullName: String get() = candidates.firstOrNull() ?: ""
}

@Composable
fun ClassListItem(
    item: ClassItem,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        onClick = { onCheckedChange(!isChecked) },
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.shortName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isChecked) MaterialTheme.typography.bodyLarge.fontWeight else null
                    )
                    if (item.candidates.size > 1) {
                        Text(
                            text = stringResource(R.string.analyse_more_items, item.candidates.size - 1),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                Text(
                    text = item.primaryFullName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 分析代码，找出可能引用的 Java 类
 *
 * @param code Lua 代码
 * @param classMap 短类名 -> 完整类名列表的映射
 * @return 匹配的完整类名列表
 */
private suspend fun analyzeCodeForClasses(
    code: String,
    classMap: Map<String, List<String>>
): List<String> = withContext(Dispatchers.IO) {
    val result = mutableSetOf<String>()
    val lexer = LuaLexer(code)
    try {
        var lastTokenType: LuaTokenTypes? = null
        while (true) {
            val token = lexer.advance() ?: break
            if (lastTokenType != LuaTokenTypes.DOT && token == LuaTokenTypes.NAME) {
                val identifier = lexer.yytext()
                // 检查标识符是否可能是类名（以大写字母开头？）
                if (identifier.isNotEmpty() && identifier[0].isUpperCase()) {
                    classMap[identifier]?.let { candidates ->
                        result.addAll(candidates)
                    }
                }
            }
            lastTokenType = token
        }
    } catch (e: Exception) {
        LogCatcher.e("AnalyseScreen", "解析代码失败", e)
    }
    return@withContext result.toList()
}