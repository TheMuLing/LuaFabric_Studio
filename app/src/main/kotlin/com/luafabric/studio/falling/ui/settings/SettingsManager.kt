package com.luafabric.studio.falling.ui.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.getSystemService
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.luafabric.studio.falling.ui.theme.ThemeType
import muling.views.tool.utils.IconManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

// DataStore 实例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

// 定义所有存储键
private object PreferencesKeys {
    val THEME_TYPE = stringPreferencesKey("theme_type")
    val DARK_MODE = stringPreferencesKey("dark_mode")
    val FONT_SIZE_SCALE = floatPreferencesKey("font_size_scale")
    val SHAPE_SIZE_INDEX = intPreferencesKey("shape_size_index")
    val FONT_FAMILY_TYPE = stringPreferencesKey("font_family_type")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val EDITOR_FONT_TYPE = stringPreferencesKey("editor_font_type")
    val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
    val ENABLE_TAB_HISTORY = booleanPreferencesKey("enable_tab_history")
    val INDENT_GUIDE_ENABLED = booleanPreferencesKey("indentGuideEnabled")
    val PROJECT_STORAGE_PATH = stringPreferencesKey("project_storage_path")

    // 语法高亮颜色
    val CLASS_NAME_COLOR = intPreferencesKey("syntax_class_name_color")
    val LOCAL_VAR_COLOR = intPreferencesKey("syntax_local_var_color")
    val KEYWORD_COLOR = intPreferencesKey("syntax_keyword_color")
    val FUNCTION_NAME_COLOR = intPreferencesKey("syntax_function_color")
    val LITERAL_COLOR = intPreferencesKey("syntax_literal_color")
    val COMMENT_COLOR = intPreferencesKey("syntax_comment_color")
    val SELECTED_LINE_COLOR = intPreferencesKey("selected_line_color")

    val SELECTED_APP_ICON = stringPreferencesKey("selected_app_icon")

    // 补全大小写敏感设置项
    val COMPLETION_CASE_SENSITIVE = booleanPreferencesKey("completion_case_sensitive")

    // 排序方式和置顶项目列表
    val SORT_ORDER = stringPreferencesKey("sort_order")
    val PINNED_PROJECTS = stringPreferencesKey("pinned_projects")

    // 智能排序开关
    val SMART_SORTING_ENABLED = booleanPreferencesKey("smart_sorting_enabled")

    // Toast 位置
    val TOAST_POSITION = stringPreferencesKey("toast_position")
    // Toast 边框开关
    val TOAST_BORDER_ENABLED = booleanPreferencesKey("toast_border_enabled")

    val EDITOR_WORD_WRAP = booleanPreferencesKey("editor_word_wrap")

    // 语言设置（使用 DataStore，不再用 SharedPreferences）
    val LANGUAGE_TAG = stringPreferencesKey("language_tag")

    // 【新增】十六进制颜色高亮开关
    val HEX_COLOR_HIGHLIGHT_ENABLED = booleanPreferencesKey("hex_color_highlight_enabled")

    // 【新增】滑动手势开关
    val ENABLE_SWIPE_GESTURE = booleanPreferencesKey("enable_swipe_gesture")
}

// 排序方式枚举
enum class SortOrder {
    NAME_ASC,           // 名称 A-Z
    NAME_DESC,          // 名称 Z-A
    DATE_MODIFIED_NEWEST, // 修改时间 最新
    DATE_MODIFIED_OLDEST  // 修改时间 最早
}

// Toast 位置枚举
enum class ToastPosition {
    TOP, BOTTOM
}

object SettingsManager {

    // 当前设置状态
    var currentSettings by mutableStateOf(SettingsData())

    // 设置变化监听器列表
    private val listeners = mutableListOf<(SettingsData) -> Unit>()

    /**
     * 获取固定项目存储路径（外部存储根目录）
     */
    private fun getFixedProjectStoragePath(): String {
        val baseDir = Environment.getExternalStorageDirectory()
        return File(baseDir, "LuaFabric-Studio/project").absolutePath
    }

    // 注册设置变化监听器
    fun addListener(listener: (SettingsData) -> Unit) {
        listeners.add(listener)
    }

    // 移除设置变化监听器
    fun removeListener(listener: (SettingsData) -> Unit) {
        listeners.remove(listener)
    }

    // 更新设置并通知所有监听器
    fun updateSettings(newSettings: SettingsData) {
        currentSettings = newSettings
        notifyListeners()
    }

