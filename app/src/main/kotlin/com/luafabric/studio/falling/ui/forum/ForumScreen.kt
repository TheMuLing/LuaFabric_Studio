package com.luafabric.studio.falling.ui.forum

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.icons.BookmarkOutlineIcon
import com.luafabric.studio.falling.ui.icons.CommentOutlineIcon
import com.luafabric.studio.falling.ui.icons.HeartOutlineIcon
import com.luafabric.studio.falling.ui.login.YunJuResponse
import com.luafabric.studio.falling.ui.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import muling.views.tool.utils.NonBlockingToastState
import muling.views.tool.utils.TransitionUtil

/** 源码实例 第二层分类：默认「全部」，7 类两字扩写为四字 + 「其他」 */
private val SOURCE_CATEGORIES =
    listOf("全部", "控件组件", "动画效果", "布局导航", "网络传输", "安全加密", "系统设备", "媒体处理", "其他")

/** 完整项目 第二层分类 */
private val PROJECT_CATEGORIES =
    listOf("全部", "社区论坛", "工具", "外挂", "病毒", "其他")

/** 互动操作身份兜底：未登录时以管理员账号参与互动 */
private const val FORUM_ADMIN = "3445352175"

/** 圆角跟随 luafabric 主题设置 */
private fun themeRadius(): Dp = when (SettingsManager.currentSettings.shapeSizeIndex) {
    0 -> 4.dp
    1 -> 8.dp
    2 -> 12.dp
    3 -> 16.dp
    else -> 12.dp
}

/** 源码论坛：搜索框 + 双层 tabs + 帖子列表 + 发帖 FAB */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumScreen(
    toast: NonBlockingToastState,
    listState: LazyListState,
    postsCache: Map<Int, List<ForumItem>>,
    isRefreshing: Boolean,
    onEnsureLoaded: (Int) -> Unit,
    onRefresh: (Int) -> Unit,
    fabGap: Dp,
    /** 当前登录用户（未登录为 null，互动以管理员兜底） */
    loggedInUser: YunJuResponse?
) {
    var searchQuery by remember { mutableStateOf("") }
    var layer1Index by remember { mutableIntStateOf(0) }
    // 第一层切换时第二层回到首个分类
    var layer2Index by remember(layer1Index) { mutableIntStateOf(0) }
    var showCompose by remember { mutableStateOf(false) }
    // 发帖 FAB 展开态：列表到顶显示文字，滚动后收起（复制自创建项目 FAB）
    var showExtendedFab by remember { mutableStateOf(true) }

    val layer1Tabs = listOf(
        stringResource(R.string.forum_source),
        stringResource(R.string.forum_project)
    )
    val secondLayer = if (layer1Index == 0) SOURCE_CATEGORIES else PROJECT_CATEGORIES
    val category = secondLayer[layer2Index.coerceIn(secondLayer.indices)]

    // 板块 ID：第一层「源码实例」=1，「完整项目」=2（云居后端按板块发帖）
    val layer1ForumIds = listOf(1, 2)
    val layer1ForumId = layer1ForumIds[layer1Index.coerceIn(layer1ForumIds.indices)]
    // 帖子数据来自外层缓存：板块无缓存才请求加载（有缓存直接用，切换导航页不重复请求）
    val posts = postsCache[layer1ForumId] ?: emptyList()
    val isLoading = !postsCache.containsKey(layer1ForumId)

    LaunchedEffect(layer1ForumId) { onEnsureLoaded(layer1ForumId) }

    // 发帖 FAB 展开态跟随列表滚动：到顶显示文字，滚动后收起（复制自创建项目 FAB）
    LaunchedEffect(
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset
    ) {
        val isScrolled = listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 0
        showExtendedFab = !isScrolled
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
    // 埋点：区分「后端未返回帖子」与「代码过滤导致不显示」
    LaunchedEffect(posts.size, filtered.size, isLoading, layer1ForumId, searchQuery) {
        android.util.Log.i(
            "ForumLoad",
            "page: forumId=$layer1ForumId posts=${posts.size} filtered=${filtered.size} " +
                "isLoading=$isLoading 空因=${if (isLoading) "加载中" else if (posts.isEmpty()) "后端未返回" else if (filtered.isEmpty()) "代码过滤置空" else "有帖"}"
        )
    }

    // 互动身份：优先登录用户，未登录以管理员兜底
    val activeUser = loggedInUser

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
            // 第一层 tabs：源码实例 / 完整项目（tablayout 同款、均分）
            TabRow(
                selectedTabIndex = layer1Index,
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

            // 搜索框：位于两层 tabs 全部下方、列表上方；圆角跟随主题，search 前导图标 + 筛选末尾图标
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

            when {
                isLoading -> {
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
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { onRefresh(layer1ForumId) }
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            items(filtered, key = { it.postId }) { post ->
                                ForumPostCard(
                                    post = post,
                                    activeUser = activeUser,
                                    toast = toast
                                )
                            }
                        }
                    }
                }
            }
        }

        // 右下角发帖 FAB：复制创建项目 FAB——列表到顶展开显示文字，滚动后收起，停靠导航栏上方
        ExtendedFloatingActionButton(
            onClick = { showCompose = true },
            icon = {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.forum_post),
                    modifier = Modifier.size(24.dp)
                )
            },
            text = {
                AnimatedVisibility(
                    visible = showExtendedFab,
                    enter = TransitionUtil.createFABTransition(),
                    exit = TransitionUtil.createFABExitTransition()
                ) {
                    Text(stringResource(R.string.forum_post))
                }
            },
            expanded = showExtendedFab,
            shape = RoundedCornerShape(themeRadius()),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp)
                .padding(bottom = fabGap)
        )
    }
}

