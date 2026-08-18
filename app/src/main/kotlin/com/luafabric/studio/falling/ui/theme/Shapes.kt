package com.luafabric.studio.falling.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

/**
 * LuaFabric Studio 形状系统
 * Material 3 形状规范
 */

/**
 * 创建根据设置变化的Shapes（带动画）
 */
@Composable
fun createDynamicShapes(shapeSizeIndex: Int): Shapes {
    // 目标尺寸（dp值）
    val targetBaseSize = when (shapeSizeIndex) {
        0 -> 4f  // 小
        1 -> 8f  // 中小
        2 -> 12f // 中（默认）
        3 -> 16f // 大
        else -> 12f
    }

    // 创建 Animatable 对象来驱动动画
    val animatedBaseSize = remember { Animatable(targetBaseSize) }

    // 当目标尺寸变化时，启动动画
    LaunchedEffect(targetBaseSize) {
        animatedBaseSize.animateTo(
            targetValue = targetBaseSize,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // 将动画值转换为dp
    val baseSize = animatedBaseSize.value.dp

    return Shapes(
        extraSmall = RoundedCornerShape(baseSize * 0.33f),
        small = RoundedCornerShape(baseSize * 0.67f),
        medium = RoundedCornerShape(baseSize),
        large = RoundedCornerShape(baseSize * 1.33f),
        extraLarge = RoundedCornerShape(baseSize * 2.33f)
    )
}