    // 通知所有监听器
    private fun notifyListeners() {
        listeners.forEach { listener ->
            listener(currentSettings)
        }
    }

    // 从 DataStore 异步加载设置
    suspend fun loadSavedSettings(context: Context) {
        val preferences = context.dataStore.data.first()

        val themeType = ThemeType.valueOf(
            preferences[PreferencesKeys.THEME_TYPE] ?: "GREEN"
        )
        val darkMode = DarkMode.valueOf(
            preferences[PreferencesKeys.DARK_MODE] ?: "FOLLOW_SYSTEM"
        )
        val fontSizeScale = preferences[PreferencesKeys.FONT_SIZE_SCALE] ?: 1.0f
        val shapeSizeIndex = preferences[PreferencesKeys.SHAPE_SIZE_INDEX] ?: 2
        val fontFamilyType = FontFamilyType.valueOf(
            preferences[PreferencesKeys.FONT_FAMILY_TYPE] ?: "DEFAULT"
        )
        val dynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: false
        val editorFontType = EditorFontType.valueOf(
            preferences[PreferencesKeys.EDITOR_FONT_TYPE] ?: "JETBRAINS_MONO"
        )
        val customFontPath = preferences[PreferencesKeys.CUSTOM_FONT_PATH] ?: ""
        val enableTabHistory = preferences[PreferencesKeys.ENABLE_TAB_HISTORY] ?: false
        val indentGuideEnabled = preferences[PreferencesKeys.INDENT_GUIDE_ENABLED] ?: true

        val fixedPath = getFixedProjectStoragePath()

        val classNameColor = preferences[PreferencesKeys.CLASS_NAME_COLOR] ?: 0xFF6E81D9.toInt()
        val localVariableColor = preferences[PreferencesKeys.LOCAL_VAR_COLOR] ?: 0xFFAAAA88.toInt()
        val keywordColor = preferences[PreferencesKeys.KEYWORD_COLOR] ?: 0xFFFF565E.toInt()
        val functionNameColor =
            preferences[PreferencesKeys.FUNCTION_NAME_COLOR] ?: 0xFF2196F3.toInt()
        val literalColor = preferences[PreferencesKeys.LITERAL_COLOR] ?: 0xFF008080.toInt()
        val commentColor = preferences[PreferencesKeys.COMMENT_COLOR] ?: 0xFFA7A8A8.toInt()
        val selectedLineColor =
            preferences[PreferencesKeys.SELECTED_LINE_COLOR] ?: 0x33000000

        // 加载补全大小写敏感设置项
        val completionCaseSensitive =
            preferences[PreferencesKeys.COMPLETION_CASE_SENSITIVE] ?: false

        val selectedAppIconName = preferences[PreferencesKeys.SELECTED_APP_ICON] ?: "PLAY_STORE"
        val selectedAppIcon = try {
            IconManager.AppIcon.valueOf(selectedAppIconName)
        } catch (_: Exception) {
            IconManager.AppIcon.PLAY_STORE
        }

        // 加载排序方式
        val sortOrderName = preferences[PreferencesKeys.SORT_ORDER] ?: "NAME_ASC"
        val sortOrder = try {
            SortOrder.valueOf(sortOrderName)
        } catch (_: Exception) {
            SortOrder.NAME_ASC
        }

        // 加载置顶项目列表（存储为 JSON 字符串）
        val pinnedProjectsJson = preferences[PreferencesKeys.PINNED_PROJECTS] ?: "[]"
        val pinnedProjects: Set<String> = try {
            val type = object : TypeToken<Set<String>>() {}.type
            Gson().fromJson(pinnedProjectsJson, type)
        } catch (_: Exception) {
            emptySet()
        }

        // 加载智能排序开关
        val smartSortingEnabled = preferences[PreferencesKeys.SMART_SORTING_ENABLED] ?: false

        // 加载 Toast 位置
        val toastPositionName = preferences[PreferencesKeys.TOAST_POSITION] ?: "BOTTOM"
        val toastPosition = try {
            ToastPosition.valueOf(toastPositionName)
        } catch (_: Exception) {
            ToastPosition.BOTTOM
        }

        // 加载 Toast 边框开关
        val toastBorderEnabled = preferences[PreferencesKeys.TOAST_BORDER_ENABLED] ?: false

        val editorWordWrap = preferences[PreferencesKeys.EDITOR_WORD_WRAP] ?: false

        // 从 DataStore 加载语言设置
        val languageTag = preferences[PreferencesKeys.LANGUAGE_TAG] ?: "zh"

        // 【新增】加载十六进制颜色高亮开关
        val hexColorHighlightEnabled = preferences[PreferencesKeys.HEX_COLOR_HIGHLIGHT_ENABLED] ?: false

        // 【新增】加载滑动手势开关
        val enableSwipeGesture = preferences[PreferencesKeys.ENABLE_SWIPE_GESTURE] ?: false

        updateSettings(
            SettingsData(
                themeType = themeType,
                darkMode = darkMode,
                projectStoragePath = fixedPath,
                fontSizeScale = fontSizeScale,
                shapeSizeIndex = shapeSizeIndex,
                fontFamilyType = fontFamilyType,
                dynamicColor = dynamicColor,
                editorFontType = editorFontType,
                customFontPath = customFontPath,
                enableTabHistory = enableTabHistory,
                classNameColor = Color(classNameColor),
                localVariableColor = Color(localVariableColor),
                keywordColor = Color(keywordColor),
                functionNameColor = Color(functionNameColor),
                literalColor = Color(literalColor),
                commentColor = Color(commentColor),
                selectedLineColor = Color(selectedLineColor),
                indentGuideEnabled = indentGuideEnabled,
                selectedAppIcon = selectedAppIcon,
                completionCaseSensitive = completionCaseSensitive,
                sortOrder = sortOrder,
                pinnedProjects = pinnedProjects,
                smartSortingEnabled = smartSortingEnabled,
                toastPosition = toastPosition,
                toastBorderEnabled = toastBorderEnabled,
                editorWordWrap = editorWordWrap,
                languageTag = languageTag,
                hexColorHighlightEnabled = hexColorHighlightEnabled,
                enableSwipeGesture = enableSwipeGesture  // 【新增】
            )
        )
    }

