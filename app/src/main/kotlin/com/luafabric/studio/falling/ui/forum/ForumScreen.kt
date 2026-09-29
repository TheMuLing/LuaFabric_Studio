package com.luafabric.studio.falling.ui.forum

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.draw.scale
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
import kotlinx.coroutines.delay
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
    loggedInUser: YunJuResponse?,
    /** 未登录点击互动时跳转账户界面 */
    onRequireLogin: () -> Unit,
    /** 打开发帖页（独立界面，由 MainScreen 全屏覆盖层承载） */
    onOpenCompose: () -> Unit,
    /** 打开帖子详情页（独立界面，由 MainScreen 全屏覆盖层承载） */
    onOpenDetail: (ForumItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var layer1Index by remember { mutableIntStateOf(0) }
    // 第一层切换时第二层回到首个分类
    var layer2Index by remember(layer1Index) { mutableIntStateOf(0) }
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
                                    toast = toast,
                                    onRequireLogin = onRequireLogin,
                                    onOpenDetail = { onOpenDetail(post) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 右下角发帖 FAB：复制创建项目 FAB——列表到顶展开显示文字，滚动后收起，停靠导航栏上方
        ExtendedFloatingActionButton(
            onClick = onOpenCompose,
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
 * 论坛独立界面（发帖页 / 帖子详情页）：不再嵌入论坛导航页内，
 * 由 MainScreen 以全屏覆盖层承载，覆盖底部导航与顶栏，返回后回到论坛列表。
 */
sealed interface ForumOverlay {
    data object Compose : ForumOverlay
    data class Detail(val post: ForumItem) : ForumOverlay
}

/**
 * 帖子卡片（朋友圈式）：头像 + 昵称 + 日期 + 标题 + 正文节选 + 配图 + 点赞/评论/收藏。
 * 点击卡片或评论按钮进入详情页；点赞/收藏为乐观反馈（本地填充 + 脉冲动画），
 * 未登录点击互动弹提示并跳转账户界面。
 */
@Composable
private fun ForumPostCard(
    post: ForumItem,
    activeUser: YunJuResponse?,
    toast: NonBlockingToastState,
    onRequireLogin: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = SettingsManager.currentSettings
    val identity = forumIdentity(activeUser)
    // 本卡片点赞/收藏本地乐观态（后端无状态查询，纯反馈型）
    val praised = remember(post.postId) { mutableStateOf(false) }
    val favored = remember(post.postId) { mutableStateOf(false) }

    fun guard(): Boolean {
        if (activeUser == null) {
            toast.showToast(context.getString(R.string.forum_need_login))
            onRequireLogin()
            return false
        }
        return true
    }

    // 点赞/取消：Praise.php 为 toggle 接口（qq=操作者）；未登录拦截，失败回滚本地状态
    fun doPraise() {
        if (!guard()) return
        val next = !praised.value
        praised.value = next
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ForumRepository.praise(context, post.postId, identity.user, identity.qq)
            }
            if (!ok) praised.value = !next
            toast.showToast(
                context.getString(if (ok) R.string.forum_praise_ok else R.string.forum_action_fail)
            )
        }
    }

    // 收藏/取消收藏：Follow.php 为 toggle 接口（qq=操作者）；未登录拦截，失败回滚本地状态
    fun doFollow() {
        if (!guard()) return
        val next = !favored.value
        favored.value = next
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ForumRepository.follow(context, post.postId, identity.user, identity.qq)
            }
            if (!ok) favored.value = !next
            toast.showToast(
                context.getString(if (ok) R.string.forum_follow_ok else R.string.forum_action_fail)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
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

        // 动作条：居右，顺序 点赞 / 评论 / 收藏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            PostAction(
                icon = HeartOutlineIcon,
                filledIcon = Icons.Filled.Favorite,
                label = stringResource(R.string.forum_praise),
                active = praised.value,
                onClick = { doPraise() }
            )
            PostAction(
                icon = CommentOutlineIcon,
                filledIcon = null,
                label = stringResource(R.string.forum_comment),
                active = false,
                onClick = onOpenDetail
            )
            PostAction(
                icon = BookmarkOutlineIcon,
                filledIcon = Icons.Filled.Bookmark,
                label = stringResource(R.string.forum_follow),
                active = favored.value,
                onClick = { doFollow() }
            )
        }
    }
}

/** 帖子头像：只显示 QQ 头像（qlogo）；qq 无效或「隐藏非己头像」开启时显示纯色占位（无文字头像） */
@Composable
private fun Avatar(qq: String, nickname: String, hideAvatar: Boolean, size: Dp) {
    val placeholder = Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
    if (hideAvatar || qq.isBlank()) {
        placeholder
    } else {
        SubcomposeAsyncImage(
            model = ForumRepository.avatarUrl(qq),
            contentDescription = nickname,
            contentScale = ContentScale.Crop,
            loading = { placeholder },
            error = { placeholder },
            modifier = Modifier.size(size).clip(CircleShape)
        )
    }
}

