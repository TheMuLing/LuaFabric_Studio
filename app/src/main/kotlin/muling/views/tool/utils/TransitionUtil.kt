@file:OptIn(ExperimentalAnimationApi::class)

package muling.views.tool.utils

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import com.luafabric.studio.falling.ui.settings.ToastPosition

object TransitionUtil {

    /**
     * 获取页面切换动画（水平滑动 + 淡入淡出）
     * @param currentIndex 当前页面索引
     * @param targetIndex 目标页面索引
     * @param duration 动画时长，默认300ms
     */
    fun createPageTransition(
        currentIndex: Int,
        targetIndex: Int,
        duration: Int = 300
    ): ContentTransform {
        val isForward = targetIndex > currentIndex

        return if (isForward) {
            // 向前切换（从左边进入，右边退出）
            (slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(duration)
            ) + fadeIn(
                animationSpec = tween(duration)
            )).togetherWith(
                slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(duration)
                    ) + fadeOut(
                        animationSpec = tween(duration)
                    )
            )
        } else {
            // 向后切换（从右边进入，左边退出）
            (slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(duration)
            ) + fadeIn(
                animationSpec = tween(duration)
            )).togetherWith(
                slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(duration)
                    ) + fadeOut(
                        animationSpec = tween(duration)
                    )
            )
        }
    }

    val decelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val accelerateEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    /**
     * FAB展开/折叠动画
     */
    fun createFABTransition(
        duration: Int = 200
    ): EnterTransition {
        return fadeIn(
            animationSpec = tween(duration, easing = LinearEasing)
        ) + scaleIn(
            initialScale = 0.8f,
            animationSpec = tween(duration, easing = decelerateEasing)
        )
    }

    fun createFABExitTransition(
        duration: Int = 200
    ): ExitTransition {
        return fadeOut(
            animationSpec = tween(duration, easing = LinearEasing)
        ) + scaleOut(
            targetScale = 0.8f,
            animationSpec = tween(duration, easing = accelerateEasing)
        )
    }

    fun createScreenTransition(
        isForward: Boolean,
        duration: Int = 350,
        easing: CubicBezierEasing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f)
    ): ContentTransform {
        return if (isForward) {
            // 进入动画（前往新页面）
            (slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(duration, easing = easing)
            ) + fadeIn(
                animationSpec = tween(duration, easing = easing)
            )).togetherWith(
                slideOutHorizontally(
                                targetOffsetX = { -(it * 0.3f).toInt() },
                                animationSpec = tween(duration, easing = easing)
                            ) + fadeOut(
                        targetAlpha = 0.7f,
                        animationSpec = tween(duration, easing = easing)
                    )
            )
        } else {
            // 返回动画（侧滑返回）
            (slideInHorizontally(
                initialOffsetX = { -(it * 0.3f).toInt() },
                animationSpec = tween(duration, easing = easing)
            ) + fadeIn(
                initialAlpha = 0.7f,
                animationSpec = tween(duration, easing = easing)
            ) + scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(duration, easing = easing)
            )).togetherWith(
                slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(duration, easing = easing)
                            ) + fadeOut(
                        targetAlpha = 0f,
                        animationSpec = tween(duration, easing = easing)
                    ) + scaleOut(
                        targetScale = 1.05f,
                        animationSpec = tween(duration, easing = easing)
                    )
            )
        }
    }

    /**
     * 创建带有方向的 Toast 缩放动画（缩放 + 淡入淡出）
     * 进入时从 0 放大到 1，退出时从 1 缩小到 0，缩放中心根据位置变化
     * @param position 弹出位置（顶部或底部）
     * @param duration 动画时长，默认300ms
     */
    fun createToastPositionedScaleTransition(
        position: ToastPosition,
        duration: Int = 300
    ): ContentTransform {
        val transformOrigin = when (position) {
            ToastPosition.TOP -> TransformOrigin(0.5f, 0f)   // 顶部中心
            ToastPosition.BOTTOM -> TransformOrigin(0.5f, 1f) // 底部中心
        }
        return scaleIn(
            initialScale = 0f,
            transformOrigin = transformOrigin,
            animationSpec = tween(duration)
        ) + fadeIn(
            animationSpec = tween(duration)
        ) togetherWith scaleOut(
            targetScale = 0f,
            transformOrigin = transformOrigin,
            animationSpec = tween(duration)
        ) + fadeOut(
            animationSpec = tween(duration)
        )
    }




}