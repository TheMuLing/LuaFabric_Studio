package com.luafabric.studio.falling.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.luafabric.studio.falling.ui.settings.DarkMode
import com.luafabric.studio.falling.ui.settings.SettingsManager

enum class ThemeType {
    GREEN, PINK, BLUE
}

// 粉色主题色系
private val PinkLightColorScheme = lightColorScheme(
    primary = primaryLightPink,
    onPrimary = onPrimaryLightPink,
    primaryContainer = primaryContainerLightPink,
    onPrimaryContainer = onPrimaryContainerLightPink,
    secondary = secondaryLightPink,
    onSecondary = onSecondaryLightPink,
    secondaryContainer = secondaryContainerLightPink,
    onSecondaryContainer = onSecondaryContainerLightPink,
    tertiary = tertiaryLightPink,
    onTertiary = onTertiaryLightPink,
    tertiaryContainer = tertiaryContainerLightPink,
    onTertiaryContainer = onTertiaryContainerLightPink,
    error = errorLightPink,
    onError = onErrorLightPink,
    errorContainer = errorContainerLightPink,
    onErrorContainer = onErrorContainerLightPink,
    background = backgroundLightPink,
    onBackground = onBackgroundLightPink,
    surface = surfaceLightPink,
    onSurface = onSurfaceLightPink,
    surfaceVariant = surfaceVariantLightPink,
    onSurfaceVariant = onSurfaceVariantLightPink,
    outline = outlineLightPink,
    outlineVariant = outlineVariantLightPink,
    scrim = scrimLightPink,
    inverseSurface = inverseSurfaceLightPink,
    inverseOnSurface = inverseOnSurfaceLightPink,
    inversePrimary = inversePrimaryLightPink,
    surfaceDim = surfaceDimLightPink,
    surfaceBright = surfaceBrightLightPink,
    surfaceContainerLowest = surfaceContainerLowestLightPink,
    surfaceContainerLow = surfaceContainerLowLightPink,
    surfaceContainer = surfaceContainerLightPink,
    surfaceContainerHigh = surfaceContainerHighLightPink,
    surfaceContainerHighest = surfaceContainerHighestLightPink
)

private val PinkDarkColorScheme = darkColorScheme(
    primary = primaryDarkPink,
    onPrimary = onPrimaryDarkPink,
    primaryContainer = primaryContainerDarkPink,
    onPrimaryContainer = onPrimaryContainerDarkPink,
    secondary = secondaryDarkPink,
    onSecondary = onSecondaryDarkPink,
    secondaryContainer = secondaryContainerDarkPink,
    onSecondaryContainer = onSecondaryContainerDarkPink,
    tertiary = tertiaryDarkPink,
    onTertiary = onTertiaryDarkPink,
    tertiaryContainer = tertiaryContainerDarkPink,
    onTertiaryContainer = onTertiaryContainerDarkPink,
    error = errorDarkPink,
    onError = onErrorDarkPink,
    errorContainer = errorContainerDarkPink,
    onErrorContainer = onErrorContainerDarkPink,
    background = backgroundDarkPink,
    onBackground = onBackgroundDarkPink,
    surface = surfaceDarkPink,
    onSurface = onSurfaceDarkPink,
    surfaceVariant = surfaceVariantDarkPink,
    onSurfaceVariant = onSurfaceVariantDarkPink,
    outline = outlineDarkPink,
    outlineVariant = outlineVariantDarkPink,
    scrim = scrimDarkPink,
    inverseSurface = inverseSurfaceDarkPink,
    inverseOnSurface = inverseOnSurfaceDarkPink,
    inversePrimary = inversePrimaryDarkPink,
    surfaceDim = surfaceDimDarkPink,
    surfaceBright = surfaceBrightDarkPink,
    surfaceContainerLowest = surfaceContainerLowestDarkPink,
    surfaceContainerLow = surfaceContainerLowDarkPink,
    surfaceContainer = surfaceContainerDarkPink,
    surfaceContainerHigh = surfaceContainerHighDarkPink,
    surfaceContainerHighest = surfaceContainerHighestDarkPink
)

