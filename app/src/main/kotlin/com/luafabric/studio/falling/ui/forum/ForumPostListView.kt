package com.luafabric.studio.falling.ui.forum

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.components.Toast
import com.luafabric.studio.falling.ui.components.applyOpenTransitionIfLegacy
import com.luafabric.studio.falling.ui.login.LoginStore
import com.luafabric.studio.falling.ui.login.YunJuResponse
import com.luafabric.studio.falling.ui.settings.SettingsManager
import com.luafabric.studio.falling.ui.theme.AppThemeWithObserver
import io.github.tarifchakder.ktoast.ToastData
import io.github.tarifchakder.ktoast.ToastHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import muling.views.tool.utils.NonBlockingToastState
import muling.views.tool.utils.rememberNonBlockingToastState

/**
 * 帖子列表 Activity 通用内容：「我的帖子」「已购帖子」两页共用（标题 + 数据加载器由各自 Activity 注入）。
 * 负责设置加载遮罩、登录拦截、加载中/空态、帖子卡片列表与 Toast 宿主。
 */
@Composable
internal fun ForumPostListActivityContent(
    title: String,
    load: suspend (Context, String) -> List<ForumItem>,
    onLoginRequired: () -> Unit,
    onBack: () -> Unit
) {
    var settingsLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { SettingsManager.loadSavedSettings(context) }
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
                var posts by remember { mutableStateOf<List<ForumItem>>(emptyList()) }
                var loading by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    val qq = activeUser?.qq.orEmpty()
                    if (qq.isBlank()) {
                        onLoginRequired()
                        return@LaunchedEffect
                    }
                    posts = withContext(Dispatchers.IO) { load(context, qq) }
                    loading = false
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    ForumPostListPage(
                        title = title,
                        posts = posts,
                        loading = loading,
                        activeUser = activeUser,
                        toast = toast,
                        onRequireLogin = onLoginRequired,
                        onOpenDetail = { post ->
                            context.startActivity(ForumPostDetailActivity.intent(context, post))
                            (context as? android.app.Activity)?.applyOpenTransitionIfLegacy(
                                R.anim.slide_in_right,
                                R.anim.slide_out_left
                            )
                        },
                        onBack = onBack
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * 帖子列表通用页：「我的帖子」「已购帖子」共用。顶栏返回 + 标题，
 * 列表项复用论坛帖子卡片；加载中转圈、空列表居中提示。
 */
@Composable
internal fun ForumPostListPage(
    title: String,
    posts: List<ForumItem>,
    loading: Boolean,
    activeUser: YunJuResponse?,
    toast: NonBlockingToastState,
    onRequireLogin: () -> Unit,
    onOpenDetail: (ForumItem) -> Unit,
    onBack: () -> Unit
) {
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
                text = title,
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

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                posts.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.forum_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(posts) { post ->
                        ForumPostCard(
                            post = post,
                            activeUser = activeUser,
                            toast = toast,
                            onRequireLogin = onRequireLogin,
                            onOpenDetail = { onOpenDetail(post) },
                            onTagClick = { onOpenDetail(post) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}
