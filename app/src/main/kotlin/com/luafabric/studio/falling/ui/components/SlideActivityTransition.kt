package com.luafabric.studio.falling.ui.components

import android.app.Activity
import android.os.Build
import androidx.annotation.AnimRes

/**
 * 左右滑入/滑出的 Activity 转场，且兼容预测性返回手势。
 *
 * Android 官方文档明确：`overridePendingTransition` 会让 Android 14+ 的预测性返回动画失效，
 * 自定义转场必须改用 `overrideActivityTransition`（转场才会随用户滑回的距离播放）。
 * 故 API 34+ 走 `overrideActivityTransition`；API <34 本就没有预测返回动画，V 由调用方用
 * `overridePendingTransition` 兜底（见 [finishWithSlide]）。
 *
 * @param enterAnim 本页从右侧滑入
 * @param exitAnim 下层页向左侧滑出
 * @param popEnterAnim 返回时下层页从左侧滑回
 * @param popExitAnim 返回时本页向右侧滑出
 */
fun Activity.configureSlideTransitions(
    @AnimRes enterAnim: Int,
    @AnimRes exitAnim: Int,
    @AnimRes popEnterAnim: Int,
    @AnimRes popExitAnim: Int
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, enterAnim, exitAnim)
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, popEnterAnim, popExitAnim)
    }
}

/** 关闭当前 Activity 并播放左右滑返回转场；API <34 需显式 `overridePendingTransition`。 */
fun Activity.finishWithSlide(@AnimRes popEnterAnim: Int, @AnimRes popExitAnim: Int) {
    finish()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        @Suppress("DEPRECATION")
        overridePendingTransition(popEnterAnim, popExitAnim)
    }
}

/** 启动新页时播放进入转场；API 34+ 由目标页 `overrideActivityTransition(OPEN)` 接管，无需此处设置。 */
fun Activity.applyOpenTransitionIfLegacy(@AnimRes enterAnim: Int, @AnimRes exitAnim: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        @Suppress("DEPRECATION")
        overridePendingTransition(enterAnim, exitAnim)
    }
}
