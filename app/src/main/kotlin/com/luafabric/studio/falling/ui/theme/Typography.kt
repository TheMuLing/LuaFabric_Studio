package com.luafabric.studio.falling.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.settings.FontFamilyType

/**
 * LuaFabric Studio 排版系统
 * Material 3 排版规范
 */

// 定义 Josefin Sans 字体族
val JosefinSansFontFamily = FontFamily(
    Font(R.font.josefin_sans)
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * 创建根据设置变化的Typography（带动画）
 */
@Composable
fun createDynamicTypography(
    fontSizeScale: Float,
    fontFamilyType: FontFamilyType
): Typography {
    // 创建 Animatable 对象来驱动动画
    val animatedFontSizeScale = remember { Animatable(fontSizeScale) }

    // 当字体缩放比例变化时，启动动画
    LaunchedEffect(fontSizeScale) {
        animatedFontSizeScale.animateTo(
            targetValue = fontSizeScale,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    // 根据设置中的fontFamilyType选择字体
    val fontFamily = when (fontFamilyType) {
        FontFamilyType.DEFAULT -> FontFamily.Default
        FontFamilyType.SANS_SERIF -> FontFamily.SansSerif
        FontFamilyType.SERIF -> FontFamily.Serif
        FontFamilyType.MONOSPACE -> FontFamily.Monospace
        FontFamilyType.JOSEFIN_SANS -> JosefinSansFontFamily
    }

    // 基础字体大小，乘以缩放系数
    return Typography(
        displayLarge = AppTypography.displayLarge.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.displayLarge.fontSize * animatedFontSizeScale.value
        ),
        displayMedium = AppTypography.displayMedium.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.displayMedium.fontSize * animatedFontSizeScale.value
        ),
        displaySmall = AppTypography.displaySmall.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.displaySmall.fontSize * animatedFontSizeScale.value
        ),
        headlineLarge = AppTypography.headlineLarge.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.headlineLarge.fontSize * animatedFontSizeScale.value
        ),
        headlineMedium = AppTypography.headlineMedium.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.headlineMedium.fontSize * animatedFontSizeScale.value
        ),
        headlineSmall = AppTypography.headlineSmall.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.headlineSmall.fontSize * animatedFontSizeScale.value
        ),
        titleLarge = AppTypography.titleLarge.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.titleLarge.fontSize * animatedFontSizeScale.value
        ),
        titleMedium = AppTypography.titleMedium.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.titleMedium.fontSize * animatedFontSizeScale.value
        ),
        titleSmall = AppTypography.titleSmall.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.titleSmall.fontSize * animatedFontSizeScale.value
        ),
        bodyLarge = AppTypography.bodyLarge.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.bodyLarge.fontSize * animatedFontSizeScale.value
        ),
        bodyMedium = AppTypography.bodyMedium.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.bodyMedium.fontSize * animatedFontSizeScale.value
        ),
        bodySmall = AppTypography.bodySmall.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.bodySmall.fontSize * animatedFontSizeScale.value
        ),
        labelLarge = AppTypography.labelLarge.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.labelLarge.fontSize * animatedFontSizeScale.value
        ),
        labelMedium = AppTypography.labelMedium.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.labelMedium.fontSize * animatedFontSizeScale.value
        ),
        labelSmall = AppTypography.labelSmall.copy(
            fontFamily = fontFamily,
            fontSize = AppTypography.labelSmall.fontSize * animatedFontSizeScale.value
        )
    )
}