// 蓝色主题色系
private val BlueLightColorScheme = lightColorScheme(
    primary = primaryLightBlue,
    onPrimary = onPrimaryLightBlue,
    primaryContainer = primaryContainerLightBlue,
    onPrimaryContainer = onPrimaryContainerLightBlue,
    secondary = secondaryLightBlue,
    onSecondary = onSecondaryLightBlue,
    secondaryContainer = secondaryContainerLightBlue,
    onSecondaryContainer = onSecondaryContainerLightBlue,
    tertiary = tertiaryLightBlue,
    onTertiary = onTertiaryLightBlue,
    tertiaryContainer = tertiaryContainerLightBlue,
    onTertiaryContainer = onTertiaryContainerLightBlue,
    error = errorLightBlue,
    onError = onErrorLightBlue,
    errorContainer = errorContainerLightBlue,
    onErrorContainer = onErrorContainerLightBlue,
    background = backgroundLightBlue,
    onBackground = onBackgroundLightBlue,
    surface = surfaceLightBlue,
    onSurface = onSurfaceLightBlue,
    surfaceVariant = surfaceVariantLightBlue,
    onSurfaceVariant = onSurfaceVariantLightBlue,
    outline = outlineLightBlue,
    outlineVariant = outlineVariantLightBlue,
    scrim = scrimLightBlue,
    inverseSurface = inverseSurfaceLightBlue,
    inverseOnSurface = inverseOnSurfaceLightBlue,
    inversePrimary = inversePrimaryLightBlue,
    surfaceDim = surfaceDimLightBlue,
    surfaceBright = surfaceBrightLightBlue,
    surfaceContainerLowest = surfaceContainerLowestLightBlue,
    surfaceContainerLow = surfaceContainerLowLightBlue,
    surfaceContainer = surfaceContainerLightBlue,
    surfaceContainerHigh = surfaceContainerHighLightBlue,
    surfaceContainerHighest = surfaceContainerHighestLightBlue
)

private val BlueDarkColorScheme = darkColorScheme(
    primary = primaryDarkBlue,
    onPrimary = onPrimaryDarkBlue,
    primaryContainer = primaryContainerDarkBlue,
    onPrimaryContainer = onPrimaryContainerDarkBlue,
    secondary = secondaryDarkBlue,
    onSecondary = onSecondaryDarkBlue,
    secondaryContainer = secondaryContainerDarkBlue,
    onSecondaryContainer = onSecondaryContainerDarkBlue,
    tertiary = tertiaryDarkBlue,
    onTertiary = onTertiaryDarkBlue,
    tertiaryContainer = tertiaryContainerDarkBlue,
    onTertiaryContainer = onTertiaryContainerDarkBlue,
    error = errorDarkBlue,
    onError = onErrorDarkBlue,
    errorContainer = errorContainerDarkBlue,
    onErrorContainer = onErrorContainerDarkBlue,
    background = backgroundDarkBlue,
    onBackground = onBackgroundDarkBlue,
    surface = surfaceDarkBlue,
    onSurface = onSurfaceDarkBlue,
    surfaceVariant = surfaceVariantDarkBlue,
    onSurfaceVariant = onSurfaceVariantDarkBlue,
    outline = outlineDarkBlue,
    outlineVariant = outlineVariantDarkBlue,
    scrim = scrimDarkBlue,
    inverseSurface = inverseSurfaceDarkBlue,
    inverseOnSurface = inverseOnSurfaceDarkBlue,
    inversePrimary = inversePrimaryDarkBlue,
    surfaceDim = surfaceDimDarkBlue,
    surfaceBright = surfaceBrightDarkBlue,
    surfaceContainerLowest = surfaceContainerLowestDarkBlue,
    surfaceContainerLow = surfaceContainerLowDarkBlue,
    surfaceContainer = surfaceContainerDarkBlue,
    surfaceContainerHigh = surfaceContainerHighDarkBlue,
    surfaceContainerHighest = surfaceContainerHighestDarkBlue
)

// 绿色主题色系
private val GreenLightColorScheme = lightColorScheme(
    primary = primaryLightGreen,
    onPrimary = onPrimaryLightGreen,
    primaryContainer = primaryContainerLightGreen,
    onPrimaryContainer = onPrimaryContainerLightGreen,
    secondary = secondaryLightGreen,
    onSecondary = onSecondaryLightGreen,
    secondaryContainer = secondaryContainerLightGreen,
    onSecondaryContainer = onSecondaryContainerLightGreen,
    tertiary = tertiaryLightGreen,
    onTertiary = onTertiaryLightGreen,
    tertiaryContainer = tertiaryContainerLightGreen,
    onTertiaryContainer = onTertiaryContainerLightGreen,
    error = errorLightGreen,
    onError = onErrorLightGreen,
    errorContainer = errorContainerLightGreen,
    onErrorContainer = onErrorContainerLightGreen,
    background = backgroundLightGreen,
    onBackground = onBackgroundLightGreen,
    surface = surfaceLightGreen,
    onSurface = onSurfaceLightGreen,
    surfaceVariant = surfaceVariantLightGreen,
    onSurfaceVariant = onSurfaceVariantLightGreen,
    outline = outlineLightGreen,
    outlineVariant = outlineVariantLightGreen,
    scrim = scrimLightGreen,
    inverseSurface = inverseSurfaceLightGreen,
    inverseOnSurface = inverseOnSurfaceLightGreen,
    inversePrimary = inversePrimaryLightGreen,
    surfaceDim = surfaceDimLightGreen,
    surfaceBright = surfaceBrightLightGreen,
    surfaceContainerLowest = surfaceContainerLowestLightGreen,
    surfaceContainerLow = surfaceContainerLowLightGreen,
    surfaceContainer = surfaceContainerLightGreen,
    surfaceContainerHigh = surfaceContainerHighLightGreen,
    surfaceContainerHighest = surfaceContainerHighestLightGreen
)

