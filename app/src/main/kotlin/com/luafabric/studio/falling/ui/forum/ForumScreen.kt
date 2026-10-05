package com.luafabric.studio.falling.ui.forum

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

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
 * 论坛独立界面（发帖页）：不再嵌入论坛导航页内，
 * 由 MainScreen 以全屏覆盖层承载，覆盖底部导航与顶栏，返回后回到论坛列表。
 * 帖子详情页已独立为 ForumPostDetailActivity。
 */
sealed interface ForumOverlay {
    data object Compose : ForumOverlay
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

    // 点赞/取消：Praise.php 为 toggle 接口（qq=操作者）；未登录拦截，失败回滚本地状态；无 toast
    fun doPraise() {
        if (!guard()) return
        val next = !praised.value
        praised.value = next
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ForumRepository.praise(context, post.postId, identity.user, identity.qq)
            }
            if (!ok) praised.value = !next
        }
    }

    // 收藏/取消收藏：Follow.php 为 toggle 接口（qq=操作者）；未登录拦截，失败回滚本地状态；无 toast
    fun doFollow() {
        if (!guard()) return
        val next = !favored.value
        favored.value = next
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ForumRepository.follow(context, post.postId, identity.user, identity.qq)
            }
            if (!ok) favored.value = !next
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
                text = ForumRepository.stripCategoryMarker(post.content),
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

/** 帖子头像：只显示 QQ 头像（qlogo）；隐藏非己头像开启时透明留空（不显示纯色占位圆）；
 * 仅 qq 缺失（无号可查）时才以纯色圆兜底。 */