/** 互动身份：user 恒为管理员，qq/nickname 优先登录用户、未登录以管理员兜底 */
private data class ForumIdentity(val user: String, val qq: String, val nickname: String)

private fun forumIdentity(activeUser: YunJuResponse?): ForumIdentity {
    val qq = activeUser?.qq?.takeIf { it.isNotBlank() } ?: FORUM_ADMIN
    val nick = activeUser?.name?.takeIf { it.isNotBlank() } ?: FORUM_ADMIN
    return ForumIdentity(FORUM_ADMIN, qq, nick)
}

/**
 * 帖子卡片（朋友圈式）：头像 + 昵称 + 日期 + 标题 + 正文节选 + 配图 + 收藏/点赞/评论。
 * 评论内联展开：评论列表 + 输入框 + 回复。
 */
@Composable
private fun ForumPostCard(
    post: ForumItem,
    activeUser: YunJuResponse?,
    toast: NonBlockingToastState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = SettingsManager.currentSettings
    val identity = forumIdentity(activeUser)
    // 本卡片互动状态（收藏/点赞不持久态，纯反馈型；评论展开独立）
    val showComments = remember(post.postId) { mutableStateOf(false) }
    // 评论输入
    val commentText = remember(post.postId) { mutableStateOf("") }
    // 评论列表（宽松解析数据缓存：原始 JSON 数组）
    val commentsRaw = remember(post.postId) { mutableStateOf<JsonArray?>(null) }
    val commentsLoading = remember(post.postId) { mutableStateOf(false) }

    // 点击收藏：Follow.php（qq=操作者）
    fun doFollow() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ForumRepository.follow(context, post.postId, identity.user, identity.qq)
            }
            toast.showToast(
                context.getString(if (ok) R.string.forum_follow_ok else R.string.forum_action_fail)
            )
        }
    }

    // 点击点赞：Praise.php（qq=操作者）
    fun doPraise() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ForumRepository.praise(context, post.postId, identity.user, identity.qq)
            }
            toast.showToast(
                context.getString(if (ok) R.string.forum_praise_ok else R.string.forum_action_fail)
            )
        }
    }

    // 展开评论：加载 CommentList（数据字段先埋点确认，宽松展示）
    fun loadComments() {
        showComments.value = !showComments.value
        if (showComments.value && commentsRaw.value == null) {
            commentsLoading.value = true
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    ForumRepository.loadComments(context, post.postId, identity.user)
                }
                commentsLoading.value = false
                commentsRaw.value = parseCommentsArray(raw)
            }
        }
    }

    // 发表评论：Comment.php
    fun sendComment() {
        val text = commentText.value.trim()
        if (text.isEmpty()) return
        commentText.value = ""
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ForumRepository.comment(
                    context, post.postId, identity.user, identity.qq, identity.nickname, text
                )
            }
            // 提交成功后重新拉取评论列表
            if (ok) {
                val raw = withContext(Dispatchers.IO) {
                    ForumRepository.loadComments(context, post.postId, identity.user)
                }
                commentsRaw.value = parseCommentsArray(raw)
            }
            toast.showToast(
                context.getString(if (ok) R.string.forum_comment_ok else R.string.forum_action_fail)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 头像 + 昵称 + 日期（相对日期开关控制显示格式）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                qq = post.qq,
                nickname = post.nickname,
                hideAvatar = settings.forumHideOtherAvatars &&
                    post.qq != identity.qq,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.nickname.ifBlank { post.qq },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (settings.forumRelativeDate) {
                        ForumRepository.formatRelativeTime(post.createTime)
                    } else {
                        post.createTime
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 标题
        Text(
            text = post.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // 正文节选
        if (post.content.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 配图：受「快速帖子列表」开关控制（隐藏其他用户的帖子配图）
        if (post.img.isNotBlank() &&
            !(settings.forumHideOtherImages && post.qq != identity.qq)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SubcomposeAsyncImage(
                model = post.img,
                contentDescription = post.title,
                contentScale = ContentScale.Crop,
                loading = { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                error = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(themeRadius()))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.forum_img_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(themeRadius()))
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 动作条：收藏 / 点赞 / 评论
        Row(modifier = Modifier.fillMaxWidth()) {
            PostAction(
                icon = BookmarkOutlineIcon,
                label = stringResource(R.string.forum_follow),
                onClick = { doFollow() }
            )
            PostAction(
                icon = HeartOutlineIcon,
                label = stringResource(R.string.forum_praise),
                onClick = { doPraise() }
            )
            PostAction(
                icon = CommentOutlineIcon,
                label = stringResource(R.string.forum_comment),
                onClick = { loadComments() }
            )
        }

        // 评论内联展开帧
        if (showComments.value) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            CommentSection(
                post = post,
                identity = identity,
                toast = toast,
                raw = commentsRaw.value,
                loading = commentsLoading.value,
                input = commentText.value,
                onInputChange = { commentText.value = it },
                onSend = { sendComment() }
            )
        }
    }
}

/** 帖子头像：QQ 头像（qlogo）；「隐藏非己头像」开启且非本人时降级首字色块 */
@Composable
private fun Avatar(qq: String, nickname: String, hideAvatar: Boolean, size: Dp) {
    val fallback = Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = nickname.take(1).takeIf { it.isNotBlank() } ?: "·",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
    if (hideAvatar || qq.isBlank()) {
        fallback
    } else {
        SubcomposeAsyncImage(
            model = ForumRepository.avatarUrl(qq),
            contentDescription = nickname,
            contentScale = ContentScale.Crop,
            loading = { fallback },
            error = { fallback },
            modifier = Modifier.size(size).clip(CircleShape)
        )
    }
}

/** 动作条单项：图标 + 文字（点击波纹） */
@Composable
private fun PostAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(themeRadius()))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 评论内联区：列表 + 输入框 + 发送（回复入口暂并入列表项，字段确认后精化） */
@Composable
private fun CommentSection(
    post: ForumItem,
    identity: ForumIdentity,
    toast: NonBlockingToastState,
    raw: JsonArray?,
    loading: Boolean,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 回复状态：正在回复的评论在列表中的下标（null=未在回复）
    val replyingIndex = remember(post.postId) { mutableStateOf(-1) }
    val replyText = remember(post.postId) { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.forum_comment_list),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        when {
            loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.forum_comment_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            raw == null || raw.size() == 0 -> {
                Text(
                    text = stringResource(R.string.forum_comment_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                // 评论列表（宽松渲染：字段名与后端响应一致，缺失则跳过；commit_id 埋点中）
                raw.forEachIndexed { index, element ->
                    val obj = element.asJsonObject
                    // 字段名：CommentList data 待真机确认，先按常见候选读取，缺失即跳过
                    val nick = obj.stringOr("nickname", "name", "user") ?: "用户"
                    val content = obj.stringOr("content", "text") ?: return@forEachIndexed
                    val time = obj.stringOr("create_time", "time", "date") ?: ""
                    val cid = obj.longOr("comment_id", "id", "cid") ?: index.toLong()

                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = nick.take(1),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = nick,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (time.isNotBlank()) {
                                    Text(
                                        text = time,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    replyingIndex.value = if (replyingIndex.value == index) -1 else index
                                    replyText.value = ""
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.forum_reply),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        // 回复输入行：针对该条评论
                        if (replyingIndex.value == index) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = replyText.value,
                                    onValueChange = { replyText.value = it },
                                    placeholder = {
                                        Text(stringResource(R.string.forum_reply_hint))
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(themeRadius()),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        val text = replyText.value.trim()
                                        if (text.isEmpty()) return@Button
                                        replyText.value = ""
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) {
                                                ForumRepository.commentReply(
                                                    context, post.postId, cid,
                                                    identity.user, identity.qq, identity.nickname, text
                                                )
                                            }
                                            toast.showToast(
                                                context.getString(
                                                    if (ok) R.string.forum_reply_ok else R.string.forum_action_fail
                                                )
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(themeRadius())
                                ) {
                                    Text(stringResource(R.string.forum_reply_send))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 评论输入框 + 发送
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text(stringResource(R.string.forum_comment_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(themeRadius()),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Button(
                onClick = onSend,
                shape = RoundedCornerShape(themeRadius()),
                enabled = input.isNotBlank()
            ) {
                Text(stringResource(R.string.forum_comment_send))
            }
        }
    }
}

/** 宽松字段读取：按候选字段名逐个取第一个非空字符串 */
private fun JsonObject.stringOr(vararg names: String): String? {
    for (n in names) {
        val e = this.get(n)
        if (e != null && !e.isJsonNull) {
            val s = e.asString
            if (s.isNotBlank()) return s
        }
    }
    return null
}

/** 宽松字段读取：按候选字段名逐个取第一个数字（评论 ID） */
private fun JsonObject.longOr(vararg names: String): Long? {
    for (n in names) {
        val e = this.get(n)
        if (e != null && !e.isJsonNull && e.isJsonPrimitive) {
            runCatching { return e.asLong }
        }
    }
    return null
}

/** 将 CommentList 裁剪 JSON 解析为宽松 JsonArray（data 字段或多个对象兜底） */
private fun parseCommentsArray(json: String?): JsonArray? {
    if (json == null) return null
    return try {
        val el = com.google.gson.JsonParser.parseString(json)
        val data = el.asJsonObject.get("data")
        when {
            data != null && data.isJsonArray -> data.asJsonArray
            data != null && data.isJsonObject ->
                JsonArray().apply { add(data) }
            el.isJsonArray -> el.asJsonArray
            else -> null
        }
    } catch (e: Exception) {
        android.util.Log.i("ForumComment", "评论 JSON 解析失败：$e")
        null
    }
}

/** 发帖编辑页：标题 + 正文 + 提交按钮（发帖接口待接入） */
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