/**
 * 动作条单项：图标 + 文字（点击波纹）。
 * 传入 filledIcon 的项激活时填充图标 + 主题色，并伴有脉冲放大动画（点赞/收藏反馈）。
 */
@Composable
private fun PostAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filledIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    var pulse by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pulse) 1.35f else 1f,
        animationSpec = tween(280),
        label = "post_action_scale"
    )
    LaunchedEffect(pulse) {
        if (pulse) {
            delay(280)
            pulse = false
        }
    }
    val tint = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(themeRadius()))
            .clickable(onClick = {
                if (filledIcon != null) pulse = true
                onClick()
            })
            .scale(scale)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (active && filledIcon != null) filledIcon else icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
    }
}

/**
 * 帖子详情页：完整内容 + 评论区，独立界面由 MainScreen 全屏承载。
 * 点赞/收藏/评论未登录时弹提示并跳转账户界面。
 */
@Composable
internal fun ForumPostDetailScreen(
    post: ForumItem,
    activeUser: YunJuResponse?,
    toast: NonBlockingToastState,
    onRequireLogin: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = SettingsManager.currentSettings
    val identity = forumIdentity(activeUser)
    val praised = remember(post.postId) { mutableStateOf(false) }
    val favored = remember(post.postId) { mutableStateOf(false) }
    val commentText = remember(post.postId) { mutableStateOf("") }
    val commentsRaw = remember(post.postId) { mutableStateOf<JsonArray?>(null) }
    val commentsLoading = remember(post.postId) { mutableStateOf(false) }

    // 进入详情页即拉取评论列表
    LaunchedEffect(post.postId) {
        commentsLoading.value = true
        val raw = withContext(Dispatchers.IO) {
            ForumRepository.loadComments(context, post.postId, identity.user)
        }
        commentsLoading.value = false
        commentsRaw.value = parseCommentsArray(raw)
    }

    fun guard(): Boolean {
        if (activeUser == null) {
            toast.showToast(context.getString(R.string.forum_need_login))
            onRequireLogin()
            return false
        }
        return true
    }

    fun doPraise() {
        if (!guard()) return
        val next = !praised.value
        praised.value = next
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ForumRepository.praise(context, post.postId, identity.user, identity.qq)
            }
            if (!ok) praised.value = !next
            toast.showToast(
                context.getString(if (ok) R.string.forum_praise_ok else R.string.forum_action_fail)
            )
        }
    }

    fun doFollow() {
        if (!guard()) return
        val next = !favored.value
        favored.value = next
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ForumRepository.follow(context, post.postId, identity.user, identity.qq)
            }
            if (!ok) favored.value = !next
            toast.showToast(
                context.getString(if (ok) R.string.forum_follow_ok else R.string.forum_action_fail)
            )
        }
    }

    fun sendComment() {
        if (!guard()) return
        val text = commentText.value.trim()
        if (text.isEmpty()) return
        commentText.value = ""
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ForumRepository.comment(
                    context, post.postId, identity.user, identity.qq, identity.nickname, text
                )
            }
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
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶栏：返回箭头 + 帖子标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null
                )
            }
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "post") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (post.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = post.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (post.img.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SubcomposeAsyncImage(
                            model = post.img,
                            contentDescription = post.title,
                            contentScale = ContentScale.Crop,
                            loading = { Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                            error = {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
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
                                .height(220.dp)
                                .clip(RoundedCornerShape(themeRadius()))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 动作条：居右，顺序 点赞 / 评论 / 收藏（与列表一致）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        PostAction(
                            icon = HeartOutlineIcon,
                            filledIcon = Icons.Filled.Favorite,
                            label = stringResource(R.string.forum_praise),
                            active = praised.value,
                            onClick = { doPraise() }
                        )
                        PostAction(
                            icon = CommentOutlineIcon,
                            filledIcon = null,
                            label = stringResource(R.string.forum_comment),
                            active = false,
                            onClick = {}
                        )
                        PostAction(
                            icon = BookmarkOutlineIcon,
                            filledIcon = Icons.Filled.Bookmark,
                            label = stringResource(R.string.forum_follow),
                            active = favored.value,
                            onClick = { doFollow() }
                        )
                    }
                }
            }

            item(key = "comments") {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
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

/** 发帖编辑页：标题 + 正文 + 提交按钮（发帖接口待接入），独立界面由 MainScreen 全屏承载 */
@Composable
internal fun ForumComposeScreen(
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