private val GreenDarkColorScheme = darkColorScheme(
    primary = primaryDarkGreen,
    onPrimary = onPrimaryDarkGreen,
    primaryContainer = primaryContainerDarkGreen,
    onPrimaryContainer = onPrimaryContainerDarkGreen,
    secondary = secondaryDarkGreen,
    onSecondary = onSecondaryDarkGreen,
    secondaryContainer = secondaryContainerDarkGreen,
    onSecondaryContainer = onSecondaryContainerDarkGreen,
    tertiary = tertiaryDarkGreen,
    onTertiary = onTertiaryDarkGreen,
    tertiaryContainer = tertiaryContainerDarkGreen,
    onTertiaryContainer = onTertiaryContainerDarkGreen,
    error = errorDarkGreen,
    onError = onErrorDarkGreen,
    errorContainer = errorContainerDarkGreen,
    onErrorContainer = onErrorContainerDarkGreen,
    background = backgroundDarkGreen,
    onBackground = onBackgroundDarkGreen,
    surface = surfaceDarkGreen,
    onSurface = onSurfaceDarkGreen,
    surfaceVariant = surfaceVariantDarkGreen,
    onSurfaceVariant = onSurfaceVariantDarkGreen,
    outline = outlineDarkGreen,
    outlineVariant = outlineVariantDarkGreen,
    scrim = scrimDarkGreen,
    inverseSurface = inverseSurfaceDarkGreen,
    inverseOnSurface = inverseOnSurfaceDarkGreen,
    inversePrimary = inversePrimaryDarkGreen,
    surfaceDim = surfaceDimDarkGreen,
    surfaceBright = surfaceBrightDarkGreen,
    surfaceContainerLowest = surfaceContainerLowestDarkGreen,
    surfaceContainerLow = surfaceContainerLowDarkGreen,
    surfaceContainer = surfaceContainerDarkGreen,
    surfaceContainerHigh = surfaceContainerHighDarkGreen,
    surfaceContainerHighest = surfaceContainerHighestDarkGreen
)

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color
)

val unspecifiedScheme = ColorFamily(
    Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified
)

/**
 * 带观察者的AppTheme，自动监听设置变化
 * 支持颜色、形状和字体大小的过渡动画
 */
