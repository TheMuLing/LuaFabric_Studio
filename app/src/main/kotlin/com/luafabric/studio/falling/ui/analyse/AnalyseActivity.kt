package com.luafabric.studio.falling.ui.analyse

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
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.components.Toast
import com.luafabric.studio.falling.ui.settings.SettingsManager
import com.luafabric.studio.falling.ui.theme.AppThemeWithObserver
import io.github.tarifchakder.ktoast.ToastData
import io.github.tarifchakder.ktoast.ToastHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import muling.views.tool.utils.rememberNonBlockingToastState

/**
 * 导入分析独立 Activity：一次返回键即关闭。
 * 左右滑动画：进入 slide_in_right/slide_out_left；返回 slide_in_left/slide_out_right；
 * API33+ 系统预测返回自动叠加。
 */
class AnalyseActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_CODE = "code_content"
        private const val EXTRA_PROJECT = "project_path"

        fun intent(context: Context, codeContent: String, projectPath: String?): Intent =
            Intent(context, AnalyseActivity::class.java)
                .putExtra(EXTRA_CODE, codeContent)
                .putExtra(EXTRA_PROJECT, projectPath.orEmpty())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

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
                        val toast = rememberNonBlockingToastState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            AnalyseScreen(
                                codeContent = intent.getStringExtra(EXTRA_CODE).orEmpty(),
                                projectPath = intent.getStringExtra(EXTRA_PROJECT)
                                    ?.takeIf { it.isNotBlank() },
                                onBack = { finishSliding() },
                                toast = toast
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

    private fun finishSliding() {
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        finish()
    }
}
