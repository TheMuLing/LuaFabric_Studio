package com.luafabric.studio.falling.ui.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.luafabric.studio.falling.ui.settings.SettingsManager
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * 编辑器颜色方案管理器
 * 负责将 Material Theme 的颜色应用到 CodeEditor
 */
object EditorColorSchemeManager {

    // 暗色模式下的滚动条颜色 - 使用 Int 类型
    private val DARK_THEME_COLORS = mapOf(
        "scrollTrack" to 0xFF4A4644.toInt(),
        "scrollThumbPressed" to 0xFF8D878D.toInt(),
        "scrollThumb" to 0x99777278.toInt()
    )

    // 亮色模式下的滚动条颜色 - 使用 Int 类型
    private val LIGHT_THEME_COLORS = mapOf(
        "scrollTrack" to 0xFFE8E1DD.toInt(),
        "scrollThumbPressed" to 0xFF969194.toInt(),
        "scrollThumb" to 0x99A19DA4.toInt()
    )

    /**
     * 应用主题颜色到编辑器 - 从 MaterialTheme 动态获取颜色
     */
    @Composable
    fun applyThemeColors(
        scheme: EditorColorScheme,
        seedColor: Color,
        isDark: Boolean
    ) {
        // 获取 MaterialTheme 颜色
        val colorScheme = MaterialTheme.colorScheme

        // 获取语法高亮颜色设置
        val settings = SettingsManager.currentSettings
        val classNameColor = settings.classNameColor.toArgb()
        val localVariableColor = settings.localVariableColor.toArgb()
        val keywordColor = settings.keywordColor.toArgb()
        val functionNameColor = settings.functionNameColor.toArgb()
        val literalColor = settings.literalColor.toArgb()
        val commentColor = settings.commentColor.toArgb()
        val selectedLineColor = settings.selectedLineColor.toArgb()

        // 从 MaterialTheme 动态获取颜色值
        val primaryColor = colorScheme.primary.toArgb()
        val backgroundColor = colorScheme.background.toArgb()
        val onBackgroundColor = colorScheme.onBackground.toArgb()
        val outlineColor = colorScheme.outline.toArgb()
        val surfaceColor = colorScheme.surface.toArgb()
        val onSurfaceColor = colorScheme.onSurface.toArgb()

        // 应用主题颜色
        applyNonComposableThemeColors(
            scheme = scheme,
            seedColor = seedColor,
            isDark = isDark,
            primaryColor = primaryColor,
            backgroundColor = backgroundColor,
            onBackgroundColor = onBackgroundColor,
            outlineColor = outlineColor,
            surfaceColor = surfaceColor,
            onSurfaceColor = onSurfaceColor,
            classNameColor = classNameColor,
            localVariableColor = localVariableColor,
            keywordColor = keywordColor,
            functionNameColor = functionNameColor,
            literalColor = literalColor,
            commentColor = commentColor,
            selectedLineColor = selectedLineColor
        )
    }

    /**
     * 非Composable版本：应用主题颜色到编辑器
     * 可以在ViewModel等非Composable环境中调用
     */
    fun applyNonComposableThemeColors(
        scheme: EditorColorScheme,
        seedColor: Color,
        isDark: Boolean,
        primaryColor: Int,
        backgroundColor: Int,
        onBackgroundColor: Int,
        outlineColor: Int,
        surfaceColor: Int,
        onSurfaceColor: Int,
        classNameColor: Int = 0xFF6E81D9.toInt(),
        localVariableColor: Int = 0xFFAAAA88.toInt(),
        keywordColor: Int = 0xFFFF565E.toInt(),
        functionNameColor: Int = 0xFF2196F3.toInt(),
        literalColor: Int = 0xFF008080.toInt(),
        commentColor: Int = 0xFFA7A8A8.toInt(),
        selectedLineColor: Int = 0x1A000000.toInt()
    ) {
        // 获取颜色配置
        val themeColors = if (isDark) DARK_THEME_COLORS else LIGHT_THEME_COLORS

        // 应用滚动条颜色
        val scrollBarTrack = themeColors["scrollTrack"] ?: 0xFFE8E1DD.toInt()
        val scrollBarThumbPressed = themeColors["scrollThumbPressed"] ?: 0xFF969194.toInt()
        val scrollBarThumb = themeColors["scrollThumb"] ?: 0x99A19DA4.toInt()

        // 基础颜色
        scheme.setColor(
            EditorColorScheme.WHOLE_BACKGROUND,
            if (isDark) 0xFF212121.toInt() else 0xFFFFFFFF.toInt()
        )
        scheme.setColor(
            EditorColorScheme.LINE_NUMBER_BACKGROUND,
            if (isDark) 0xFF212121.toInt() else 0xFFFFFFFF.toInt()
        )
        scheme.setColor(EditorColorScheme.TEXT_NORMAL, onBackgroundColor)
        scheme.setColor(EditorColorScheme.LINE_NUMBER, onBackgroundColor)
        scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, onBackgroundColor)

