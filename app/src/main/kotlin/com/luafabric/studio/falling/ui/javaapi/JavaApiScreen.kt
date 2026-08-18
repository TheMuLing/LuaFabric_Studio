package com.luafabric.studio.falling.ui.javaapi

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.langs.lua.tools.CompleteHashmapUtils
import muling.views.tool.utils.LogCatcher
import muling.views.tool.utils.NonBlockingToastState
import muling.views.tool.utils.TransitionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier as JModifier

/**
 * 主屏幕：显示类列表或详情页，共用同一个搜索框
 *
 * @param initialClass 如果非空，加载后直接跳转到该类详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JavaApiScreen(
    initialClass: String? = null,
    onBack: () -> Unit,
    toast: NonBlockingToastState
) {
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    val allClassItems = remember { mutableStateListOf<ClassItem>() }
    val classStack = remember { mutableStateListOf<String>() }

    // 搜索状态（分开保存，页面切换时各自保留）
    var classListSearchQuery by remember { mutableStateOf("") }
    var classDetailSearchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // 分类状态：存储资源ID，避免在remember中直接调用stringResource
    data class CategoryData(val nameResId: Int, val predicate: (ClassItem) -> Boolean)

    val categories = remember {
        listOf(
            CategoryData(R.string.java_api_category_all) { true },
            CategoryData(R.string.java_api_category_android) { it.fullName.startsWith("android.") },
            CategoryData(R.string.java_api_category_androidx) { it.fullName.startsWith("androidx.") },
            CategoryData(R.string.java_api_category_material) { it.fullName.startsWith("com.google.android.material.") },
            CategoryData(R.string.java_api_category_androlua) {
                it.fullName.contains("androlua", ignoreCase = true) ||
                        it.fullName.contains("nirenr", ignoreCase = true) ||
                        it.fullName.contains("myopicmobile", ignoreCase = true) ||
                        it.fullName.contains("luajava", ignoreCase = true)
            },
            CategoryData(R.string.java_api_category_LuaFabric) {
                it.fullName.startsWith("com.luafabric.studio.falling.") ||
                        it.fullName.startsWith("muling.views.tool.")
            },
            CategoryData(R.string.java_api_category_gson) { it.fullName.startsWith("com.google.gson.") },
            CategoryData(R.string.java_api_category_glide) { it.fullName.startsWith("com.bumptech.glide.") },
            CategoryData(R.string.java_api_category_okhttp) { it.fullName.startsWith("okhttp3.") },
            CategoryData(R.string.java_api_category_r) { it.fullName.endsWith(".R") },
            CategoryData(R.string.java_api_category_util) {
                val simpleName = it.shortName
                simpleName.contains("Util", ignoreCase = true) ||
                        simpleName.contains("Helper", ignoreCase = true) ||
                        simpleName.contains("Manager", ignoreCase = true) ||
                        simpleName.contains("Tool", ignoreCase = true) ||
                        simpleName.contains("Utils", ignoreCase = true)
            },
            CategoryData(R.string.java_api_category_interface) { it.isInterface },
            CategoryData(R.string.java_api_category_abstract) { it.isAbstract }
        )
    }
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    // 加载 classMap.dat 并预计算类的类型信息
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val classMap = CompleteHashmapUtils.loadHashMapFromFile2(context, "classMap.dat")
            if (classMap != null) {
                val items = classMap.entries
                    .flatMap { (shortName, fullNames) ->
                        fullNames.mapNotNull { fullName ->
                            try {
                                val clazz = Class.forName(fullName, false, this::class.java.classLoader)
                                val standardName = clazz.name
                                val isInterface = clazz.isInterface
                                val isAbstract = JModifier.isAbstract(clazz.modifiers)
                                ClassItem(shortName, standardName, isInterface, isAbstract)
                            } catch (_: Throwable) {
                                LogCatcher.w("JavaApiScreen", "Skipping unloadable class: $fullName")
                                null
                            }
                        }
                    }
                    .filter { !it.fullName.contains('$') && !it.fullName.contains('_') }
                    .sortedBy { it.fullName.lowercase() }
                allClassItems.clear()
                allClassItems.addAll(items)
            }
            isLoading = false
        }

        // 如果指定了初始类，且类列表已加载，则直接跳转到详情
        if (initialClass != null && allClassItems.isNotEmpty()) {
            val found = allClassItems.find { it.fullName == initialClass }
            if (found != null) {
                classStack.clear()
                classStack.add(initialClass)
                classDetailSearchQuery = ""
            }
        }
    }

    // 当前是否在详情页（栈非空）
    val isDetail = classStack.isNotEmpty()

    // 过滤后的类列表
    val filteredClassItems by remember(allClassItems, classListSearchQuery, selectedCategoryIndex) {
        derivedStateOf {
            allClassItems.filter { item ->
                categories[selectedCategoryIndex].predicate(item) &&
                        (classListSearchQuery.isBlank() ||
                                item.shortName.contains(classListSearchQuery, ignoreCase = true) ||
                                item.fullName.contains(classListSearchQuery, ignoreCase = true))
            }
        }
    }

BackHandler(enabled = true) {
    if (classStack.isNotEmpty()) {
        classStack.removeLastOrNull()
    } else {
        onBack()
    }
}

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isDetail) {
                        val currentClass = classStack.last()
                        Column {
                            Text(
                                text = currentClass.substringAfterLast('.'),
                                maxLines = 1
                            )
                            Text(
                                text = currentClass,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(stringResource(R.string.java_api_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (classStack.isNotEmpty()) {
                            classStack.removeLastOrNull()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    if (!isDetail) {
                        // 分类按钮
                        IconButton(onClick = { showCategoryMenu = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.java_api_filter_category))
                        }
                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false }
                        ) {
                            categories.forEachIndexed { index, category ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(category.nameResId)) },
                                    onClick = {
                                        selectedCategoryIndex = index
                                        showCategoryMenu = false
                                    },
                                    leadingIcon = if (index == selectedCategoryIndex) {
                                        { Icon(Icons.Default.FilterList, null) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 共用搜索框
            TextField(
                value = if (isDetail) classDetailSearchQuery else classListSearchQuery,
                onValueChange = { newQuery ->
                    if (isDetail) {
                        classDetailSearchQuery = newQuery
                    } else {
                        classListSearchQuery = newQuery
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        if (isDetail) stringResource(R.string.java_api_search_members_placeholder)
                        else stringResource(R.string.java_api_search_placeholder)
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    val currentQuery = if (isDetail) classDetailSearchQuery else classListSearchQuery
                    if (currentQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            if (isDetail) {
                                classDetailSearchQuery = ""
                            } else {
                                classListSearchQuery = ""
                            }
                        }) {
                            Icon(Icons.Default.Clear, stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator()
                    }
                } else {
                    AnimatedContent(
                        targetState = classStack.lastOrNull(),
                        transitionSpec = {
                            val isForward = when {
                                targetState == null -> false
                                initialState == null -> true
                                else -> {
                                    val initialIndex = classStack.indexOf(initialState)
                                    val targetIndex = classStack.indexOf(targetState)
                                    if (initialIndex == -1) false else targetIndex > initialIndex
                                }
                            }
                            val currentIndex = if (isForward) 0 else 1
                            val targetIndex = if (isForward) 1 else 0
                            TransitionUtil.createPageTransition(currentIndex, targetIndex, 300)
                        },
                        label = "class_detail_transition"
                    ) { target ->
                        if (target == null) {
                            ClassListScreen(
                                items = filteredClassItems,
                                onItemClick = { fullName ->
                                    classStack.clear()
                                    classStack.add(fullName)
                                    classDetailSearchQuery = "" // 进入详情清空详情搜索
                                },
                                toast = toast
                            )
                        } else {
                            ClassDetailScreen(
                                classFullName = target,
                                onNavigateToClass = { newFullName ->
                                    classStack.add(newFullName)
                                },
                                toast = toast,
                                searchQuery = classDetailSearchQuery,
                                onSearchQueryChange = { classDetailSearchQuery = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ClassItem(
    val shortName: String,
    val fullName: String,
    val isInterface: Boolean = false,
    val isAbstract: Boolean = false
)

@Composable
fun ClassListScreen(
    items: List<ClassItem>,
    onItemClick: (String) -> Unit,
    toast: NonBlockingToastState
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { item ->
            ClassListItem(
                item = item,
                onClick = { onItemClick(item.fullName) },
                toast = toast
            )
        }
    }
}

@Composable
fun ClassListItem(
    item: ClassItem,
    onClick: () -> Unit,
    toast: NonBlockingToastState
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        clipboard.setText(AnnotatedString(item.fullName))
                        scope.launch { toast.showToast(context.getString(R.string.java_api_class_name_copied)) }
                    }
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.shortName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                )
                Text(
                    text = item.fullName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// 标签数据，持有资源 ID 而不是直接字符串
data class TabData(
    val titleResId: Int,
    val members: List<MemberDisplayInfo>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
    classFullName: String,
    onNavigateToClass: (String) -> Unit,
    toast: NonBlockingToastState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var classInfo by remember { mutableStateOf<ClassInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }

    // 使用资源 ID 的标签列表
    var tabs by remember { mutableStateOf(listOf<TabData>()) }
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    // 详情对话框状态
    var showDetailDialog by remember { mutableStateOf(false) }
    var detailDialogTitle by remember { mutableStateOf("") }
    var detailDialogItems by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var detailDialogExtraPreview: @Composable (() -> Unit)? by remember { mutableStateOf(null) }

    // 加载类信息
    LaunchedEffect(classFullName) {
        withContext(Dispatchers.IO) {
            try {
                val info = ClassReflectUtil.loadClassInfo(context, classFullName)
                classInfo = info
            } catch (e: Exception) {
                LogCatcher.e("JavaApiScreen", "加载类信息失败", e)
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    // 根据 classInfo 更新 tabs（使用资源 ID）
    LaunchedEffect(classInfo) {
        if (classInfo != null) {
            tabs = listOfNotNull(
                if (classInfo!!.constructors.isNotEmpty()) TabData(R.string.java_api_tab_constructors, classInfo!!.constructors) else null,
                if (classInfo!!.methods.isNotEmpty()) TabData(R.string.java_api_tab_methods, classInfo!!.methods) else null,
                if (classInfo!!.fields.isNotEmpty()) TabData(R.string.java_api_tab_fields, classInfo!!.fields) else null,
                if (classInfo!!.superClasses.isNotEmpty()) TabData(R.string.java_api_tab_superclasses, classInfo!!.superClasses) else null,
                if (classInfo!!.interfaces.isNotEmpty()) TabData(R.string.java_api_tab_interfaces, classInfo!!.interfaces) else null,
                if (classInfo!!.innerClasses.isNotEmpty()) TabData(R.string.java_api_tab_innerclasses, classInfo!!.innerClasses) else null
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.java_api_loading))
                }
            }
            error != null -> Box(contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.java_api_load_failed, error ?: ""), color = MaterialTheme.colorScheme.error)
            }
            classInfo == null -> Box(contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.java_api_no_info))
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (tabs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(stringResource(R.string.java_api_no_content), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        ScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage.coerceIn(0, tabs.size - 1),
                            edgePadding = 0.dp,
                            containerColor = MaterialTheme.colorScheme.surface,
                            indicator = { tabPositions ->
                                if (pagerState.currentPage < tabPositions.size) {
                                    Box(
                                        Modifier
                                            .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                                            .height(3.dp)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            },
                            divider = {}
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                    text = { Text(stringResource(tab.titleResId)) },
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f)
                        ) { page ->
                            val members = tabs[page].members
                            val filteredMembers = if (searchQuery.isBlank()) members
                            else members.filter { it.matchesSearch(searchQuery) }
                            val currentTabTitleResId = tabs[page].titleResId

                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredMembers) { member ->
                                    // 根据是否是类引用决定点击行为
                                    if (member.isClassReference) {
                                        // 父类、接口、内部类：点击直接导航
                                        ClassReferenceItem(
                                            member = member,
                                            onClick = { onNavigateToClass(member.copyTextForMenu) },
                                            onLongClick = {
                                                clipboard.setText(AnnotatedString(member.copyTextForMenu))
                                                scope.launch { toast.showToast(context.getString(R.string.java_api_class_name_copied)) }
                                            }
                                        )
                                    } else {
                                        // 构造器、方法、字段：点击弹出菜单
                                        MemberItem(
                                            member = member,
                                            tabTitleResId = currentTabTitleResId,
                                            onCopy = { copyText ->
                                                clipboard.setText(AnnotatedString(copyText))
                                                scope.launch { toast.showToast(context.getString(R.string.java_api_copy_success)) }
                                            },
                                            onShowDetail = { title, items, preview ->
                                                detailDialogTitle = title
                                                detailDialogItems = items
                                                detailDialogExtraPreview = preview
                                                showDetailDialog = true
                                            }
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

    // 详情对话框
    if (showDetailDialog) {
        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            title = { Text(detailDialogTitle) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    detailDialogExtraPreview?.let { preview ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            preview()
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(detailDialogItems) { (label, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        clipboard.setText(AnnotatedString(value))
                                        scope.launch { toast.showToast(context.getString(R.string.java_api_copy_label, label)) }
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$label:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(0.3f)
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(0.7f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
fun ClassReferenceItem(
    member: MemberDisplayInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = MaterialTheme.typography.bodyLarge.fontWeight
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = member.displaySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MemberItem(
    member: MemberDisplayInfo,
    tabTitleResId: Int,
    onCopy: (String) -> Unit,
    onShowDetail: (String, List<Pair<String, String>>, @Composable (() -> Unit)?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = {
                        expanded = true
                    },
                    onLongClick = {
                        val detailTitle = when (tabTitleResId) {
                            R.string.java_api_tab_constructors -> context.getString(R.string.java_api_constructor_detail)
                            R.string.java_api_tab_methods -> context.getString(R.string.java_api_method_detail)
                            R.string.java_api_tab_fields -> context.getString(R.string.java_api_field_detail)
                            else -> context.getString(R.string.java_api_details)
                        }
                        onShowDetail(detailTitle, member.detailItems, member.previewProvider)
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                val annotatedTitle = buildAnnotatedString {
                    val text = member.displayTitle
                    val regex = "\\([^()]*\\)".toRegex()
                    var lastIndex = 0
                    regex.findAll(text).forEach { matchResult ->
                        if (matchResult.range.first > lastIndex) {
                            append(text.substring(lastIndex, matchResult.range.first))
                        }
                        append("(")
                        val inner = matchResult.value.substring(1, matchResult.value.length - 1)
                        pushStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = MaterialTheme.colorScheme.primary))
                        append(inner)
                        pop()
                        append(")")
                        lastIndex = matchResult.range.last + 1
                    }
                    if (lastIndex < text.length) {
                        append(text.substring(lastIndex))
                    }
                }
                Text(
                    text = annotatedTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = MaterialTheme.typography.bodyLarge.fontWeight
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = member.displaySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 下拉菜单（无图标）
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(140.dp)
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy)) },
                onClick = {
                    expanded = false
                    onCopy(member.copyTextForMenu)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.java_api_details)) },
                onClick = {
                    expanded = false
                    val detailTitle = when (tabTitleResId) {
                        R.string.java_api_tab_constructors -> context.getString(R.string.java_api_constructor_detail)
                        R.string.java_api_tab_methods -> context.getString(R.string.java_api_method_detail)
                        R.string.java_api_tab_fields -> context.getString(R.string.java_api_field_detail)
                        else -> context.getString(R.string.java_api_details)
                    }
                    onShowDetail(detailTitle, member.detailItems, member.previewProvider)
                }
            )
        }
    }
}

data class MemberDisplayInfo(
    val displayTitle: String,
    val displaySubtitle: String,
    val copyTextForMenu: String,          // 用于复制（不带参数括号内容）
    val detailItems: List<Pair<String, String>>,
    val searchableText: String,
    val previewProvider: @Composable (() -> Unit)? = null,
    val isClassReference: Boolean = false // 标记是否为父类、接口、内部类等引用
) {
    fun matchesSearch(query: String): Boolean {
        return query.isBlank() || searchableText.contains(query, ignoreCase = true)
    }
}

data class ClassInfo(
    val constructors: List<MemberDisplayInfo>,
    val methods: List<MemberDisplayInfo>,
    val fields: List<MemberDisplayInfo>,
    val superClasses: List<MemberDisplayInfo>,
    val interfaces: List<MemberDisplayInfo>,
    val innerClasses: List<MemberDisplayInfo>
)

object ClassReflectUtil {

    private val PRIMITIVE_MAP = mapOf(
        "byte" to "java.lang.Byte",
        "short" to "java.lang.Short",
        "int" to "java.lang.Integer",
        "long" to "java.lang.Long",
        "float" to "java.lang.Float",
        "double" to "java.lang.Double",
        "char" to "java.lang.Character",
        "boolean" to "java.lang.Boolean"
    )

    fun loadClassInfo(context: Context, className: String): ClassInfo? {
        val actualClassName = PRIMITIVE_MAP[className] ?: className
        val clazz = try {
            Class.forName(actualClassName, false, ClassLoader.getSystemClassLoader())
        } catch (_: ClassNotFoundException) {
            try {
                Class.forName(actualClassName)
            } catch (e2: ClassNotFoundException) {
                LogCatcher.e("ClassReflectUtil", "Class not found: $actualClassName, $e2")
                return null
            }
        }

        val constructors = getConstructors(context, clazz)
        val methods = getMethods(context, clazz)
        val fields = getFields(context, clazz)
        val superClasses = getSuperClasses(context, clazz)
        val interfaces = getInterfaces(context, clazz)
        val innerClasses = getInnerClasses(context, clazz)

        return ClassInfo(constructors, methods, fields, superClasses, interfaces, innerClasses)
    }

    private fun getConstructors(context: Context, clazz: Class<*>): List<MemberDisplayInfo> {
        return clazz.declaredConstructors.map { constructor ->
            val modifiers = JModifier.toString(constructor.modifiers)
            val paramTypes = constructor.parameterTypes.map { it.simpleName }
            val paramStr = paramTypes.joinToString(", ")
            val displayTitle = "${clazz.simpleName}($paramStr)"
            val detailItems = buildDetailItems(
                mapOf(
                    context.getString(R.string.java_api_field_name) to clazz.simpleName,
                    context.getString(R.string.java_api_field_modifiers) to modifiers,
                    context.getString(R.string.java_api_field_parameters) to paramTypes.joinToString(", ")
                )
            )
            MemberDisplayInfo(
                displayTitle = displayTitle,
                displaySubtitle = modifiers,
                copyTextForMenu = "${clazz.simpleName}()",
                detailItems = detailItems,
                searchableText = "$displayTitle $modifiers $paramStr",
                isClassReference = false
            )
        }
    }

    private fun getMethods(context: Context, clazz: Class<*>): List<MemberDisplayInfo> {
        val signatures = mutableSetOf<String>()
        val result = mutableListOf<MemberDisplayInfo>()

        clazz.declaredMethods.forEach { method ->
            val signature = buildSignature(method)
            if (!signatures.contains(signature) && !isSyntheticOrInner(method.name)) {
                signatures.add(signature)
                result.add(methodToDisplayInfo(context, method))
            }
        }

        clazz.methods.forEach { method ->
            val signature = buildSignature(method)
            if (!signatures.contains(signature) && !isSyntheticOrInner(method.name)) {
                signatures.add(signature)
                result.add(methodToDisplayInfo(context, method))
            }
        }

        return result
    }

    private fun methodToDisplayInfo(context: Context, method: Method): MemberDisplayInfo {
        val modifiers = JModifier.toString(method.modifiers)
        val returnTypeSimple = method.returnType.simpleName
        val paramTypes = method.parameterTypes.map { it.simpleName }
        val paramStr = paramTypes.joinToString(", ")
        val displayTitle = "${method.name}($paramStr)"
        val displaySubtitle = "$returnTypeSimple $modifiers"
        val detailItems = buildDetailItems(
            mapOf(
                context.getString(R.string.java_api_field_name) to method.name,
                context.getString(R.string.java_api_field_modifiers) to modifiers,
                context.getString(R.string.java_api_field_return_type) to method.returnType.name,
                context.getString(R.string.java_api_field_parameters) to paramTypes.joinToString(", ")
            )
        )
        return MemberDisplayInfo(
            displayTitle = displayTitle,
            displaySubtitle = displaySubtitle,
            copyTextForMenu = "${method.name}()",
            detailItems = detailItems,
            searchableText = "$displayTitle $returnTypeSimple $modifiers $paramStr",
            isClassReference = false
        )
    }

    private fun getFields(context: Context, clazz: Class<*>): List<MemberDisplayInfo> {
        return clazz.declaredFields.map { field ->
            val modifiers = JModifier.toString(field.modifiers)
            val typeSimple = field.type.simpleName
            val displayTitle = field.name
            val displaySubtitle = "$typeSimple $modifiers"
            val detailItems = buildDetailItems(
                mapOf(
                    context.getString(R.string.java_api_field_name) to field.name,
                    context.getString(R.string.java_api_field_modifiers) to modifiers,
                    context.getString(R.string.java_api_field_type) to field.type.name
                )
            )

            // 检查是否为 R 资源字段（属于某个 R$ 内部类）
            val previewProvider = if (clazz.name.contains(".R$")) {
                @Composable {
                    ResourcePreview(context, field, clazz)
                }
            } else null

            MemberDisplayInfo(
                displayTitle = displayTitle,
                displaySubtitle = displaySubtitle,
                copyTextForMenu = field.name,
                detailItems = detailItems,
                searchableText = "$displayTitle $typeSimple $modifiers",
                previewProvider = previewProvider,
                isClassReference = false
            )
        }
    }

    @Composable
    private fun ResourcePreview(context: Context, field: Field, declaringClass: Class<*>) {
        val resId = try {
            if (JModifier.isStatic(field.modifiers)) {
                field.getInt(null)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

        if (resId == null || resId == 0) {
            Text(context.getString(R.string.java_api_preview_unable_to_get_res_id), style = MaterialTheme.typography.bodySmall)
            return
        }

        val resources = context.resources
        val resourceType = declaringClass.simpleName.lowercase()

        when {
            resourceType.contains("color") -> {
                val colorInt = try { ContextCompat.getColor(context, resId) } catch (_: Exception) { null }
                if (colorInt != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(colorInt), RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Text(context.getString(R.string.java_api_preview_invalid_color), style = MaterialTheme.typography.bodySmall)
                }
            }
            resourceType.contains("drawable") || resourceType.contains("mipmap") -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(resId)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            resourceType.contains("string") -> {
                val str = try { resources.getString(resId) } catch (_: Exception) { null }
                Text(str ?: context.getString(R.string.java_api_preview_unable_to_get_string), style = MaterialTheme.typography.bodyMedium)
            }
            resourceType.contains("bool") -> {
                val bool = try { resources.getBoolean(resId) } catch (_: Exception) { null }
                Text(context.getString(R.string.java_api_preview_boolean, bool.toString()), style = MaterialTheme.typography.bodyMedium)
            }
            resourceType.contains("dimen") -> {
                val dimen = try { resources.getDimension(resId) } catch (_: Exception) { null }
                Text(context.getString(R.string.java_api_preview_dimension, dimen?.toString() ?: "?"), style = MaterialTheme.typography.bodyMedium)
            }
            resourceType.contains("integer") -> {
                val int = try { resources.getInteger(resId) } catch (_: Exception) { null }
                Text(context.getString(R.string.java_api_preview_integer, int?.toString() ?: "?"), style = MaterialTheme.typography.bodyMedium)
            }
            resourceType.contains("font") -> {
                val font = try { resources.getFont(resId) } catch (_: Exception) { null }
                if (font != null) {
                    Text(
                        text = "Aa",
                        fontFamily = androidx.compose.ui.text.font.FontFamily(font),
                        fontSize = 18.sp
                    )
                } else {
                    Text(context.getString(R.string.java_api_preview_unable_to_load_font), style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> {
                Text(context.getString(R.string.java_api_preview_resource_id, resId), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    private fun getSuperClasses(context: Context, clazz: Class<*>): List<MemberDisplayInfo> {
        val list = mutableListOf<MemberDisplayInfo>()
        var current = clazz.superclass
        while (current != null) {
            list.add(
                MemberDisplayInfo(
                    displayTitle = current.simpleName,
                    displaySubtitle = current.name,
                    copyTextForMenu = current.name,
                    detailItems = listOf(context.getString(R.string.java_api_full_class_name) to current.name),
                    searchableText = current.name,
                    isClassReference = true
                )
            )
            current = current.superclass
        }
        return list
    }

    private fun getInterfaces(context: Context, clazz: Class<*>): List<MemberDisplayInfo> {
        return clazz.interfaces.map { intf ->
            MemberDisplayInfo(
                displayTitle = intf.simpleName,
                displaySubtitle = intf.name,
                copyTextForMenu = intf.name,
                detailItems = listOf(context.getString(R.string.java_api_full_class_name) to intf.name),
                searchableText = intf.name,
                isClassReference = true
            )
        }
    }

    private fun getInnerClasses(context: Context, clazz: Class<*>): List<MemberDisplayInfo> {
        return clazz.declaredClasses.map { inner ->
            MemberDisplayInfo(
                displayTitle = inner.simpleName,
                displaySubtitle = inner.name,
                copyTextForMenu = inner.name,
                detailItems = listOf(context.getString(R.string.java_api_full_class_name) to inner.name),
                searchableText = inner.name,
                isClassReference = true
            )
        }
    }

    private fun buildSignature(method: Method): String {
        val paramTypes = method.parameterTypes.joinToString(",") { it.name }
        return "${method.name}($paramTypes)${method.returnType.name}"
    }

    private fun isSyntheticOrInner(name: String): Boolean {
        return name.contains("$") && name.count { it == '$' } >= 2
    }

    private fun buildDetailItems(map: Map<String, String>): List<Pair<String, String>> {
        return map.map { it.key to it.value }
    }
}