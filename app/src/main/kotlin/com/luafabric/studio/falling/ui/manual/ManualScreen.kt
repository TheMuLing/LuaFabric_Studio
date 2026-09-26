package com.luafabric.studio.falling.ui.manual

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.luafabric.studio.falling.ui.editor.ai.preprocessLatex
import com.luafabric.studio.falling.ui.settings.SettingsManager
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.delay
import muling.views.tool.utils.NonBlockingToastState

private const val CATEGORY_ALL = "全部"
private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * 手册：主页帖子列表 + 详情预览（markdown 渲染，代码块带一键复制）
 */
@Composable
fun ManualScreen(toast: NonBlockingToastState) {
    val context = LocalContext.current

    var posts by remember { mutableStateOf<List<ManualPost>>(emptyList()) }
    var excerpts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedPost by remember { mutableStateOf<ManualPost?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(CATEGORY_ALL) }

    LaunchedEffect(Unit) {
        val loaded = ManualRepository.loadPosts(context)
        posts = loaded
        excerpts = loaded.associate { post ->
            post.file to ManualRepository.extractExcerpt(ManualRepository.loadMarkdown(context, post.file))
        }
    }

    // 搜索防抖
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = searchQuery.trim()
    }

    val categories = remember(posts) {
        listOf(CATEGORY_ALL) + posts.map { it.category }.distinct()
    }

    val filtered = remember(posts, selectedCategory, debouncedQuery) {
        posts.filter { post ->
            (selectedCategory == CATEGORY_ALL || post.category == selectedCategory) &&
                (debouncedQuery.isEmpty() ||
                    post.title.contains(debouncedQuery, ignoreCase = true) ||
                    post.subtitle.contains(debouncedQuery, ignoreCase = true) ||
                    post.tags.any { it.contains(debouncedQuery, ignoreCase = true) })
        }
    }

    BackHandler(enabled = selectedPost != null) { selectedPost = null }

    val current = selectedPost
    if (current != null) {
        ManualDetailScreen(
            post = current,
            toast = toast,
            onBack = { selectedPost = null }
        )
    } else {
        ManualHomeScreen(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelect = { selectedCategory = it },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            posts = filtered,
            excerpts = excerpts,
            onPostClick = { selectedPost = it }
        )
    }
}

// ------------------------------ 主页 ------------------------------

@Composable
private fun ManualHomeScreen(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    posts: List<ManualPost>,
    excerpts: Map<String, String>,
    onPostClick: (ManualPost) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 分类 tabs：TabLayout 同款样式（文字 + primary 下划线指示器），置于搜索框上方
        val categoryIndex = categories.indexOf(selectedCategory).coerceAtLeast(0)
        ScrollableTabRow(
            selectedTabIndex = categoryIndex,
            edgePadding = 16.dp,
            divider = {}
        ) {
            categories.forEach { category ->
                Tab(
                    selected = category == selectedCategory,
                    onClick = { onCategorySelect(category) },
                    text = { Text(category) }
                )
            }
        }

        // 搜索框：圆角跟随 LuaFabric 主题配置（形状圆角）
        val radius = when (SettingsManager.currentSettings.shapeSizeIndex) {
            0 -> 4.dp
            1 -> 8.dp
            2 -> 12.dp
            3 -> 16.dp
            else -> 12.dp
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("搜索教程标题 / 副标题 / 标签") },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(radius),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (posts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "未找到匹配的教程",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(posts, key = { it.file }) { post ->
                    ManualCard(
                        post = post,
                        excerpt = excerpts[post.file] ?: "",
                        onClick = { onPostClick(post) }
                    )
                    // 条目间左右不碰壁细分割线
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

@Composable
private fun ManualCard(
    post: ManualPost,
    excerpt: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = post.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = post.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = excerpt,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        TagChips(
            tags = post.tags,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// ------------------------------ 详情页 ------------------------------

@Composable
private fun ManualDetailScreen(
    post: ManualPost,
    toast: NonBlockingToastState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var markdown by remember(post.file) { mutableStateOf<String?>(null) }

    LaunchedEffect(post.file) {
        markdown = ManualRepository.loadMarkdown(context, post.file)
    }

    val segments = remember(markdown) { markdown?.let { splitMarkdown(it) } }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏：返回箭头 + 跑马灯标题
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            MarqueeTitle(
                text = post.title,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        if (segments == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = post.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                TagChips(tags = post.tags, modifier = Modifier.padding(top = 8.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                segments.forEach { segment ->
                    if (segment.isCode) {
                        CodeBlockCard(code = segment.content, toast = toast)
                    } else {
                        MarkdownBody(markdown = segment.content)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

/** 跑马灯标题：compose 1.8 的 basicMarquee，WhileFocused 需 focusable 在 marquee 之后（作为其后代） */
@Composable
private fun MarqueeTitle(text: String, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    BasicText(
        text = text,
        maxLines = 1,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .basicMarquee(
                iterations = Int.MAX_VALUE,
                animationMode = MarqueeAnimationMode.WhileFocused
            )
            .focusRequester(focusRequester)
            .focusable()
    )
    LaunchedEffect(text) { focusRequester.requestFocus() }
}

/** 代码块卡片：等宽字体 + 右上角一键复制 */
@Composable
private fun CodeBlockCard(code: String, toast: NonBlockingToastState) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("lua", code))
                        toast.showToast("代码已复制")
                    }
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "复制代码",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            )
        }
    }
}

/** 非代码段：Markwon 渲染，可长按选择 */
@Composable
private fun MarkdownBody(markdown: String) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(
                JLatexMathPlugin.create(
                    context.resources.displayMetrics.scaledDensity * 14f,
                    { builder -> builder.inlinesEnabled(true) }
                )
            )
            .build()
    }
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(true)
                textSize = 14f
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, preprocessLatex(markdown))
        }
    )
}

/** 标签 chips：圆角矩形、容器色、文本居中、每个标签带 # 前缀、多行排布 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(tags: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.forEach { tag ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ------------------------------ md 拆分 ------------------------------

/** 渲染段：isCode=true 表示 fenced 代码块，否则为普通 markdown 正文 */
private data class MdSegment(val isCode: Boolean, val content: String)

/** 按 ``` 栅栏把 md 拆成代码段与非代码段 */
private fun splitMarkdown(md: String): List<MdSegment> {
    val segments = mutableListOf<MdSegment>()
    val buffer = StringBuilder()
    var inCode = false
    for (line in md.lines()) {
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                segments += MdSegment(true, buffer.toString().trim('\n'))
                buffer.clear()
                inCode = false
            } else {
                if (buffer.isNotBlank()) segments += MdSegment(false, buffer.toString().trim('\n'))
                buffer.clear()
                inCode = true
            }
        } else {
            buffer.append(line).append('\n')
        }
    }
    if (buffer.isNotBlank()) segments += MdSegment(inCode, buffer.toString().trim('\n'))
    return segments.filter { it.content.isNotBlank() }
}
