package com.luafabric.studio.falling.ui.forum

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import coil.compose.SubcomposeAsyncImage
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.components.Toast
import com.luafabric.studio.falling.ui.components.configureSlideTransitions
import com.luafabric.studio.falling.ui.components.finishWithSlide
import com.luafabric.studio.falling.ui.login.LoginStore
import com.luafabric.studio.falling.ui.login.YunJuResponse
import com.luafabric.studio.falling.ui.settings.SettingsManager
import com.luafabric.studio.falling.ui.theme.AppThemeWithObserver
import io.github.tarifchakder.ktoast.ToastData
import io.github.tarifchakder.ktoast.ToastHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import muling.views.tool.utils.NonBlockingToastState
import muling.views.tool.utils.rememberNonBlockingToastState
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** 自定义标签最大字符数（超出拒绝添加） */
private const val TAG_MAX_LEN = 10

/** 百分比收费允许的百分比上限 */
private const val PERCENT_MAX = 90

/** 固定价上限系数：自身金币 × 该系数向下取整 */
private const val FIXED_PRICE_RATIO = 0.8

/** 百分比模式底价上限系数：自身金币 × 该系数向下取整 */
private const val PERCENT_FLOOR_RATIO = 0.9

/**
 * 发帖编辑页独立 Activity：一次返回键即关闭（不再退回主框架覆盖层）。
 * 左右滑动画：进入 slide_in_right/slide_out_left；返回 slide_in_left/slide_out_right。
 * API33+ 系统预测返回（manifest enableOnBackInvokedCallback）自动叠加左右滑动预览。
 * 发帖成功返回 RESULT_POSTED（携板块 ID）；未登录发帖返回 RESULT_LOGIN_REQUIRED。
 */
class ForumComposeActivity : ComponentActivity() {

