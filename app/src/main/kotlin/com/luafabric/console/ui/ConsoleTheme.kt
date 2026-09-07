package com.luafabric.console.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.luafabric.studio.falling.ui.settings.DarkMode
import com.luafabric.studio.falling.ui.settings.SettingsManager
import com.luafabric.studio.falling.ui.theme.ThemeType
import com.luafabric.studio.falling.ui.theme.onSurfaceDarkBlue
import com.luafabric.studio.falling.ui.theme.onSurfaceDarkGreen
import com.luafabric.studio.falling.ui.theme.onSurfaceDarkPink
import com.luafabric.studio.falling.ui.theme.onSurfaceLightBlue
import com.luafabric.studio.falling.ui.theme.onSurfaceLightGreen
import com.luafabric.studio.falling.ui.theme.onSurfaceLightPink
import com.luafabric.studio.falling.ui.theme.onSurfaceVariantDarkBlue
import com.luafabric.studio.falling.ui.theme.onSurfaceVariantDarkGreen
import com.luafabric.studio.falling.ui.theme.onSurfaceVariantDarkPink
import com.luafabric.studio.falling.ui.theme.onSurfaceVariantLightBlue
import com.luafabric.studio.falling.ui.theme.onSurfaceVariantLightGreen
import com.luafabric.studio.falling.ui.theme.onSurfaceVariantLightPink
import com.luafabric.studio.falling.ui.theme.primaryContainerDarkBlue
import com.luafabric.studio.falling.ui.theme.primaryContainerDarkGreen
import com.luafabric.studio.falling.ui.theme.primaryContainerDarkPink
import com.luafabric.studio.falling.ui.theme.primaryContainerLightBlue
import com.luafabric.studio.falling.ui.theme.primaryContainerLightGreen
import com.luafabric.studio.falling.ui.theme.primaryContainerLightPink
import com.luafabric.studio.falling.ui.theme.primaryDarkBlue
import com.luafabric.studio.falling.ui.theme.primaryDarkGreen
import com.luafabric.studio.falling.ui.theme.primaryDarkPink
import com.luafabric.studio.falling.ui.theme.primaryLightBlue
import com.luafabric.studio.falling.ui.theme.primaryLightGreen
import com.luafabric.studio.falling.ui.theme.primaryLightPink
import com.luafabric.studio.falling.ui.theme.surfaceDarkBlue
import com.luafabric.studio.falling.ui.theme.surfaceDarkGreen
import com.luafabric.studio.falling.ui.theme.surfaceDarkPink
import com.luafabric.studio.falling.ui.theme.surfaceLightBlue
import com.luafabric.studio.falling.ui.theme.surfaceLightGreen
import com.luafabric.studio.falling.ui.theme.surfaceLightPink

/**
 * 控制台主题：浮球/浮窗及全部控件颜色遵循 luafabric「主题与外观」配置
 * （themeType GREEN/PINK/BLUE + darkMode + dynamicColor）。
 * 非 Compose 环境取色：动态取色走 material3 dynamic scheme，静态走 Color.kt 色板。
 * 使用前须 refresh()（OverlayController.showBall/openSheet 已调用）。
 */
object ConsoleTheme {

    var primary: Int = 0xFF3D5AFE.toInt()
        private set
    var onPrimary: Int = Color.WHITE
        private set
    var surface: Int = Color.WHITE
        private set
    var onSurface: Int = 0xFF222222.toInt()
        private set
    var onSurfaceVariant: Int = 0xFF777777.toInt()
        private set
    /** 强调浅底：选中行 / 操作按钮背景。 */
    var accentContainer: Int = 0xFFE3EDFF.toInt()
        private set
    var isDark: Boolean = false
        private set

    fun refresh(context: Context) {
        val s = SettingsManager.currentSettings
        val dark = when (s.darkMode) {
            DarkMode.FOLLOW_SYSTEM -> isSystemDark(context)
            DarkMode.LIGHT -> false
            DarkMode.DARK -> true
            else -> isSystemDark(context)
        }
        isDark = dark

        if (s.dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                primary = scheme.primary.toArgb()
                onPrimary = scheme.onPrimary.toArgb()
                surface = scheme.surface.toArgb()
                onSurface = scheme.onSurface.toArgb()
                onSurfaceVariant = scheme.onSurfaceVariant.toArgb()
                accentContainer = scheme.primaryContainer.toArgb()
                return
            } catch (_: Exception) {
                // 动态取色失败 → 回退静态色板
            }
        }

        when (s.themeType) {
            ThemeType.GREEN -> if (dark) {
                primary = primaryDarkGreen.toArgb(); surface = surfaceDarkGreen.toArgb()
                onSurface = onSurfaceDarkGreen.toArgb(); onSurfaceVariant = onSurfaceVariantDarkGreen.toArgb()
                accentContainer = primaryContainerDarkGreen.toArgb()
            } else {
                primary = primaryLightGreen.toArgb(); surface = surfaceLightGreen.toArgb()
                onSurface = onSurfaceLightGreen.toArgb(); onSurfaceVariant = onSurfaceVariantLightGreen.toArgb()
                accentContainer = primaryContainerLightGreen.toArgb()
            }
            ThemeType.PINK -> if (dark) {
                primary = primaryDarkPink.toArgb(); surface = surfaceDarkPink.toArgb()
                onSurface = onSurfaceDarkPink.toArgb(); onSurfaceVariant = onSurfaceVariantDarkPink.toArgb()
                accentContainer = primaryContainerDarkPink.toArgb()
            } else {
                primary = primaryLightPink.toArgb(); surface = surfaceLightPink.toArgb()
                onSurface = onSurfaceLightPink.toArgb(); onSurfaceVariant = onSurfaceVariantLightPink.toArgb()
                accentContainer = primaryContainerLightPink.toArgb()
            }
            ThemeType.BLUE -> if (dark) {
                primary = primaryDarkBlue.toArgb(); surface = surfaceDarkBlue.toArgb()
                onSurface = onSurfaceDarkBlue.toArgb(); onSurfaceVariant = onSurfaceVariantDarkBlue.toArgb()
                accentContainer = primaryContainerDarkBlue.toArgb()
            } else {
                primary = primaryLightBlue.toArgb(); surface = surfaceLightBlue.toArgb()
                onSurface = onSurfaceLightBlue.toArgb(); onSurfaceVariant = onSurfaceVariantLightBlue.toArgb()
                accentContainer = primaryContainerLightBlue.toArgb()
            }
        }
    }

    private fun isSystemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
