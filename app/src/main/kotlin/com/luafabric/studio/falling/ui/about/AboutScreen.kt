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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.core.net.toUri
import com.luafabric.studio.falling.BuildConfig
import com.luafabric.studio.falling.R
import muling.views.tool.utils.AppInfoUtil
import muling.views.tool.utils.JsonUtil
import muling.views.tool.utils.LogCatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

data class LibraryLicense(
    val name: String,
    val artifactVersion: String? = null,
    val developers: List<LibraryDeveloper>,
    val licenses: List<LicenseInfo>,
    val website: String?
)

data class LibraryDeveloper(
    val name: String,
    val organization: String? = null
)

data class LicenseInfo(
    val name: String,
    val licenseContent: String? = null,
    val url: String? = null
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

    // 加载licenses.json
    var libraryLicenses by remember { mutableStateOf<List<LibraryLicense>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedLicense by remember { mutableStateOf<LibraryLicense?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val jsonString = context.resources.openRawResource(R.raw.licenses)
                    .bufferedReader().use { it.readText() }

                val jsonMap = JsonUtil.parseObject(jsonString) as? Map<String, Any> ?: emptyMap()
                val librariesArray = jsonMap["libraries"] as? List<Map<String, Any>> ?: emptyList()

                val unknown = context.getString(R.string.unknown)
                val unknownLicense = context.getString(R.string.about_unknown_license)
                val unknownLibrary = context.getString(R.string.about_unknown_library)

                val loadedLicenses = librariesArray.map { libMap ->
                    val developers = (libMap["developers"] as? List<Map<String, Any>>)?.map { devMap ->
                        LibraryDeveloper(
                            name = devMap["name"] as? String ?: unknown,
                            organization = devMap["organization"] as? String
                        )
                    } ?: emptyList()

                    val licenses = (libMap["licenses"] as? List<Map<String, Any>>)?.map { licenseMap ->
                        LicenseInfo(
                            name = licenseMap["name"] as? String ?: unknownLicense,
                            licenseContent = licenseMap["licenseContent"] as? String,
                            url = licenseMap["url"] as? String
                        )
                    } ?: emptyList()

                    LibraryLicense(
                        name = libMap["name"] as? String ?: unknownLibrary,
                        artifactVersion = libMap["artifactVersion"] as? String,
                        developers = developers,
                        licenses = licenses,
                        website = libMap["website"] as? String
                    )
                }

                libraryLicenses = loadedLicenses
                isLoading = false
            } catch (e: Exception) {
                LogCatcher.e("AboutScreen", "加载开源协议失败", e)
                isLoading = false
            }
        }
    }

    val teamMembers = remember {
        listOf(
            Developer(
                nameResId = R.string.dev_w_name,
                roleResId = R.string.dev_w_role,
                description = "",
                color = Color(0xFF8D4A5A),
                iconResId = R.drawable.ic_w,
                url = "https://github.com/wisyh"
            ),
            Developer(
                nameResId = R.string.dev_kotlin_name,
                roleResId = R.string.dev_kotlin_role,
                description = "",
                color = Color(0xFF7F52FF),
                iconResId = R.drawable.ic_kotlin,
                url = "https://kotlinlang.org"
            ),
            Developer(
                nameResId = R.string.dev_lua_name,
                roleResId = R.string.dev_lua_role,
                description = "",
                color = Color(0xFF36618E),
                iconResId = R.drawable.ic_lua,
                url = "https://www.lua.org"
            ),
            Developer(
                nameResId = R.string.dev_android_name,
                roleResId = R.string.dev_android_role,
                description = "",
                color = Color(0xFF2E6A44),
                iconResId = R.drawable.ic_android,
                url = "https://developer.android.com"
            ),
            Developer(
                nameResId = R.string.dev_compose_name,
                roleResId = R.string.dev_compose_role,
                description = "",
                color = Color(0xFFD97757),
                iconResId = R.drawable.ic_compose,
                url = "https://developer.android.com/jetpack/compose"
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
            SectionTitle(stringResource(R.string.tech_stack_title))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(teamMembers) { dev ->
                    DeveloperChip(dev)
                }
            }

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
                            "https://qun.qq.com/universal-share/share?ac=1&authKey=fHBSdBajwT9XrPXNUDcaHH17V%2F9xWMrW1qoPGaRgnznVc5MnD%2BW9%2BJr9dtl5J&busi_data=eyJncm91cENvZGUiOiI2NTUzMjAxMTIiLCJ0b2tlbiI6ImdpYmcxUG16a2JvZHROVzJzVkVKdTBrekxEankxNlYza0NwaXNUNW9xNHk1bVBCNXdrMG40ZnVzK0Y2Z0VwbnEiLCJ1aW4iOiIxNDQ3MDE3NzAxIn0%3D&data=9sSl5HKUN9A82WDrHO0ixxwNEEKTiRmbpG-5AhdhGft7lEaUrorDneokip8GNMS47lVst25Mi8NE4MAod9SdPA&svctype=4&tempid=h5_group_info".toUri()
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
            SectionTitle(stringResource(R.string.licenses_title))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                } else if (libraryLicenses.isEmpty()) {
                    Text(
                        text = stringResource(R.string.about_no_license_info),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    libraryLicenses.forEachIndexed { index, license ->
                        val shape = when {
                            libraryLicenses.size == 1 -> MaterialTheme.shapes.large
                            index == 0 -> MaterialTheme.shapes.large.copy(
                                bottomEnd = CornerSize(0.dp),
                                bottomStart = CornerSize(0.dp)
                            )
                            index == libraryLicenses.lastIndex -> MaterialTheme.shapes.large.copy(
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
                                LicenseListItem(
                                    license = license,
                                    onClick = { selectedLicense = license }
                                )

                                if (index < libraryLicenses.lastIndex) {
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

    if (selectedLicense != null) {
        LicenseDetailDialog(license = selectedLicense!!, onDismiss = { selectedLicense = null })
    }
}

@Composable
fun LicenseListItem(license: LibraryLicense, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = license.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val author = license.developers.firstOrNull()?.name
                ?: license.developers.firstOrNull()?.organization
            val licenseName = license.licenses.firstOrNull()?.name

            val subtitle = buildString {
                if (!author.isNullOrBlank()) append(author)
                if (!author.isNullOrBlank() && !licenseName.isNullOrBlank()) append("  •  ")
                if (!licenseName.isNullOrBlank()) append(licenseName)
            }

            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!license.artifactVersion.isNullOrEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = context.getString(R.string.about_version_prefix) + license.artifactVersion,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun LicenseDetailDialog(license: LibraryLicense, onDismiss: () -> Unit) {
    val context = LocalContext.current

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    val licenseText = remember(license) {
        if (license.licenses.isNotEmpty()) {
            license.licenses.joinToString("\n\n") { licenseInfo ->
                licenseInfo.licenseContent ?: licenseInfo.url ?: context.getString(R.string.about_see_website)
            }
        } else {
            context.getString(R.string.about_no_license_info)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .clip(MaterialTheme.shapes.large),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text(text = "✕", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Column(
                    modifier = Modifier
                        .offset(y = (-40).dp)
                        .padding(horizontal = 24.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = license.name.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = license.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    val version = license.artifactVersion
                    if (version != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = context.getString(R.string.about_version_prefix) + version,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = (-20).dp)
                        .padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.license_label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(licenseText))
                                LogCatcher.i("AboutScreen", "复制许可证文本: ${license.name}")
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.copy),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = licenseText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(scrollState)
                        )
                    }
                }

                if (!license.website.isNullOrBlank()) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        Button(
                            onClick = {
                                try {
                                    LogCatcher.i(
                                        "AboutScreen",
                                        "访问库网站: ${license.name} - ${license.website}"
                                    )
                                    val intent =
                                        Intent(Intent.ACTION_VIEW, license.website.toUri())
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    LogCatcher.e("AboutScreen", "访问网站失败", e)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.visit_website))
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
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
    iconResId: Int? = null
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (actualIconResId != 0) {
                    Icon(
                        painter = painterResource(id = actualIconResId),
                        contentDescription = "${context.getString(dev.nameResId)}",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Unspecified
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(dev.color)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(dev.nameResId),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                )

                Text(
                    text = stringResource(dev.roleResId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}