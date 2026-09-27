package com.luafabric.studio.falling.ui.forum

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import muling.views.tool.utils.NonBlockingToastState

/** 源码实例 第二层分类：默认「全部」，7 类两字扩写为四字 + 「其他」 */
private val SOURCE_CATEGORIES =
    listOf("全部", "控件组件", "动画效果", "布局导航", "网络传输", "安全加密", "系统设备", "媒体处理", "其他")

/** 完整项目 第二层分类 */
private val PROJECT_CATEGORIES =
    listOf("全部", "社区论坛", "工具", "外挂", "病毒", "其他")

/** 圆角跟随 luafabric 主题设置 */
private fun themeRadius(): Dp = when (SettingsManager.currentSettings.shapeSizeIndex) {
    0 -> 4.dp
    1 -> 8.dp
    2 -> 12.dp
    3 -> 16.dp
    else -> 12.dp
}

/** 源码论坛：搜索框 + 双层 tabs + 帖子列表 + 发帖 FAB */
@Composable
fun ForumScreen(toast: NonBlockingToastState) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var layer1Index by remember { mutableIntStateOf(0) }
    // 第一层切换时第二层回到首个分类
    var layer2Index by remember(layer1Index) { mutableIntStateOf(0) }
    var posts by remember { mutableStateOf<List<ForumItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCompose by remember { mutableStateOf(false) }

    val layer1Tabs = listOf(
        stringResource(R.string.forum_source),
        stringResource(R.string.forum_project)
    )
    val secondLayer = if (layer1Index == 0) SOURCE_CATEGORIES else PROJECT_CATEGORIES
    val category = secondLayer[layer2Index.coerceIn(secondLayer.indices)]

    // 板块 ID：第一层「源码实例」=1，「完整项目」=2（云居后端按板块发帖）
    val layer1ForumIds = listOf(1, 2)

    LaunchedEffect(layer1Index) {
        loading = true
        posts = withContext(Dispatchers.IO) {
            ForumRepository.loadPosts(context, layer1ForumIds[layer1Index.coerceIn(layer1ForumIds.indices)])
        }
        loading = false
    }

    // 帖子过滤：搜索（标题/作者/正文）+ 当前分类
    val filtered = remember(posts, searchQuery, layer1Index, layer2Index) {
        val q = searchQuery.trim()
        val specifics = secondLayer.filter { it != "全部" && it != "其他" }
        posts.filter { post ->
            val inSearch = q.isEmpty() ||
                post.title.contains(q, ignoreCase = true) ||
                post.nickname.contains(q, ignoreCase = true) ||
                post.content.contains(q, ignoreCase = true)
            if (!inSearch) return@filter false
            val hay = post.forumName + post.title + post.content
            when (category) {
                "全部" -> true
                "其他" -> !specifics.any { hay.contains(it, ignoreCase = true) }
                else -> hay.contains(category, ignoreCase = true)
            }
        }
    }

    if (showCompose) {
        ForumComposeScreen(
            toast = toast,
            onBack = { showCompose = false }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 搜索框：圆角跟随主题，search 前导图标 + Tune 末尾图标
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.forum_search_hint)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { /* 过滤功能暂未接入 */ }) {
                        Icon(
                            Icons.Filled.FilterAlt,
                            contentDescription = stringResource(R.string.forum_filter),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(themeRadius()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 第一层 tabs：源码实例 / 完整项目（tablayout 同款）
            ScrollableTabRow(
                selectedTabIndex = layer1Index,
                edgePadding = 16.dp,
                divider = {}
            ) {
                layer1Tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = i == layer1Index,
                        onClick = { layer1Index = i },
                        text = { Text(label) }
                    )
                }
            }
            // 第一层 tabs 下方细分割线
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            // 第二层 tabs：依第一层选择展示不同分类
            ScrollableTabRow(
                selectedTabIndex = layer2Index.coerceIn(secondLayer.indices),
                edgePadding = 16.dp,
                divider = {}
            ) {
                secondLayer.forEachIndexed { i, label ->
                    Tab(
                        selected = i == layer2Index,
                        onClick = { layer2Index = i },
                        text = { Text(label) }
                    )
                }
            }
            // 第二层 tabs 下方细分割线
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                filtered.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.forum_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered) { post ->
                            ForumPostCard(post)
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }

        // 右下角发帖 FAB：圆角跟随主题
        ExtendedFloatingActionButton(
            onClick = { showCompose = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.forum_post)) },
            shape = RoundedCornerShape(themeRadius()),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

/** 帖子条目：标题 + 作者 + 时间（点击暂无详情） */
@Composable
private fun ForumPostCard(post: ForumItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* 详情暂未接入 */ }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = post.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = post.nickname.ifBlank { post.qq },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = post.createTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 发帖编辑页骨架：标题 + 正文 + 提交按钮（暂不接接口） */
@Composable
private fun ForumComposeScreen(
    toast: NonBlockingToastState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.login_back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.forum_post),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.forum_compose_title)) },
            singleLine = true,
            shape = RoundedCornerShape(themeRadius()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text(stringResource(R.string.forum_compose_content)) },
            shape = RoundedCornerShape(themeRadius()),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Button(
            onClick = { toast.showToast(context.getString(R.string.forum_compose_wip)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.forum_submit))
        }
    }
}
