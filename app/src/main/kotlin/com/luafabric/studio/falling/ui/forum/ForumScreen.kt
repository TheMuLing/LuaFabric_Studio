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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.onFocusChanged
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

/** 圆角跟随 luafabric 主题设置 */
internal fun themeRadius(): Dp = when (SettingsManager.currentSettings.shapeSizeIndex) {
    0 -> 4.dp
    1 -> 8.dp
    2 -> 12.dp
    3 -> 16.dp
    else -> 12.dp
}

/** 源码论坛：第一层板块 tabs + 搜索（#标签/文本/范围）+ 帖子列表 + 发帖 FAB */
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
    /** 当前登录用户（未登录为 null，互动一律先强制登录） */
    loggedInUser: YunJuResponse?,
    /** 未登录点击互动时跳转账户界面 */
    onRequireLogin: () -> Unit,
    /** 全局受控标签词表（用于 `#` 补全），可为空 */
    tagWordlist: List<String>,
    /** 详情页点标签回填的搜索串（一次性种子，消费后清空） */
    searchSeed: String?,
    onSearchSeedConsumed: () -> Unit,
    /** 打开发帖页并携带当前板块 ID（独立 Activity，发帖成功返回 RESULT_POSTED 刷新） */
    onOpenCompose: (Int) -> Unit,
    /** 打开帖子详情页（独立界面，由 MainScreen 全屏覆盖层承载） */
    onOpenDetail: (ForumItem) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var layer1Index by remember { mutableIntStateOf(0) }
    // 发帖 FAB 展开态：列表到顶显示文字，滚动后收起（复制自创建项目 FAB）
    var showExtendedFab by remember { mutableStateOf(true) }

    // 搜索历史（完整查询串）、输入焦点、范围过滤开关与范围（仅作用文本匹配）
    var searchFocused by remember { mutableStateOf(false) }
    // 下拉被手动关闭后抑制显示，直到再次输入/聚焦（否则关闭后无法再弹出）
    var dropdownDismissed by remember { mutableStateOf(false) }
    var showRangeFilter by remember { mutableStateOf(false) }
    var searchHistory by remember { mutableStateOf(ForumRepository.loadSearchHistory(context)) }
    var rangeTitle by remember { mutableStateOf(true) }
    var rangeContent by remember { mutableStateOf(true) }
    var rangeNickname by remember { mutableStateOf(true) }

    val layer1Tabs = listOf(
        stringResource(R.string.forum_source),
        stringResource(R.string.forum_project)
    )

    // 板块 ID：第一层「源码实例」=1，「完整项目」=2（云居后端按板块发帖）
    val layer1ForumIds = listOf(1, 2)
    val layer1ForumId = layer1ForumIds[layer1Index.coerceIn(layer1ForumIds.indices)]
    // 帖子数据来自外层缓存：板块无缓存才请求加载（有缓存直接用，切换导航页不重复请求）
    val posts = postsCache[layer1ForumId] ?: emptyList()
    val isLoading = !postsCache.containsKey(layer1ForumId)

    LaunchedEffect(layer1ForumId) { onEnsureLoaded(layer1ForumId) }

    // 详情页点标签 → 回填搜索串（消费后清空一次性种子）
    LaunchedEffect(searchSeed) {
        val seed = searchSeed
        if (!seed.isNullOrBlank()) {
            searchQuery = seed
            onSearchSeedConsumed()
        }
    }

    // 发帖 FAB 展开态跟随列表滚动：到顶显示文字，滚动后收起（复制自创建项目 FAB）
    LaunchedEffect(
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset
    ) {
        val isScrolled = listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 0
        showExtendedFab = !isScrolled
    }

    // `#` 补全：仅当最后一个词元以 # 开头时，按归一化子串筛选词表
    val lastToken = remember(searchQuery) {
        searchQuery.split(Regex("\\s+")).lastOrNull().orEmpty()
    }
    val tagSuggestions = remember(lastToken, tagWordlist) {
        if (lastToken.startsWith("#")) {
            val prefix = ForumRepository.normalizeTag(lastToken.removePrefix("#"))
            tagWordlist
                .filter { ForumRepository.normalizeTag(it).contains(prefix) }
                .take(20)
        } else {
            emptyList()
        }
    }
    // 下拉优先级：# 补全 > 空串时的搜索历史
    val showTagSuggest = searchFocused && tagSuggestions.isNotEmpty()
    val showHistory = searchFocused && searchQuery.isBlank() && searchHistory.isNotEmpty()

    // 等级可见性：未登录视为 0 级；作者本人始终可见自己的帖
    val viewerLevel = loggedInUser?.level?.toIntOrNull() ?: 0
    val viewerQq = loggedInUser?.qq.orEmpty()

    // 帖子过滤：等级限制彻底隐藏 → `#标签` 归一子串匹配（多标签 OR）→ 标签与文本 AND，范围仅作用文本
    val filtered = remember(
        posts, searchQuery, rangeTitle, rangeContent, rangeNickname, viewerLevel, viewerQq
    ) {
        val qTags = ForumRepository.parseQueryTags(searchQuery)
        val qText = ForumRepository.parseQueryText(searchQuery)
        posts.filter { post ->
            val meta = ForumRepository.parseMeta(post.content)
            if (meta.minLevel > viewerLevel && post.qq != viewerQq) return@filter false
            val tagMatch = qTags.isEmpty() || run {
                val postTags = meta.tags.map { ForumRepository.normalizeTag(it) }
                qTags.any { qt -> postTags.any { it.contains(qt) } }
            }
            if (!tagMatch) return@filter false
            if (qText.isEmpty()) return@filter true
            (rangeTitle && post.title.contains(qText, ignoreCase = true)) ||
                (rangeContent && ForumRepository.stripCategoryMarker(post.content)
                    .contains(qText, ignoreCase = true)) ||
                (rangeNickname && post.nickname.contains(qText, ignoreCase = true))
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

            // 搜索框：板块 tabs 下方、列表上方；前导 search 图标 + 末尾 清除/筛选 图标
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        dropdownDismissed = false
                    },
                    placeholder = { Text(stringResource(R.string.forum_search_hint)) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.forum_search_clear),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { showRangeFilter = !showRangeFilter }) {
                                Icon(
                                    Icons.Filled.FilterAlt,
                                    contentDescription = stringResource(R.string.forum_filter),
                                    tint = if (showRangeFilter) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(themeRadius()),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        searchHistory = ForumRepository.pushSearchHistory(context, searchQuery)
                        dropdownDismissed = true
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .onFocusChanged {
                            searchFocused = it.isFocused
                            if (it.isFocused) dropdownDismissed = false
                        }
                )
                // 下拉：# 标签补全 / 搜索历史
                DropdownMenu(
                    expanded = (showTagSuggest || showHistory) && !dropdownDismissed,
                    onDismissRequest = { dropdownDismissed = true }
                ) {
                    if (showTagSuggest) {
                        tagSuggestions.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text("#$tag") },
                                onClick = {
                                    searchQuery = replaceLastToken(searchQuery, "#$tag")
                                    dropdownDismissed = true
                                }
                            )
                        }
                    } else {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.forum_search_history),
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            trailingIcon = {
                                Text(stringResource(R.string.forum_search_history_clear))
                            },
                            onClick = {
                                ForumRepository.clearSearchHistory(context)
                                searchHistory = emptyList()
                            }
                        )
                        searchHistory.forEach { h ->
                            DropdownMenuItem(
                                text = {
                                    Text(h, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                onClick = {
                                    searchQuery = h
                                    dropdownDismissed = true
                                }
                            )
                        }
                    }
                }
            }

            // 范围复选框：仅作用文本匹配（标题/正文/昵称）
            AnimatedVisibility(visible = showRangeFilter) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = rangeTitle,
                        onClick = { rangeTitle = !rangeTitle },
                        label = { Text(stringResource(R.string.forum_search_range_title)) },
                        shape = RoundedCornerShape(themeRadius())
                    )
                    FilterChip(
                        selected = rangeContent,
                        onClick = { rangeContent = !rangeContent },
                        label = { Text(stringResource(R.string.forum_search_range_content)) },
                        shape = RoundedCornerShape(themeRadius())
                    )
                    FilterChip(
                        selected = rangeNickname,
                        onClick = { rangeNickname = !rangeNickname },
                        label = { Text(stringResource(R.string.forum_search_range_nickname)) },
                        shape = RoundedCornerShape(themeRadius())
                    )
                }
            }

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
                                    onOpenDetail = { onOpenDetail(post) },
                                    onTagClick = { tag ->
                                        searchQuery = "#$tag"
                                        searchHistory = ForumRepository.pushSearchHistory(
                                            context, searchQuery
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 右下角发帖 FAB：复制创建项目 FAB——列表到顶展开显示文字，滚动后收起，停靠导航栏上方
        ExtendedFloatingActionButton(
            onClick = { onOpenCompose(layer1ForumId) },
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

/** 互动身份：仅取当前登录用户（未登录不参与互动，一律先强制登录）；管理员账号仅在 native 内部使用 */
private data class ForumIdentity(val qq: String, val nickname: String)

private fun forumIdentity(activeUser: YunJuResponse?): ForumIdentity {
    val qq = activeUser?.qq?.takeIf { it.isNotBlank() } ?: ""
    val nick = activeUser?.name?.takeIf { it.isNotBlank() } ?: qq
    return ForumIdentity(qq, nick)
}

/** 用新串替换查询串最后一个空白分隔的词元（供 `#` 补全回填）；无空白则整体替换 */
private fun replaceLastToken(query: String, replacement: String): String {
    val idx = query.indexOfLast { it.isWhitespace() }
    return if (idx < 0) replacement else query.substring(0, idx + 1) + replacement
}

/** 帖子标签 chips 行（点击回填搜索）；无标签不渲染 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostTagChips(tags: List<String>, onTagClick: (String) -> Unit) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.forEach { tag ->
            AssistChip(
                onClick = { onTagClick(tag) },
                label = { Text("#$tag", style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(themeRadius())
            )
        }
    }
}

/**
 * 帖子卡片（朋友圈式）：头像 + 昵称 + 日期 + 标题 + 正文节选 + 配图 + 点赞/评论/收藏。
 * 点击卡片或评论按钮进入详情页；点赞/收藏为乐观反馈（本地填充 + 脉冲动画），
 * 未登录点击互动弹提示并跳转账户界面。
 */
@Composable
internal fun ForumPostCard(
    post: ForumItem,
    activeUser: YunJuResponse?,
    toast: NonBlockingToastState,
    onRequireLogin: () -> Unit,
    onOpenDetail: () -> Unit,
    onTagClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = SettingsManager.currentSettings
    val identity = forumIdentity(activeUser)
    val meta = remember(post.postId, post.content) { ForumRepository.parseMeta(post.content) }
    // 本卡片点赞本地乐观态（后端无状态查询，纯反馈型）
    val praised = remember(post.postId) { mutableStateOf(false) }

    fun guard(): Boolean {
        if (activeUser == null || identity.qq.isBlank()) {
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
                ForumRepository.praise(context, post.postId, identity.qq)
            }
            if (!ok) praised.value = !next
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

        // 付费 / 等级限制角标
        if (meta.paid || meta.minLevel > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (meta.paid) {
                    PostBadge(
                        text = if (meta.priceMode == PRICE_MODE_PERCENT) {
                            stringResource(R.string.forum_badge_percent, meta.percent)
                        } else {
                            stringResource(R.string.forum_badge_price, meta.fixedPrice)
                        },
                        icon = Icons.Filled.Lock
                    )
                }
                if (meta.minLevel > 0) {
                    PostBadge(
                        text = stringResource(R.string.forum_badge_level, meta.minLevel),
                        icon = Icons.Filled.Person
                    )
                }
            }
        }

        // 正文节选（完整 Lua 注释语义：剔注释、折叠空白）
        val excerpt = remember(post.postId, post.content) {
            ForumRepository.buildExcerpt(post.content)
        }
        if (excerpt.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = excerpt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 标签 chips（点击回填搜索）
        PostTagChips(tags = meta.tags, onTagClick = onTagClick)

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

        // 动作条：居右，顺序 点赞 / 评论
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
        }
    }
}

/** 帖子角标（付费 / 等级限制）：图标 + 文字的小圆角容器 */
@Composable
private fun PostBadge(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = RoundedCornerShape(themeRadius()),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * 付费墙：仅详情页遮挡付费帖正文，展示价格与解锁按钮。
 * 固定价 → 直显价；百分比 → 有余额则按余额算成交价，无余额则显「百分比 + 底价」。
 */
@Composable
private fun PaidWallCard(meta: ForumPostMeta, myCoin: Int?, onUnlock: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(themeRadius()),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.forum_pay_locked),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            val priceText = if (meta.priceMode == PRICE_MODE_PERCENT) {
                myCoin?.let { stringResource(R.string.forum_pay_cost, ForumRepository.computeCost(meta, it)) }
                    ?: stringResource(R.string.forum_pay_percent, meta.percent, meta.percentFloor)
            } else {
                stringResource(R.string.forum_pay_cost, meta.fixedPrice)
            }
            Text(
                text = priceText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onUnlock,
                shape = RoundedCornerShape(themeRadius())
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.forum_pay_unlock))
            }
            myCoin?.let { coin ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.forum_pay_my_coin, coin),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 购买确认弹窗的键值行（左标签右值） */
@Composable
private fun PaySheetRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
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
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ForumPostDetailScreen(
    post: ForumItem,
    activeUser: YunJuResponse?,
    toast: NonBlockingToastState,
    onRequireLogin: () -> Unit,
    onTagClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = SettingsManager.currentSettings
    val identity = forumIdentity(activeUser)
    val meta = remember(post.postId, post.content) { ForumRepository.parseMeta(post.content) }
    val praised = remember(post.postId) { mutableStateOf(false) }
    // 付费墙：作者本人直出；其余需已购
    val isAuthor = identity.qq.isNotBlank() && post.qq == identity.qq
    val needsPurchase = meta.paid && !isAuthor
    var unlocked by remember(post.postId) { mutableStateOf(!needsPurchase) }
    var showPurchaseSheet by remember(post.postId) { mutableStateOf(false) }
    var buyLoading by remember(post.postId) { mutableStateOf(false) }
    var myCoin by remember(post.postId) { mutableStateOf<Int?>(null) }
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
            ForumRepository.loadComments(context, post.postId)
        }
        commentsLoading.value = false
        commentsRaw.value = parseCommentsArray(raw)
    }

    // 付费帖：进入时判定已购（FollowList ∪ 本地 pending）+ 拉我的余额（显示成交价）
    LaunchedEffect(post.postId, needsPurchase) {
        if (!needsPurchase) return@LaunchedEffect
        val ids = withContext(Dispatchers.IO) {
            ForumRepository.loadPurchasedIds(context, identity.qq)
        }
        if (post.postId in ids) unlocked = true
        myCoin = withContext(Dispatchers.IO) { ForumRepository.fetchCoin(context, identity.qq) }
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
        if (activeUser == null || identity.qq.isBlank()) {
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
                ForumRepository.praise(context, post.postId, identity.qq)
            }
            if (!ok) praised.value = !next
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
                        identity.qq, identity.nickname, text
                    )
                } else {
                    ForumRepository.comment(
                        context, post.postId, identity.qq, identity.nickname, text
                    )
                }
            }
            if (ok) {
                val raw = withContext(Dispatchers.IO) {
                    ForumRepository.loadComments(context, post.postId)
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

                    if (!unlocked) {
                        Spacer(modifier = Modifier.height(8.dp))
                        PaidWallCard(
                            meta = meta,
                            myCoin = myCoin,
                            onUnlock = {
                                if (!guard()) return@PaidWallCard
                                showPurchaseSheet = true
                            }
                        )
                    } else if (post.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = ForumRepository.stripCategoryMarker(post.content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 标签 chips（点击回填搜索并返回列表）
                    PostTagChips(tags = meta.tags, onTagClick = onTagClick)

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

                    // 动作条：居右，顺序 点赞 / 评论（与列表一致）
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

    // 购买确认弹窗（ModalBottomSheet）：价格按当前余额实时计算，确认后走 ForumRepository.purchase
    if (showPurchaseSheet) {
        val sheetState = rememberModalBottomSheetState()
        val sheetCost = myCoin?.let { ForumRepository.computeCost(meta, it) }
        ModalBottomSheet(
            onDismissRequest = { if (!buyLoading) showPurchaseSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.forum_pay_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                PaySheetRow(
                    label = stringResource(R.string.forum_pay_row_title),
                    value = post.title
                )
                PaySheetRow(
                    label = stringResource(R.string.forum_pay_row_author),
                    value = post.nickname.ifBlank { post.qq }
                )
                PaySheetRow(
                    label = stringResource(R.string.forum_pay_row_price),
                    value = sheetCost?.let { stringResource(R.string.forum_pay_cost, it) }
                        ?: stringResource(R.string.forum_pay_calculating)
                )
                PaySheetRow(
                    label = stringResource(R.string.forum_pay_row_balance),
                    value = myCoin?.let { stringResource(R.string.forum_pay_cost, it) }
                        ?: stringResource(R.string.forum_pay_calculating)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { showPurchaseSheet = false },
                        enabled = !buyLoading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(themeRadius())
                    ) {
                        Text(stringResource(R.string.forum_pay_cancel))
                    }
                    Button(
                        onClick = {
                            if (buyLoading) return@Button
                            buyLoading = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    ForumRepository.purchase(context, post, identity.qq)
                                }
                                buyLoading = false
                                showPurchaseSheet = false
                                when (result) {
                                    is PurchaseResult.Success -> {
                                        unlocked = true
                                        toast.showToast(context.getString(R.string.forum_pay_ok))
                                    }
                                    is PurchaseResult.PayoutPending -> {
                                        unlocked = true
                                        toast.showToast(context.getString(R.string.forum_pay_payout_pending))
                                    }
                                    is PurchaseResult.Fail -> toast.showToast(result.message)
                                }
                            }
                        },
                        enabled = !buyLoading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(themeRadius())
                    ) {
                        if (buyLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.forum_pay_confirm))
                        }
                    }
                }
            }
        }
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