@Composable
private fun Avatar(qq: String, nickname: String, hideAvatar: Boolean, size: Dp) {
    // 占位圆：必须用 @Composable lambda（直接 val=Box(...) 会在 if/else 前恒定进场，
    // 与成功后头像图共存 → 两个圆重叠/并排 —— 论坛双头像根因）
    val placeholder: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
    if (hideAvatar) {
        // 隐藏非己头像：位置留空保持对齐，不渲染任何头像
        Box(modifier = Modifier.size(size))
    } else if (qq.isBlank()) {
        placeholder()
    } else {
        SubcomposeAsyncImage(
            model = ForumRepository.avatarUrl(qq),
            contentDescription = nickname,
            contentScale = ContentScale.Crop,
            loading = { placeholder() },
            error = { placeholder() },
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
@OptIn(ExperimentalLayoutApi::class)
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
    // 发送中：禁用编辑框 + 发送图标换环形加载指示器，接口返回后复位
    var sending by remember(post.postId) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 回复目标（被点击的评论 id + 昵称，null=普通评论）；软键盘关闭/回复发出后复位
    var replyTarget by remember(post.postId) { mutableStateOf<Pair<Long, String>?>(null) }
    // 待锚定的评论 id：点击某条评论后把该条滚动到完全可见
    var pendingAnchorId by remember(post.postId) { mutableStateOf<Long?>(null) }
    val commentInputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // 软键盘关闭 → hint 恢复初始
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (!imeVisible) replyTarget = null
    }

    // 评论行拍平（顶层评论 + 其嵌套回复），供 LazyColumn 按条渲染与按 id 锚定
    val commentRows = remember(commentsRaw.value) { buildCommentRows(commentsRaw.value) }
    // LazyColumn 前导 item 数：0=帖子内容，1=评论标题 → 评论行从 index 2 起
    val commentRowLead = 2

    // 进入详情页即拉取评论列表
    LaunchedEffect(post.postId) {
        commentsLoading.value = true
        val raw = withContext(Dispatchers.IO) {
            ForumRepository.loadComments(context, post.postId, identity.user)
        }
        commentsLoading.value = false
        commentsRaw.value = parseCommentsArray(raw)
    }

    // 点击评论 → 键盘弹出后把该条锚定到列表顶（保证完整可见，不被底部输入栏遮挡）
    LaunchedEffect(imeVisible, pendingAnchorId, commentRows.size) {
        val id = pendingAnchorId ?: return@LaunchedEffect
        if (!imeVisible) return@LaunchedEffect
        delay(150) // 等 IME 动画与列表重排完成
        val idx = commentRows.indexOfFirst { it.entry.id == id }
        if (idx >= 0) listState.animateScrollToItem(commentRowLead + idx)
        pendingAnchorId = null
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
        }
    }

    fun sendComment() {
        if (!guard()) return
        if (sending) return
        val text = commentText.value.trim()
        if (text.isEmpty()) return
        commentText.value = ""
        val target = replyTarget
        replyTarget = null // 回复发出 → hint 恢复初始
        sending = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (target != null) {
                    // 点击评论/回复聚焦底部框 → 发的是针对该条的回复
                    ForumRepository.commentReply(
                        context, post.postId, target.first,
                        identity.user, identity.qq, identity.nickname, text
                    )
                } else {
                    ForumRepository.comment(
                        context, post.postId, identity.user, identity.qq, identity.nickname, text
                    )
                }
            }
            if (ok) {
                val raw = withContext(Dispatchers.IO) {
                    ForumRepository.loadComments(context, post.postId, identity.user)
                }
                val parsed = parseCommentsArray(raw)
                // 解析失败时保留旧列表，绝不把 commentsRaw 置 null（否则整个列表消失）
                if (parsed != null) commentsRaw.value = parsed
                toast.showToast(
                    context.getString(
                        if (parsed != null) R.string.forum_comment_ok
                        else R.string.forum_comment_refresh_fail
                    )
                )
            } else {
                toast.showToast(context.getString(R.string.forum_action_fail))
            }
            sending = false // 接口返回 → 编辑框与发送图标复位
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding() // 软键盘弹出时整体上移，底部固定评论栏随之顶起
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
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
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

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
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
                            text = ForumRepository.stripCategoryMarker(post.content),
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

            item(key = "comments_header") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    Text(
                        text = stringResource(R.string.forum_comment_list),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (commentsLoading.value) {
                item(key = "comments_loading") {
                    CommentPlaceholder(text = stringResource(R.string.forum_comment_loading), loading = true)
                }
            } else if (commentRows.isEmpty()) {
                item(key = "comments_empty") {
                    CommentPlaceholder(text = stringResource(R.string.forum_comment_empty), loading = false)
                }
            } else {
                items(commentRows, key = { "c_${it.entry.id}" }) { row ->
                    CommentRowView(
                        row = row,
                        identity = identity,
                        hideOtherAvatars = settings.forumHideOtherAvatars,
                        relativeDate = settings.forumRelativeDate,
                        onCommentClick = { entry ->
                            // 点击评论 → hint 置「回复@昵称」+ 聚焦底部输入框 + 键盘弹出 + 锚定该条
                            replyTarget = entry.id to entry.nickname
                            pendingAnchorId = entry.id
                            commentInputFocus.requestFocus()
                            keyboard?.show()
                        }
                    )
                }
                item(key = "comments_end") { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }

            // 底部边缘渐隐（4dp）：列表内容向底部输入栏方向淡出
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                        )
                    )
            )
        }

        // 底部固定评论输入栏：MD3 编辑框 + 图标发送按钮；navigationBarsPadding 防止延伸到系统导航栏下方
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
        MomentsCommentBar(
            input = commentText.value,
            onInputChange = { commentText.value = it },
            onSend = { sendComment() },
            sending = sending,
            replyTargetNick = replyTarget?.second,
            focusRequester = commentInputFocus
        )
    }
}

/** 评论条目（宽松解析 CommentList data 项） */
private data class CommentEntry(
    val id: Long,
    val qq: String,
    val nickname: String,
    val content: String,
    val time: String,
    val replyTo: Long
)

/** 评论行：顶层评论或某条嵌套回复。 */
private data class CommentRow(val entry: CommentEntry, val isReply: Boolean)