    // 异步保存设置到 DataStore
    suspend fun saveSettingsAsync(context: Context) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_TYPE] = currentSettings.themeType.name
            preferences[PreferencesKeys.DARK_MODE] = currentSettings.darkMode.name
            preferences[PreferencesKeys.FONT_SIZE_SCALE] = currentSettings.fontSizeScale
            preferences[PreferencesKeys.SHAPE_SIZE_INDEX] = currentSettings.shapeSizeIndex
            preferences[PreferencesKeys.FONT_FAMILY_TYPE] = currentSettings.fontFamilyType.name
            preferences[PreferencesKeys.DYNAMIC_COLOR] = currentSettings.dynamicColor
            preferences[PreferencesKeys.EDITOR_FONT_TYPE] = currentSettings.editorFontType.name
            preferences[PreferencesKeys.CUSTOM_FONT_PATH] = currentSettings.customFontPath
            preferences[PreferencesKeys.ENABLE_TAB_HISTORY] = currentSettings.enableTabHistory
            preferences[PreferencesKeys.INDENT_GUIDE_ENABLED] = currentSettings.indentGuideEnabled
            preferences[PreferencesKeys.PROJECT_STORAGE_PATH] = currentSettings.projectStoragePath

            preferences[PreferencesKeys.CLASS_NAME_COLOR] = currentSettings.classNameColor.toArgb()
            preferences[PreferencesKeys.LOCAL_VAR_COLOR] =
                currentSettings.localVariableColor.toArgb()
            preferences[PreferencesKeys.KEYWORD_COLOR] = currentSettings.keywordColor.toArgb()
            preferences[PreferencesKeys.FUNCTION_NAME_COLOR] =
                currentSettings.functionNameColor.toArgb()
            preferences[PreferencesKeys.LITERAL_COLOR] = currentSettings.literalColor.toArgb()
            preferences[PreferencesKeys.COMMENT_COLOR] = currentSettings.commentColor.toArgb()
            preferences[PreferencesKeys.SELECTED_LINE_COLOR] =
                currentSettings.selectedLineColor.toArgb()

            preferences[PreferencesKeys.COMPLETION_CASE_SENSITIVE] =
                currentSettings.completionCaseSensitive

            preferences[PreferencesKeys.SELECTED_APP_ICON] = currentSettings.selectedAppIcon.name

            preferences[PreferencesKeys.SORT_ORDER] = currentSettings.sortOrder.name

            val pinnedJson = Gson().toJson(currentSettings.pinnedProjects)
            preferences[PreferencesKeys.PINNED_PROJECTS] = pinnedJson

            preferences[PreferencesKeys.SMART_SORTING_ENABLED] = currentSettings.smartSortingEnabled

            preferences[PreferencesKeys.TOAST_POSITION] = currentSettings.toastPosition.name

            preferences[PreferencesKeys.TOAST_BORDER_ENABLED] = currentSettings.toastBorderEnabled

            preferences[PreferencesKeys.EDITOR_WORD_WRAP] = currentSettings.editorWordWrap

            // 保存语言设置到 DataStore
            preferences[PreferencesKeys.LANGUAGE_TAG] = currentSettings.languageTag

            // 【新增】保存十六进制颜色高亮开关
            preferences[PreferencesKeys.HEX_COLOR_HIGHLIGHT_ENABLED] = currentSettings.hexColorHighlightEnabled

            // 【新增】保存滑动手势开关
            preferences[PreferencesKeys.ENABLE_SWIPE_GESTURE] = currentSettings.enableSwipeGesture
        }
        notifyListeners()
    }

    // 保存设置（在后台协程中执行）
    fun saveSettings(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            saveSettingsAsync(context)
        }
    }

    /**
     * 确保项目目录存在
     */
    fun ensureProjectDirectoryExists(): Boolean {
        val projectDir = File(currentSettings.projectStoragePath)
        return try {
            if (!projectDir.exists()) {
                val created = projectDir.mkdirs()
                if (!created) {
                    try {
                        Runtime.getRuntime().exec(arrayOf("mkdir", "-p", projectDir.absolutePath))
                        Thread.sleep(200)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            projectDir.exists() && projectDir.canWrite()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 设置应用语言（兼容 Android 13+ 和旧版本）
     * 会自动重启 Activity 使语言生效
     */
    fun setAppLanguage(context: Context, languageTag: String) {
        // 更新内存中的设置
        val newSettings = currentSettings.copy(languageTag = languageTag)
        updateSettings(newSettings)

        // 异步保存到 DataStore
        CoroutineScope(Dispatchers.IO).launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.LANGUAGE_TAG] = languageTag
            }
        }

        // 设置系统语言
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService<LocaleManager>()
            localeManager?.applicationLocales = LocaleList.forLanguageTags(languageTag)
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        }

    }

    /**
     * 同步加载语言设置（用于启动时）
     * 从 DataStore 读取，如果失败返回默认值
     */
    fun loadLanguageSync(context: Context): String {
        return try {
            // 尝试从 DataStore 同步读取（使用 runBlocking 或直接访问）
            // 但由于 DataStore 是异步的，这里用 currentSettings 作为回退
            currentSettings.languageTag
        } catch (_: Exception) {
            "zh"
        }
    }
}

