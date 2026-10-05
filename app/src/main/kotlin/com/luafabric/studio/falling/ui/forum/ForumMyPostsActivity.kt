package com.luafabric.studio.falling.ui.forum

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.components.configureSlideTransitions
import com.luafabric.studio.falling.ui.components.finishWithSlide

/**
 * 我的帖子页独立 Activity：本地合并 1/2 板块 ForumList，筛作者 == 当前登录 QQ，按发帖时间倒序。
 * 左右滑动画：进入 slide_in_right/slide_out_left；返回 slide_in_left/slide_out_right。
 * API33+ 系统预测返回（manifest enableOnBackInvokedCallback）自动叠加左右滑动预览。
 * 未登录返回 RESULT_LOGIN_REQUIRED，由账户页兜底跳登录。
 */
class ForumMyPostsActivity : ComponentActivity() {

    companion object {
        /** 未登录：账户页据此接管登录流程 */
        const val RESULT_LOGIN_REQUIRED = 3001

        fun intent(context: Context): Intent =
            Intent(context, ForumMyPostsActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        configureSlideTransitions(
            R.anim.slide_in_right,
            R.anim.slide_out_left,
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )

        setContent {
            ForumPostListActivityContent(
                title = stringResource(R.string.profile_my_posts),
                load = { ctx, qq ->
                    val merged = ForumRepository.loadPosts(ctx, 1) +
                        ForumRepository.loadPosts(ctx, 2)
                    ForumRepository.sortByTimeDesc(merged.filter { it.qq == qq })
                },
                onLoginRequired = {
                    setResult(RESULT_LOGIN_REQUIRED)
                    finishSliding()
                },
                onBack = { finishSliding() }
            )
        }
    }

    /** 关闭：当前页右滑出 + 下层页左滑回位（API<34 显式转场；34+ 由 CLOSE 转场接管） */
    private fun finishSliding() {
        finishWithSlide(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
