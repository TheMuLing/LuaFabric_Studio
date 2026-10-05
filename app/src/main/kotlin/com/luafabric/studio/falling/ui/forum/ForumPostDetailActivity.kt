package com.luafabric.studio.falling.ui.forum

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.google.gson.Gson
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.components.Toast
import com.luafabric.studio.falling.ui.components.configureSlideTransitions
import com.luafabric.studio.falling.ui.components.finishWithSlide
import com.luafabric.studio.falling.ui.login.LoginStore
import com.luafabric.studio.falling.ui.settings.SettingsManager
import com.luafabric.studio.falling.ui.theme.AppThemeWithObserver
import io.github.tarifchakder.ktoast.ToastData
import io.github.tarifchakder.ktoast.ToastHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import muling.views.tool.utils.rememberNonBlockingToastState

/**
 * 帖子详情页独立 Activity：一次返回键即关闭（不再先退回项目页）。
 * 左右滑动画：进入 slide_in_right/slide_out_left；返回 slide_in_left/slide_out_right。
 * API33+ 系统预测返回（manifest enableOnBackInvokedCallback）自动叠加左右滑动预览。
 * 未登录互动时返回 RESULT_LOGIN_REQUIRED，由列表页切到账户界面。
 */
class ForumPostDetailActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_POST = "post"

        /** 未登录跳账户：列表页据返回码切账户界面 */
        const val RESULT_LOGIN_REQUIRED = 1001

        /** 点标签回填搜索：列表页据返回码取 EXTRA_SEARCH_TAG 回填并搜 */
        const val RESULT_SEARCH_TAG = 1002
        const val EXTRA_SEARCH_TAG = "search_tag"

        fun intent(context: Context, post: ForumItem): Intent =
            Intent(context, ForumPostDetailActivity::class.java)
                .putExtra(EXTRA_POST, Gson().toJson(post))
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
                        val post = remember {
                            Gson().fromJson(intent.getStringExtra(EXTRA_POST), ForumItem::class.java)
                        }
                        val activeUser = remember { LoginStore.read(context).user }
                        val toast = rememberNonBlockingToastState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            ForumPostDetailScreen(
                                post = post,
                                activeUser = activeUser,
                                toast = toast,
                                onRequireLogin = {
                                    setResult(RESULT_LOGIN_REQUIRED)
                                    finishSliding()
                                },
                                onTagClick = { tag ->
                                    setResult(
                                        RESULT_SEARCH_TAG,
                                        Intent().putExtra(EXTRA_SEARCH_TAG, "#$tag")
                                    )
                                    finishSliding()
                                },
                                onBack = { finishSliding() }
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