data class SettingsData(
    val themeType: ThemeType = ThemeType.GREEN,
    val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    val projectStoragePath: String = "/storage/emulated/0/LuaFabric-Studio/project/",
    val fontSizeScale: Float = 1.0f,
    val shapeSizeIndex: Int = 2,
    val fontFamilyType: FontFamilyType = FontFamilyType.DEFAULT,
    val dynamicColor: Boolean = false,
    val editorFontType: EditorFontType = EditorFontType.JETBRAINS_MONO,
    val customFontPath: String = "",
    val enableTabHistory: Boolean = false,
    val classNameColor: Color = Color(0xFF6E81D9),
    val localVariableColor: Color = Color(0xFFAAAA88),
    val keywordColor: Color = Color(0xFFFF565E),
    val functionNameColor: Color = Color(0xFF2196F3),
    val literalColor: Color = Color(0xFF008080),
    val commentColor: Color = Color(0xFFA7A8A8),
    val selectedLineColor: Color = Color(0x1A000000),
    val indentGuideEnabled: Boolean = true,
    val selectedAppIcon: IconManager.AppIcon = IconManager.AppIcon.PLAY_STORE,
    val completionCaseSensitive: Boolean = false,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val pinnedProjects: Set<String> = emptySet(),
    val smartSortingEnabled: Boolean = false,
    val toastPosition: ToastPosition = ToastPosition.BOTTOM,
    val toastBorderEnabled: Boolean = false,
    val editorWordWrap: Boolean = false,
    val languageTag: String = "zh",
    val hexColorHighlightEnabled: Boolean = false,  // 【新增】十六进制颜色高亮开关
    val enableSwipeGesture: Boolean = false,         // 【新增】滑动手势开关
)