    companion object {
        /** 目标板块 ID：既是入参 extra，也随 RESULT_POSTED 回传（列表页据此刷新） */
        const val EXTRA_FORUM_ID = "forum_id"

        /** 发帖成功：列表页据此刷新对应板块 */
        const val RESULT_POSTED = 2001

        /** 未登录发帖：列表页据此切到账户界面 */
        const val RESULT_LOGIN_REQUIRED = 2002

        fun intent(context: Context, forumId: Int): Intent =
            Intent(context, ForumComposeActivity::class.java).putExtra(EXTRA_FORUM_ID, forumId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 左右滑转场走 overrideActivityTransition（API34+）以支持预测返回跟随拖动
        configureSlideTransitions(
            R.anim.slide_in_right,
            R.anim.slide_out_left,
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )
        val forumId = intent.getIntExtra(EXTRA_FORUM_ID, 1)

        setContent {
            var settingsLoaded by remember { mutableStateOf(false) }
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    SettingsManager.loadSavedSettings(context)
                }
                settingsLoaded = true
            }
            Crossfade(
                targetState = settingsLoaded,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(durationMillis = 300)
            ) { loaded: Boolean ->
                if (loaded) {
                    AppThemeWithObserver {
                        val activeUser = remember { LoginStore.read(context).user }
                        val toast = rememberNonBlockingToastState()
                        // 词表：先用本地 MMKV 缓存兜底，再拉取云居文档刷新（失败保留缓存）
                        var wordlist by remember { mutableStateOf<List<String>>(emptyList()) }
                        LaunchedEffect(Unit) {
                            val cached = withContext(Dispatchers.IO) {
                                ForumRepository.loadCachedTagWordlist(context)
                            }
                            if (cached.isNotEmpty()) wordlist = cached
                            wordlist = withContext(Dispatchers.IO) {
                                ForumRepository.refreshTagWordlist(context)
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            ForumComposeScreen(
                                toast = toast,
                                activeUser = activeUser,
                                forumId = forumId,
                                tagWordlist = wordlist,
                                onRequireLogin = {
                                    setResult(RESULT_LOGIN_REQUIRED)
                                    finishSliding()
                                },
                                onBack = { finishSliding() },
                                onPosted = {
                                    setResult(
                                        RESULT_POSTED,
                                        Intent().putExtra(EXTRA_FORUM_ID, forumId)
                                    )
                                }
                            )
                            ToastHost(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 64.dp, bottom = 64.dp, start = 24.dp, end = 24.dp),
                                alignment = Alignment.BottomCenter,
                                hostState = toast.originalToastState,
                                transitionSpec = {
                                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                                },
                                toast = { toastData: ToastData -> Toast(toastData) }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    /** 关闭：当前页右滑出 + 下层页左滑回位（API<34 显式转场；34+ 由 CLOSE 转场接管） */
    private fun finishSliding() {
        finishWithSlide(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}

/**
 * 发帖编辑页：标题 + 标签（词表勾选 + 自定义输入，两行横向滚动）
 * + 正文 + 图片（相册选图 → 压缩 1920px → native 单次直传）
 * + 付费设置（免费/付费 + 固定价/百分比）+ 可查看等级（0=无限制）。
 * 提交正文自动在首行拼元数据隐藏标志（JSON 单行，显示端剥离）；未登录拦截跳账户页。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ForumComposeScreen(
    toast: NonBlockingToastState,
    activeUser: YunJuResponse?,
    /** 目标板块 ID（1=源码实例，2=完整项目） */
    forumId: Int,
    /** 全局受控标签词表（可选标签 chips），可为空 */
    tagWordlist: List<String>,
    onRequireLogin: () -> Unit,
    onBack: () -> Unit,
    onPosted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    // 标签：已选标签（可为空）+ 自定义输入（`#` 触发词表补全，回车成标签、≤10 字）
    var selectedTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var tagInput by remember { mutableStateOf("") }
    // 图片：压缩后本地文件（上传用）+ 原始文件名
    var imageFile by remember { mutableStateOf<File?>(null) }
    var imageName by remember { mutableStateOf("image.jpg") }
    var processing by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    // 付费设置：免费/付费 + 收费模式（固定价/百分比）+ 价格输入
    var paid by remember { mutableStateOf(false) }
    var priceMode by remember { mutableStateOf(PRICE_MODE_FIXED) }
    var fixedPriceText by remember { mutableStateOf("") }
    var percentText by remember { mutableStateOf("") }
    var percentFloorText by remember { mutableStateOf("") }
    // 可查看等级（0=无限制）
    var minLevel by remember { mutableIntStateOf(0) }

    // 价格上限由发帖者自身金币推导（金币/等级随登录态缓存）
    val sellerCoin = activeUser?.coin?.toIntOrNull() ?: 0
    val sellerLevel = activeUser?.level?.toIntOrNull() ?: 0
    val maxFixedPrice = (sellerCoin * FIXED_PRICE_RATIO).toInt().coerceAtLeast(0)
    val maxPercentFloor = (sellerCoin * PERCENT_FLOOR_RATIO).toInt().coerceAtLeast(0)

    /** 添加自定义标签：去 `#`、校验非空与 ≤10 字、忽略重复后清空输入 */
    fun addTag(raw: String) {
        val tag = raw.trim().removePrefix("#").trim()
        if (tag.isEmpty()) return
        if (tag.length > TAG_MAX_LEN) {
            toast.showToast(context.getString(R.string.forum_compose_tag_too_long))
            return
        }
        if (selectedTags.none { it.equals(tag, ignoreCase = true) }) {
            selectedTags = selectedTags + tag
        }
        tagInput = ""
    }

    // `#` 补全：输入以 # 开头时，按归一化子串筛选未选中的词表项
    val tagSuggestions = remember(tagInput, tagWordlist, selectedTags) {
        if (tagInput.startsWith("#")) {
            val prefix = ForumRepository.normalizeTag(tagInput.removePrefix("#"))
            tagWordlist
                .filter { it !in selectedTags }
                .filter { ForumRepository.normalizeTag(it).contains(prefix) }
                .take(20)
        } else {
            emptyList()
        }
    }
    var tagMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(tagSuggestions) { tagMenuOpen = tagSuggestions.isNotEmpty() }

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
        // 付费参数校验：固定价 / 百分比 + 底价逐项校验，任一越界即拦截
        val mode = if (priceMode == PRICE_MODE_PERCENT) PRICE_MODE_PERCENT else PRICE_MODE_FIXED
        var parsedFixed = 0
        var parsedPercent = 0
        var parsedFloor = 0
        if (paid) {
            if (mode == PRICE_MODE_FIXED) {
                val v = fixedPriceText.toDoubleOrNull()?.roundToInt() ?: 0
                if (v < 1 || v > maxFixedPrice) {
                    toast.showToast(
                        context.getString(R.string.forum_compose_fixed_price_range, maxFixedPrice)
                    )
                    return
                }
                parsedFixed = v
            } else {
                val p = percentText.toDoubleOrNull()?.roundToInt() ?: 0
                if (p < 1 || p > PERCENT_MAX) {
                    toast.showToast(
                        context.getString(R.string.forum_compose_percent_range, PERCENT_MAX)
                    )
                    return
                }
                val floor = percentFloorText.toDoubleOrNull()?.roundToInt() ?: 0
                if (floor < 1 || floor > maxPercentFloor) {
                    toast.showToast(
                        context.getString(R.string.forum_compose_floor_range, maxPercentFloor)
                    )
                    return
                }
                parsedPercent = p
                parsedFloor = floor
            }
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
            // 正文首行拼元数据隐藏标志（显示端剥离）；forum_id 用当前板块
            val payload = ForumRepository.withMetaMarker(
                content.trim(),
                ForumPostMeta(
                    tags = selectedTags,
                    paid = paid,
                    priceMode = mode,
                    fixedPrice = parsedFixed,
                    percentFloor = parsedFloor,
                    percent = parsedPercent,
                    minLevel = minLevel
                )
            )
            val nickname = user.name.ifBlank { user.qq }
            val ok = withContext(Dispatchers.IO) {
                ForumRepository.submitPost(context, user.qq, nickname, forumId, t, payload, imgUrl)
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
            .imePadding()
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

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
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

            // 标签：自定义输入（# 触发补全、回车成标签、≤10 字）+ 词表两行横向网格
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Box {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text(stringResource(R.string.forum_compose_tags)) },
                        placeholder = { Text(stringResource(R.string.forum_compose_tag_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(themeRadius()),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addTag(tagInput) }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = tagMenuOpen && tagSuggestions.isNotEmpty(),
                        onDismissRequest = { tagMenuOpen = false }
                    ) {
                        tagSuggestions.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text("#$tag") },
                                onClick = { addTag(tag) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                ComposeTagGrid(
                    wordlist = tagWordlist,
                    selectedTags = selectedTags,
                    onToggleWord = { word ->
                        val on = selectedTags.any { it.equals(word, ignoreCase = true) }
                        selectedTags = if (on) {
                            selectedTags.filterNot { it.equals(word, ignoreCase = true) }
                        } else {
                            selectedTags + word
                        }
                    },
                    onRemoveCustom = { tag -> selectedTags = selectedTags - tag }
                )
            }

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.forum_compose_content)) },
                shape = RoundedCornerShape(themeRadius()),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
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

            // 付费设置：免费/付费 → 付费时展开 固定价/百分比 + 价格输入
            Text(
                text = stringResource(R.string.forum_compose_paid_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
            ConnectedSegments(
                options = listOf(
                    stringResource(R.string.forum_compose_free),
                    stringResource(R.string.forum_compose_paid)
                ),
                selectedIndex = if (paid) 1 else 0,
                onSelect = { paid = it == 1 },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            AnimatedVisibility(visible = paid) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ConnectedSegments(
                        options = listOf(
                            stringResource(R.string.forum_compose_price_fixed),
                            stringResource(R.string.forum_compose_price_percent)
                        ),
                        selectedIndex = if (priceMode == PRICE_MODE_PERCENT) 1 else 0,
                        onSelect = {
                            priceMode = if (it == 1) PRICE_MODE_PERCENT else PRICE_MODE_FIXED
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (priceMode == PRICE_MODE_FIXED) {
                        OutlinedTextField(
                            value = fixedPriceText,
                            onValueChange = { fixedPriceText = filterDecimal(it) },
                            label = { Text(stringResource(R.string.forum_compose_fixed_price)) },
                            singleLine = true,
                            supportingText = {
                                Text(stringResource(R.string.forum_compose_fixed_price_hint, maxFixedPrice))
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(themeRadius()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    } else {
                        OutlinedTextField(
                            value = percentText,
                            onValueChange = { percentText = filterDecimal(it) },
                            label = { Text(stringResource(R.string.forum_compose_percent)) },
                            singleLine = true,
                            supportingText = {
                                Text(stringResource(R.string.forum_compose_percent_hint, PERCENT_MAX))
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(themeRadius()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                        OutlinedTextField(
                            value = percentFloorText,
                            onValueChange = { percentFloorText = filterDecimal(it) },
                            label = { Text(stringResource(R.string.forum_compose_percent_floor)) },
                            singleLine = true,
                            supportingText = {
                                Text(stringResource(R.string.forum_compose_floor_hint, maxPercentFloor))
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(themeRadius()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }
            }

            // 可查看等级：Slider 0..自身等级（0=无限制）
            Text(
                text = stringResource(R.string.forum_compose_level_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            Text(
                text = if (minLevel == 0) {
                    stringResource(R.string.forum_compose_level_unlimited)
                } else {
                    stringResource(R.string.forum_compose_level_value, minLevel)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Slider(
                value = minLevel.toFloat(),
                onValueChange = { minLevel = it.roundToInt() },
                valueRange = 0f..maxOf(sellerLevel, 1).toFloat(),
                steps = (maxOf(sellerLevel, 1) - 1).coerceAtLeast(0),
                enabled = sellerLevel > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            if (sellerLevel <= 0) {
                Text(
                    text = stringResource(R.string.forum_compose_level_hint, sellerLevel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { doSubmit() },
            enabled = title.trim().isNotEmpty() && !submitting,
            shape = RoundedCornerShape(themeRadius()),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
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

/** 数字输入过滤：仅保留数字与小数点（价格允许小数，提交时四舍五入） */
private fun filterDecimal(raw: String): String =
    raw.filter { it.isDigit() || it == '.' }

/** 标签单元格：自定义标签（叉号删除）或词表标签（对勾勾选） */
private sealed interface TagCell {
    val tag: String
    val key: String

    data class Word(override val tag: String, val selected: Boolean) : TagCell {
        override val key: String get() = "w:$tag"
    }

    data class Custom(override val tag: String) : TagCell {
        override val key: String get() = "c:$tag"
    }
}

/**
 * 发帖标签区：自定义标签（不在词表内，叉号删除）置前 + 词表标签（对勾勾选）随后，
 * 合并为一个最多两行、横向滚动的网格。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComposeTagGrid(
    wordlist: List<String>,
    selectedTags: List<String>,
    onToggleWord: (String) -> Unit,
    onRemoveCustom: (String) -> Unit
) {
    val customTags = selectedTags.filter { sel -> wordlist.none { it.equals(sel, ignoreCase = true) } }
    val cells: List<TagCell> = buildList {
        customTags.forEach { add(TagCell.Custom(it)) }
        wordlist.forEach { word ->
            add(TagCell.Word(word, selectedTags.any { it.equals(word, ignoreCase = true) }))
        }
    }
    if (cells.isEmpty()) return
    LazyHorizontalGrid(
        rows = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items = cells, key = { it.key }) { cell ->
            when (cell) {
                is TagCell.Custom -> InputChip(
                    selected = true,
                    onClick = { onRemoveCustom(cell.tag) },
                    label = { Text("#${cell.tag}") },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.forum_compose_tag_remove),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(themeRadius())
                )

                is TagCell.Word -> FilterChip(
                    selected = cell.selected,
                    onClick = { onToggleWord(cell.tag) },
                    label = { Text(cell.tag) },
                    leadingIcon = if (cell.selected) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(themeRadius())
                )
            }
        }
    }
}

/**
 * MD3 connected button group（单选）：首/尾段为不对称圆角，选中段=对勾 + 色差
 * （不可只靠颜色区分）。点击已选段不取消（始终保有一段选中）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectedSegments(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, label ->
            ToggleButton(
                checked = selectedIndex == index,
                onCheckedChange = { checked -> if (checked) onSelect(index) },
                modifier = Modifier.weight(1f),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
            ) {
                if (selectedIndex == index) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ToggleButtonDefaults.IconSpacing))
                }
                Text(label)
            }
        }
    }
}