/**
 * 宽松解析 CommentList data 并按 reply_to 拍平成渲染顺序：
 * 顶层评论（reply_to==0 或父不存在）后紧跟其全部回复（回复之回复亦归入该顶层评论）。
 * 拍平后每条独立成一个 LazyColumn item，才能按 id 精确定位锚定。
 */
private fun buildCommentRows(raw: JsonArray?): List<CommentRow> {
    if (raw == null) return emptyList()
    val entries = buildList {
        for (el in raw) {
            if (!el.isJsonObject) continue
            val obj = el.asJsonObject
            val id = obj.longOr("comment_id", "id", "cid") ?: continue
            val content = obj.stringOr("content", "text") ?: continue
            add(
                CommentEntry(
                    id = id,
                    qq = obj.stringOr("qq", "author_qq", "user_qq", "uid").orEmpty(),
                    nickname = obj.stringOr("nickname", "name", "user") ?: "用户",
                    content = content,
                    time = obj.stringOr("create_time", "time", "date").orEmpty(),
                    replyTo = obj.longOr("reply_to", "replyTo", "pid") ?: 0L
                )
            )
        }
    }
    if (entries.isEmpty()) return emptyList()
    val byId = entries.associateBy { it.id }
    // 回复归到最顶层评论：沿 reply_to 上溯到根
    fun rootOf(e: CommentEntry): Long {
        var cur = e.replyTo
        val seen = HashSet<Long>()
        var last = cur
        while (cur != 0L && byId.containsKey(cur) && seen.add(cur)) {
            last = cur
            cur = byId.getValue(cur).replyTo
        }
        return last
    }
    val topComments = entries.filter { it.replyTo == 0L || it.replyTo !in byId }
    val repliesByRoot = entries
        .filter { it.replyTo != 0L && it.replyTo in byId }
        .groupBy { rootOf(it) }
    return buildList {
        topComments.forEach { top ->
            add(CommentRow(top, isReply = false))
            repliesByRoot[top.id].orEmpty().forEach { add(CommentRow(it, isReply = true)) }
        }
    }
}