@Composable
fun AppThemeWithObserver(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 获取当前设置
    val currentSettings = SettingsManager.currentSettings

    // 根据darkMode设置决定是否使用暗色主题
    val useDarkTheme = when (currentSettings.darkMode) {
        DarkMode.FOLLOW_SYSTEM -> darkTheme
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        else -> darkTheme
    }

    // 根据设置选择颜色方案
    val targetColorScheme = when {
        currentSettings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        useDarkTheme -> when (currentSettings.themeType) {
            ThemeType.PINK -> PinkDarkColorScheme
            ThemeType.BLUE -> BlueDarkColorScheme
            ThemeType.GREEN -> GreenDarkColorScheme
        }

        else -> when (currentSettings.themeType) {
            ThemeType.PINK -> PinkLightColorScheme
            ThemeType.BLUE -> BlueLightColorScheme
            ThemeType.GREEN -> GreenLightColorScheme
        }
    }

    // 为颜色切换添加动画
    val animatedColorScheme = targetColorScheme.copy(
        // 主要颜色
        primary = animateColorAsState(
            targetColorScheme.primary,
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        ).value,
        onPrimary = animateColorAsState(
            targetColorScheme.onPrimary,
            animationSpec = tween(600)
        ).value,
        primaryContainer = animateColorAsState(
            targetColorScheme.primaryContainer,
            animationSpec = tween(600)
        ).value,
        onPrimaryContainer = animateColorAsState(
            targetColorScheme.onPrimaryContainer,
            animationSpec = tween(600)
        ).value,

        // 次要颜色
        secondary = animateColorAsState(
            targetColorScheme.secondary,
            animationSpec = tween(600)
        ).value,
        onSecondary = animateColorAsState(
            targetColorScheme.onSecondary,
            animationSpec = tween(600)
        ).value,
        secondaryContainer = animateColorAsState(
            targetColorScheme.secondaryContainer,
            animationSpec = tween(600)
        ).value,
        onSecondaryContainer = animateColorAsState(
            targetColorScheme.onSecondaryContainer,
            animationSpec = tween(600)
        ).value,

        // 背景
        background = animateColorAsState(
            targetColorScheme.background,
            animationSpec = tween(600)
        ).value,
        onBackground = animateColorAsState(
            targetColorScheme.onBackground,
            animationSpec = tween(600)
        ).value,
        surface = animateColorAsState(targetColorScheme.surface, animationSpec = tween(600)).value,
        onSurface = animateColorAsState(
            targetColorScheme.onSurface,
            animationSpec = tween(600)
        ).value,

        // 变体颜色
        surfaceVariant = animateColorAsState(
            targetColorScheme.surfaceVariant,
            animationSpec = tween(600)
        ).value,
        onSurfaceVariant = animateColorAsState(
            targetColorScheme.onSurfaceVariant,
            animationSpec = tween(600)
        ).value,
        outline = animateColorAsState(targetColorScheme.outline, animationSpec = tween(600)).value,
        outlineVariant = animateColorAsState(
            targetColorScheme.outlineVariant,
            animationSpec = tween(600)
        ).value,

        // 错误颜色
        error = animateColorAsState(targetColorScheme.error, animationSpec = tween(600)).value,
        onError = animateColorAsState(targetColorScheme.onError, animationSpec = tween(600)).value,
        errorContainer = animateColorAsState(
            targetColorScheme.errorContainer,
            animationSpec = tween(600)
        ).value,
        onErrorContainer = animateColorAsState(
            targetColorScheme.onErrorContainer,
            animationSpec = tween(600)
        ).value,

        // 其他颜色
        scrim = animateColorAsState(targetColorScheme.scrim, animationSpec = tween(600)).value,
        inverseSurface = animateColorAsState(
            targetColorScheme.inverseSurface,
            animationSpec = tween(600)
        ).value,
        inverseOnSurface = animateColorAsState(
            targetColorScheme.inverseOnSurface,
            animationSpec = tween(600)
        ).value,
        inversePrimary = animateColorAsState(
            targetColorScheme.inversePrimary,
            animationSpec = tween(600)
        ).value,

        // Material 3 表面层级颜色
        surfaceDim = animateColorAsState(
            targetColorScheme.surfaceDim,
            animationSpec = tween(600)
        ).value,
        surfaceBright = animateColorAsState(
            targetColorScheme.surfaceBright,
            animationSpec = tween(600)
        ).value,
        surfaceContainerLowest = animateColorAsState(
            targetColorScheme.surfaceContainerLowest,
            animationSpec = tween(600)
        ).value,
        surfaceContainerLow = animateColorAsState(
            targetColorScheme.surfaceContainerLow,
            animationSpec = tween(600)
        ).value,
        surfaceContainer = animateColorAsState(
            targetColorScheme.surfaceContainer,
            animationSpec = tween(600)
        ).value,
        surfaceContainerHigh = animateColorAsState(
            targetColorScheme.surfaceContainerHigh,
            animationSpec = tween(600)
        ).value,
        surfaceContainerHighest = animateColorAsState(
            targetColorScheme.surfaceContainerHighest,
            animationSpec = tween(600)
        ).value,

        // 三级颜色
        tertiary = animateColorAsState(
            targetColorScheme.tertiary,
            animationSpec = tween(600)
        ).value,
        onTertiary = animateColorAsState(
            targetColorScheme.onTertiary,
            animationSpec = tween(600)
        ).value,
        tertiaryContainer = animateColorAsState(
            targetColorScheme.tertiaryContainer,
            animationSpec = tween(600)
        ).value,
        onTertiaryContainer = animateColorAsState(
            targetColorScheme.onTertiaryContainer,
            animationSpec = tween(600)
        ).value
    )

    // 创建带动画的形状和字体
    val dynamicShapes = createDynamicShapes(currentSettings.shapeSizeIndex)
    val dynamicTypography =
        createDynamicTypography(currentSettings.fontSizeScale, currentSettings.fontFamilyType)

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = dynamicTypography,
        shapes = dynamicShapes,
        content = content
    )
}