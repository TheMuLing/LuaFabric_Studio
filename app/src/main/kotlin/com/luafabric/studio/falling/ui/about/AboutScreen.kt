package com.luafabric.studio.falling.ui.about

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.luafabric.studio.falling.BuildConfig
import com.luafabric.studio.falling.R
import muling.views.tool.utils.AppInfoUtil
import muling.views.tool.utils.LogCatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Developer(
    val nameResId: Int,
    val roleResId: Int,
    val description: String,
    val color: Color,
    val iconResId: Int,
    val url: String = ""
)

data class ChangelogEntry(
    val date: String,
    val version: String,
    val items: List<String>
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val packageInfo = AppInfoUtil.getPackageInfo()
    val appVersionName = packageInfo?.versionName ?: "1.0.0"
    val copyrightYear = BuildConfig.COPYRIGHT_YEAR

    val buildTime = remember {
        derivedStateOf {
            try {
                val timeMillis = BuildConfig.BUILD_TIME.toLongOrNull() ?: System.currentTimeMillis()
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                sdf.format(Date(timeMillis))
            } catch (e: Exception) {
                LogCatcher.e("AboutScreen", "获取构建时间失败", e)
                context.getString(R.string.unknown)
            }
        }
    }

    val prefs = remember {
        context.getSharedPreferences("LuaFabric_settings", Context.MODE_PRIVATE)
    }

    var showAuthorNote by remember {
        mutableStateOf(prefs.getBoolean("show_author_note", true))
    }

    val changelogEntries = remember {
        listOf(
            ChangelogEntry(
                date = "2026-08-21",
                version = "26.08.21",
                items = listOf(
                    "修复 libsocket.so 无法加载问题",
                    "修复返回键退出后重进项目进度条卡死",
                    "侧边栏\"赞助\"与\"关于\"位置互换",
                    "Licenses 替换为更新日志"
                )
            ),
            ChangelogEntry(
                date = "2026-08-19",
                version = "26.08.19-gamma",
                items = listOf(
                    "新增 Maven 依赖下载进度显示",
                    "优化代码补全性能",
                    "修复若干崩溃问题"
                )
            ),
            ChangelogEntry(
                date = "2026-08-15",
                version = "26.08.15",
                items = listOf(
                    "重构编辑器内核",
                    "新增 Lua 语法高亮",
                    "新增项目模板功能"
                )
            ),
            ChangelogEntry(
                date = "2026-08-10",
                version = "26.08.10",
                items = listOf(
                    "初始版本发布",
                    "基础代码编辑功能",
                    "项目创建与管理",
                    "APK 编译与安装"
                )
            )
        ).sortedByDescending { entry ->
            // 按版本号排序：解析 YY.MM.DD[-suffix] 格式
            val parts = entry.version.split("-")[0].split(".")
            val major = parts.getOrElse(0) { "0" }.padStart(4, '0')
            val minor = parts.getOrElse(1) { "0" }.padStart(2, '0')
            val patch = parts.getOrElse(2) { "0" }.padStart(2, '0')
            "$major$minor$patch"
        }
    }

    val teamMembers = remember {
        listOf(
            Developer(
                nameResId = R.string.dev_muling_name,
                roleResId = R.string.dev_muling_role,
                description = "",
                color = Color(0xFF8D4A5A),
                iconResId = R.drawable.ic_muling,
                url = "https://github.com/TheMuLing"
            ),
            Developer(
                nameResId = R.string.dev_w_name,
                roleResId = R.string.dev_w_role,
                description = "",
                color = Color(0xFF8D4A5A),
                iconResId = R.drawable.ic_w,
                url = "https://github.com/wisyh"
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            AppHeaderSection(
                appVersionName = appVersionName
            )
        }

        item {
            AnimatedVisibility(
                visible = showAuthorNote,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                AuthorNoteCard(
                    buildTime = buildTime.value,
                    onClose = {
                        showAuthorNote = false
                        prefs.edit { putBoolean("show_author_note", false) }
                    }
                )
            }

            SectionTitle(stringResource(R.string.tech_stack_title))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                teamMembers.forEach { dev ->
                    DeveloperChip(dev, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.community_title))

            Surface(
                onClick = {
                    try {
                        LogCatcher.i("AboutScreen", "打开QQ群")
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=I96XEdCObX_xJ6sdtqIbL4iMyOL4Sx51&authKey=vB%2FWu7tABJtyweVPuTUtiwChmFPg3IyO6lZkAsb49r5puqX202vw%2FozUarawnEaz&noverify=0&group_code=1106643491".toUri()
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        LogCatcher.e("AboutScreen", "打开QQ群失败", e)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF12B7F5),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_qq_group),
                                contentDescription = "QQ Group",
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.qq_group_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.qq_group_number),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            SectionTitle(stringResource(R.string.changelog_title))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                var expandedIndex by remember { mutableStateOf(0) }

                changelogEntries.forEachIndexed { index, entry ->
                    val isExpanded = index == expandedIndex

                    val shape = when {
                        changelogEntries.size == 1 -> MaterialTheme.shapes.large
                        index == 0 -> MaterialTheme.shapes.large.copy(
                            bottomEnd = CornerSize(0.dp),
                            bottomStart = CornerSize(0.dp)
                        )
                        index == changelogEntries.lastIndex -> MaterialTheme.shapes.large.copy(
                            topStart = CornerSize(0.dp),
                            topEnd = CornerSize(0.dp)
                        )
                        else -> RectangleShape
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 0.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = shape
                    ) {
                        Column {
                            ChangelogHeader(
                                entry = entry,
                                isExpanded = isExpanded,
                                onClick = {
                                    expandedIndex = if (expandedIndex == index) -1 else index
                                }
                            )

                            AnimatedVisibility(visible = isExpanded) {
                                Column {
                                    entry.items.forEach { item ->
                                        Text(
                                            text = "•  $item",
                                            modifier = Modifier.padding(
                                                start = 20.dp, end = 20.dp,
                                                top = 2.dp, bottom = 2.dp
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 18.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }

                            if (index < changelogEntries.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.copyright, copyrightYear),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

}

@Composable
fun ChangelogHeader(entry: ChangelogEntry, isExpanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.version,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = entry.date,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Icon(
            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun AuthorNoteCard(
    buildTime: String,
    onClose: () -> Unit
) {
    Column {
        Spacer(modifier = Modifier.height(20.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
            shape = MaterialTheme.shapes.large
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.author_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.build_time_label, buildTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onClose() },
                    color = Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppHeaderSection(
    appVersionName: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .size(160.dp)
                .clip(MaterialTheme.shapes.extraLarge)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_studio),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = appVersionName,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

@Composable
fun DeveloperChip(
    dev: Developer,
    iconResId: Int? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val actualIconResId = iconResId ?: dev.iconResId

    Card(
        onClick = {
            if (dev.url.isNotEmpty()) {
                try {
                    LogCatcher.i("AboutScreen", "打开开发者链接: ${context.getString(dev.nameResId)} - ${dev.url}")
                    val intent = Intent(Intent.ACTION_VIEW, dev.url.toUri())
                    context.startActivity(intent)
                } catch (e: Exception) {
                    LogCatcher.e("AboutScreen", "打开链接失败", e)
                }
            }
        },
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (actualIconResId != 0) {
                    Icon(
                        painter = painterResource(id = actualIconResId),
                        contentDescription = "${context.getString(dev.nameResId)}",
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(dev.color)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(dev.nameResId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(dev.roleResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                painter = painterResource(id = R.drawable.ic_export_variant),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}