/** 评论加载中 / 空态占位（左侧 16dp 边距，与评论行对齐）。 */
@Composable
private fun CommentPlaceholder(text: String, loading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (loading) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 单条评论/回复：整行点击波纹（莫奈取色）；点击聚焦底部输入框并把该条锚定到可见位置。 */
@Composable
private fun CommentRowView(
    row: CommentRow,
    identity: ForumIdentity,
    hideOtherAvatars: Boolean,
    relativeDate: Boolean,
    onCommentClick: (CommentEntry) -> Unit
) {
    val entry = row.entry
    val interaction = remember { MutableInteractionSource() }
    val rippleIndication = ripple(color = MaterialTheme.colorScheme.primary)
    val timeText = if (relativeDate) ForumRepository.formatRelativeTime(entry.time) else entry.time

    if (!row.isReply) {
        // 顶层评论：左侧 qlogo 头像；昵称/日期弱化为浅灰莫奈色，内容完整换行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = interaction, indication = rippleIndication) {
                    onCommentClick(entry)
                }
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Avatar(
                qq = entry.qq,
                nickname = entry.nickname,
                hideAvatar = hideOtherAvatars && entry.qq != identity.qq,
                size = 32.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.nickname,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (timeText.isNotBlank()) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    } else {
        // 嵌套回复：左侧 24dp qlogo 头像 + 「昵称：内容」，长内容完整换行不截断
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = interaction, indication = rippleIndication) {
                    onCommentClick(entry)
                }
                .padding(start = 40.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            Avatar(
                qq = entry.qq,
                nickname = entry.nickname,
                hideAvatar = hideOtherAvatars && entry.qq != identity.qq,
                size = 24.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            append(entry.nickname)
                            append("：")
                        }
                        append(entry.content)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true
                )
                if (timeText.isNotBlank()) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * 底部固定评论输入栏（MD3）：OutlinedTextField（支持换行）+ 图标发送按钮。
 * 输入为空 → 发送按钮不可用；发送中 → 编辑框禁用 + 图标换环形加载指示器，接口返回后复位。
 * hint 动态：点击评论后「回复@昵称」，回复发出/软键盘关闭复位为默认；
 * navigationBarsPadding 防止延伸到系统导航栏下方。
 */
@Composable
private fun MomentsCommentBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
    replyTargetNick: String?,
    focusRequester: FocusRequester
) {
    // 长度上限：超限仅红字提醒 + 禁用发送，不硬截断输入
    val maxLen = 1000
    val overLimit = input.length > maxLen
    val canSend = input.isNotBlank() && !sending && !overLimit
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            enabled = !sending,
            placeholder = {
                Text(
                    text = replyTargetNick?.let {
                        stringResource(R.string.forum_comment_hint_reply, it)
                    } ?: stringResource(R.string.forum_comment_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            // 软键盘发送键替换为换行：多行 + ImeAction.Default
            singleLine = false,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            textStyle = MaterialTheme.typography.bodyMedium,
            supportingText = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = stringResource(
                            R.string.forum_comment_len_count, input.length, maxLen
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (overLimit) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            },
            shape = RoundedCornerShape(themeRadius()),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.forum_comment_send),
                    tint = if (input.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
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

/**
 * 发帖编辑页（独立界面，由 MainScreen 全屏承载）：
 * 标题 + 详细分类下拉 + 正文 + 图片（相册选图 → 压缩 1920px → native 单次直传）→ 提交 Issue.php。
 * 提交正文自动在首行拼分类隐藏标志（JSON 单行，显示端剥离）；未登录拦截跳账户页。
 */
@Composable
internal fun ForumComposeScreen(
    toast: NonBlockingToastState,
    activeUser: YunJuResponse?,
    onRequireLogin: () -> Unit,
    onBack: () -> Unit,
    onPosted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    // 详细分类：发帖下拉（不含「全部」），默认首个具体分类
    val categories = remember { SOURCE_CATEGORIES.filter { it != "全部" } }
    var category by remember { mutableStateOf(categories.first()) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    // 图片：原始 Uri（预览）+ 压缩后本地文件（上传用）+ 原始文件名
    var imageFile by remember { mutableStateOf<File?>(null) }
    var imageName by remember { mutableStateOf("image.jpg") }
    var processing by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    /** 选图处理：压缩到最长边 1920px JPEG 质量 85，写入 cacheDir */
    fun onImagePicked(uri: Uri) {
        processing = true
        scope.launch {
            try {
                val displayName = runCatching {
                    context.contentResolver.query(
                        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                    )?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                }.getOrNull()
                val file = withContext(Dispatchers.IO) {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val longEdge = max(info.size.width, info.size.height)
                        if (longEdge > 1920) {
                            decoder.setTargetSampleSize(longEdge / 1920)
                        }
                    }
                    val longEdge = max(bmp.width, bmp.height)
                    val scale = if (longEdge > 1920) 1920f / longEdge else 1f
                    val decoded = if (scale < 1f) {
                        Bitmap.createScaledBitmap(
                            bmp,
                            (bmp.width * scale).roundToInt(),
                            (bmp.height * scale).roundToInt(),
                            true
                        )
                    } else {
                        bmp
                    }
                    if (decoded !== bmp) bmp.recycle()
                    val out = File(context.cacheDir, "forum_upload_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(out).use { os ->
                        decoded.compress(Bitmap.CompressFormat.JPEG, 85, os)
                    }
                    decoded.recycle()
                    out
                }
                imageFile = file
                // 文件名进入 multipart Content-Disposition 头，净化为纯 ASCII 防头注入
                val raw = displayName?.takeIf { it.isNotBlank() } ?: "image.jpg"
                imageName = raw.map { c ->
                    if (c in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-") c else '_'
                }.joinToString("").ifBlank { "image.jpg" }
            } catch (e: Exception) {
                android.util.Log.i("ForumCompose", "图片处理失败：$e")
                toast.showToast(context.getString(R.string.forum_compose_image_fail))
            } finally {
                processing = false
            }
        }
    }

    /** API<33：系统相册 Intent（先声明，权限回调内引用） */
    val legacyAlbumLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) onImagePicked(uri)
    }

    /** API<33：检查相册权限，无则弹窗申请，通过后调系统相册 */
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            legacyAlbumLauncher.launch(
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            )
        } else {
            toast.showToast(context.getString(R.string.forum_compose_no_permission))
        }
    }

    /** API≥33：系统 Photo Picker（免权限） */
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onImagePicked(uri)
    }

    fun pickImage() {
        if (processing || submitting) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                legacyAlbumLauncher.launch(
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                )
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    fun doSubmit() {
        if (activeUser == null) {
            toast.showToast(context.getString(R.string.forum_need_login))
            onRequireLogin()
            return
        }
        val t = title.trim()
        if (t.isEmpty()) {
            toast.showToast(context.getString(R.string.forum_compose_title_empty))
            return
        }
        if (submitting) return
        submitting = true
        val user = activeUser
        scope.launch {
            var imgUrl = ""
            val imgFile = imageFile
            if (imgFile != null) {
                imgUrl = withContext(Dispatchers.IO) {
                    ForumRepository.uploadImage(context, imgFile.absolutePath, imageName)
                }.orEmpty()
                if (imgUrl.isEmpty()) {
                    android.util.Log.i("ForumCompose", "图片上传失败（门控/网络/后端回绝）")
                    submitting = false
                    toast.showToast(context.getString(R.string.forum_upload_fail))
                    return@launch
                }
            }
            // 正文首行拼分类隐藏标志（显示端剥离），forum_id 恒 1（仅支持源码论坛）
            val payload = ForumRepository.withCategoryMarker(content.trim(), category)
            val nickname = user.name.ifBlank { user.qq }
            val ok = withContext(Dispatchers.IO) {
                ForumRepository.submitPost(context, user.qq, nickname, 1, t, payload, imgUrl)
            }
            submitting = false
            if (ok) {
                toast.showToast(context.getString(R.string.forum_compose_success))
                onPosted()
                onBack()
            } else {
                toast.showToast(context.getString(R.string.forum_compose_fail))
            }
        }
    }

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
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

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

        // 详细分类下拉（圆角跟随主题）
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            OutlinedButton(
                onClick = { categoryMenuOpen = true },
                shape = RoundedCornerShape(themeRadius())
            ) {
                Text(
                    text = stringResource(R.string.forum_compose_category, category),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = categoryMenuOpen,
                onDismissRequest = { categoryMenuOpen = false }
            ) {
                categories.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c) },
                        onClick = {
                            category = c
                            categoryMenuOpen = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text(stringResource(R.string.forum_compose_content)) },
            shape = RoundedCornerShape(themeRadius()),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // 图片区：无图 → 添加图片按钮；有图 → 预览 + 移除 + 重新选择
        if (imageFile == null) {
            OutlinedButton(
                onClick = { pickImage() },
                enabled = !processing && !submitting,
                shape = RoundedCornerShape(themeRadius()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                if (processing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.forum_compose_processing))
                } else {
                    Icon(
                        Icons.Filled.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.forum_compose_add_image))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SubcomposeAsyncImage(
                    model = imageFile,
                    contentDescription = stringResource(R.string.forum_compose_add_image),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(themeRadius()))
                )
                IconButton(
                    onClick = {
                        imageFile = null
                        imageName = "image.jpg"
                    },
                    enabled = !submitting,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.forum_compose_remove_image),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            OutlinedButton(
                onClick = { pickImage() },
                enabled = !processing && !submitting,
                shape = RoundedCornerShape(themeRadius()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                if (processing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.forum_compose_processing))
                } else {
                    Icon(
                        Icons.Filled.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.forum_compose_pick_again))
                }
            }
        }

        Button(
            onClick = { doSubmit() },
            enabled = title.trim().isNotEmpty() && !submitting,
            shape = RoundedCornerShape(themeRadius()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.forum_compose_submitting))
            } else {
                Text(stringResource(R.string.forum_submit))
            }
        }
    }
}