        // 当前行和选择
        scheme.setColor(EditorColorScheme.CURRENT_LINE, setColorAlpha(primaryColor, 0.05f))
        scheme.setColor(EditorColorScheme.SELECTION_INSERT, setColorAlpha(primaryColor, 0.3f))
        scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selectedLineColor)
        scheme.setColor(EditorColorScheme.SELECTION_HANDLE, 0xFFDCC2AE.toInt())

        // 块线
        scheme.setColor(EditorColorScheme.BLOCK_LINE, setColorAlpha(outlineColor, 0.1f))
        scheme.setColor(EditorColorScheme.BLOCK_LINE_CURRENT, outlineColor)

        // 滚动条
        scheme.setColor(EditorColorScheme.SCROLL_BAR_TRACK, scrollBarTrack)
        scheme.setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, scrollBarThumbPressed)
        scheme.setColor(EditorColorScheme.SCROLL_BAR_THUMB, scrollBarThumb)

        // 行号面板
        scheme.setColor(EditorColorScheme.LINE_NUMBER_PANEL, primaryColor)
        scheme.setColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, onSurfaceColor)

        // 文本操作窗口
        scheme.setColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, surfaceColor)
        scheme.setColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, onSurfaceColor)
        scheme.setColor(EditorColorScheme.TEXT_ACTION_WINDOW_STROKE_COLOR, outlineColor)

        // 补全窗口
        scheme.setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, onSurfaceColor)
        scheme.setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, outlineColor)

        // 高亮分隔符
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, onBackgroundColor)

        // 匹配文本
        scheme.setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, 0)

        // 语法高亮颜色（使用传入的颜色）
        scheme.setColor(EditorColorScheme.IDENTIFIER_VAR, 0xFFFF9800.toInt())
        scheme.setColor(EditorColorScheme.LOCAL_VARIABLE, localVariableColor)
        scheme.setColor(EditorColorScheme.CLASS_NAME, classNameColor)
        scheme.setColor(EditorColorScheme.KEYWORD, keywordColor)
        scheme.setColor(EditorColorScheme.FUNCTION_NAME, functionNameColor)
        scheme.setColor(EditorColorScheme.LINE_DIVIDER, setColorAlpha(outlineColor, 0.1f))

        // 字符串文字和注释颜色
        scheme.setColor(EditorColorScheme.LITERAL, literalColor)
        scheme.setColor(EditorColorScheme.COMMENT, commentColor)

        scheme.setColor(
            EditorColorScheme.COMPLETION_WND_DIVIDER_COLOR,
            setColorAlpha(outlineColor, 0.1f)
        )
        scheme.setColor(
            EditorColorScheme.COMPLETION_WND_ICON_BACKGROUND,
            setColorAlpha(primaryColor, 0.2f)
        )
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, 0)
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BORDER, 0)

    }

    /**
     * 调整颜色透明度
     */
    private fun setColorAlpha(color: Int, alpha: Float): Int {
        val alphaValue = (alpha * 255).toInt() and 0xFF
        return (alphaValue shl 24) or (color and 0x00FFFFFF)
    }

    /**
     * 记住编辑器的颜色方案，避免每次重新创建
     */
    @Composable
    fun rememberColorScheme(seedColor: Color, isDark: Boolean): EditorColorScheme {
        val colorScheme = MaterialTheme.colorScheme
        val settings = SettingsManager.currentSettings

        return remember(
            seedColor, isDark, settings.classNameColor, settings.localVariableColor,
            settings.keywordColor, settings.functionNameColor, settings.literalColor,
            settings.commentColor, settings.selectedLineColor
        ) {
            val scheme = EditorColorScheme()
            // 获取 MaterialTheme 颜色
            val primaryColor = colorScheme.primary.toArgb()
            val backgroundColor = colorScheme.background.toArgb()
            val onBackgroundColor = colorScheme.onBackground.toArgb()
            val outlineColor = colorScheme.outline.toArgb()
            val surfaceColor = colorScheme.surface.toArgb()
            val onSurfaceColor = colorScheme.onSurface.toArgb()

            // 获取语法高亮颜色
            val classNameColor = settings.classNameColor.toArgb()
            val localVariableColor = settings.localVariableColor.toArgb()
            val keywordColor = settings.keywordColor.toArgb()
            val functionNameColor = settings.functionNameColor.toArgb()
            val literalColor = settings.literalColor.toArgb()
            val commentColor = settings.commentColor.toArgb()
            val selectedLineColor = settings.selectedLineColor.toArgb()

            applyNonComposableThemeColors(
                scheme = scheme,
                seedColor = seedColor,
                isDark = isDark,
                primaryColor = primaryColor,
                backgroundColor = backgroundColor,
                onBackgroundColor = onBackgroundColor,
                outlineColor = outlineColor,
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor,
                classNameColor = classNameColor,
                localVariableColor = localVariableColor,
                keywordColor = keywordColor,
                functionNameColor = functionNameColor,
                literalColor = literalColor,
                commentColor = commentColor,
                selectedLineColor = selectedLineColor
            )
            scheme
        }
    }
}