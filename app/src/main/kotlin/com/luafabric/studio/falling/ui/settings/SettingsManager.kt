package com.luafabric.studio.falling.ui.settings

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.luafabric.studio.falling.core.StudioMmkv
import com.luafabric.studio.falling.ui.theme.ThemeType
import muling.views.tool.utils.IconManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

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
    val THIRD_PARTY_WIDGET_SUPPORT = booleanPreferencesKey("thirdPartyWidgetSupport")
    // 防火墙：越级写入拦截
    val CROSS_PROJECT_WRITE_GUARD = booleanPreferencesKey("cross_project_write_guard")
    // 防火墙：自我守护（保护 LuaFabric-Studio/ 容器目录）
    val SELF_GUARD = booleanPreferencesKey("self_guard")
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

    // 项目分类：categories 为自定义分类(有序,不含内置"收藏/所有")；PROJECT_CATEGORY 为 项目id→分类(取值: 收藏/自定义名; 缺省=所有)
    val CATEGORIES = stringPreferencesKey("project_categories")
    val PROJECT_CATEGORY = stringPreferencesKey("project_category_map")

    // 智能排序开关
    val SMART_SORTING_ENABLED = booleanPreferencesKey("smart_sorting_enabled")

    // Toast 位置
    val TOAST_POSITION = stringPreferencesKey("toast_position")
    // Toast 边框开关
    val TOAST_BORDER_ENABLED = booleanPreferencesKey("toast_border_enabled")

    val EDITOR_WORD_WRAP = booleanPreferencesKey("editor_word_wrap")
    // 项目间自动换行独立：开=每项目独立换行状态；关=全局共享
    val EDITOR_WORD_WRAP_INDEPENDENT = booleanPreferencesKey("editor_word_wrap_independent")
    // AI 询问展示方式：关=编辑区上方横幅（默认）；开=弹窗
    val ASK_USER_IN_DIALOG = booleanPreferencesKey("ask_user_in_dialog")

    // 【新增】十六进制颜色高亮开关
    val HEX_COLOR_HIGHLIGHT_ENABLED = booleanPreferencesKey("hex_color_highlight_enabled")

    // 构建次数赞助提示
    val SPONSOR_BUILD_COUNT = intPreferencesKey("sponsor_build_count")
    val SPONSOR_ROUND = intPreferencesKey("sponsor_round")
    val SPONSOR_SKIP_NEXT = booleanPreferencesKey("sponsor_skip_next")

    // 【新增】快捷功能栏无字模式（文本功能替换为图标）
    val QUICK_BAR_ICON_ONLY = booleanPreferencesKey("quick_bar_icon_only")

    // 防火墙：拦截次数按项目分计（项目名 → 次数）
    val CROSS_WRITE_COUNTS = stringPreferencesKey("cross_write_counts")
    val SELF_GUARD_COUNTS = stringPreferencesKey("self_guard_counts")

    // 源码论坛展示开关（均默认关闭）
    val FORUM_HIDE_OTHER_AVATARS = booleanPreferencesKey("forum_hide_other_avatars")
    val FORUM_HIDE_OTHER_IMAGES = booleanPreferencesKey("forum_hide_other_images")
    val FORUM_RELATIVE_DATE = booleanPreferencesKey("forum_relative_date")
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

// 防火墙拦截类别
enum class FirewallKind { CROSS_WRITE, SELF_GUARD }

object SettingsManager {

    // 当前设置状态
    var currentSettings by mutableStateOf(SettingsData())

    // 临时的赞助弹窗提示的累计构建次数（瞬态，不持久化）
    var pendingSponsorPrompt: Int? by mutableStateOf<Int?>(null)

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

    /** 每项目换行状态的 MMKV 键（键含项目绝对路径）。 */
    private fun wordWrapKey(projectPath: String): Preferences.Key<Boolean> =
        booleanPreferencesKey("editor_word_wrap/::${projectPath.trimEnd('/', '\\')}")

    /**
     * 读取某项目应使用的自动换行状态：
     * 独立开关开 → 项目级键（缺失回退全局 editorWordWrap）；关 → 全局。
     */
    fun getEditorWordWrap(context: Context, projectPath: String?): Boolean {
        if (!currentSettings.perProjectWordWrap || projectPath.isNullOrBlank()) {
            return currentSettings.editorWordWrap
        }
        StudioMmkv.ensureInit(context)
        val preferences = MmkvPrefs(context)
        return preferences[wordWrapKey(projectPath)] ?: currentSettings.editorWordWrap
    }

    /** 写入某项目的自动换行状态：独立开 → 项目级键；关 → 全局 editorWordWrap。 */
    fun setEditorWordWrap(context: Context, projectPath: String?, value: Boolean) {
        StudioMmkv.ensureInit(context)
        val preferences = MmkvPrefs(context)
        if (currentSettings.perProjectWordWrap && !projectPath.isNullOrBlank()) {
            preferences[wordWrapKey(projectPath)] = value
        } else {
            updateSettings(currentSettings.copy(editorWordWrap = value))
            preferences[PreferencesKeys.EDITOR_WORD_WRAP] = value
        }
    }

    // 从 MMKV 同步加载设置
    suspend fun loadSavedSettings(context: Context) {
        StudioMmkv.ensureInit(context)
        val preferences = MmkvPrefs(context)

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
        val dynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true
        val editorFontType = EditorFontType.valueOf(
            preferences[PreferencesKeys.EDITOR_FONT_TYPE] ?: "JETBRAINS_MONO"
        )
        val customFontPath = preferences[PreferencesKeys.CUSTOM_FONT_PATH] ?: ""
        val enableTabHistory = preferences[PreferencesKeys.ENABLE_TAB_HISTORY] ?: true
        val indentGuideEnabled = preferences[PreferencesKeys.INDENT_GUIDE_ENABLED] ?: true
        val thirdPartyWidgetSupport =
            preferences[PreferencesKeys.THIRD_PARTY_WIDGET_SUPPORT] ?: true
        // 防火墙：越级写入拦截（默认开启）
        val crossProjectWriteGuard =
            preferences[PreferencesKeys.CROSS_PROJECT_WRITE_GUARD] ?: true
        // 防火墙：自我守护（默认开启）
        val selfGuard =
            preferences[PreferencesKeys.SELF_GUARD] ?: true

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

        // 加载自定义分类(有序)与 项目→分类 映射
        val categories: List<String> = try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(preferences[PreferencesKeys.CATEGORIES] ?: "[]", type)
        } catch (_: Exception) {
            emptyList()
        }
        val projectCategory: Map<String, String> = try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            Gson().fromJson(preferences[PreferencesKeys.PROJECT_CATEGORY] ?: "{}", type)
        } catch (_: Exception) {
            emptyMap()
        }

        // 加载智能排序开关
        val smartSortingEnabled = preferences[PreferencesKeys.SMART_SORTING_ENABLED] ?: true

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
        val perProjectWordWrap = preferences[PreferencesKeys.EDITOR_WORD_WRAP_INDEPENDENT] ?: true
        val askUserInDialog = preferences[PreferencesKeys.ASK_USER_IN_DIALOG] ?: false

        // 【新增】加载十六进制颜色高亮开关
        val hexColorHighlightEnabled = preferences[PreferencesKeys.HEX_COLOR_HIGHLIGHT_ENABLED] ?: true

        // 加载构建次数赞助提示相关设置
        val buildCount = preferences[PreferencesKeys.SPONSOR_BUILD_COUNT] ?: 0
        val sponsorRound = preferences[PreferencesKeys.SPONSOR_ROUND] ?: 0
        val skipNextSponsor = preferences[PreferencesKeys.SPONSOR_SKIP_NEXT] ?: false

        // 【新增】快捷功能栏无字模式
        val quickBarIconOnly = preferences[PreferencesKeys.QUICK_BAR_ICON_ONLY] ?: false

        // 源码论坛展示开关（相对日期默认开启，其余默认关闭）
        val forumHideOtherAvatars = preferences[PreferencesKeys.FORUM_HIDE_OTHER_AVATARS] ?: false
        val forumHideOtherImages = preferences[PreferencesKeys.FORUM_HIDE_OTHER_IMAGES] ?: false
        val forumRelativeDate = preferences[PreferencesKeys.FORUM_RELATIVE_DATE] ?: true

        // 防火墙拦截计数（项目名 → 次数，JSON 字符串）
        val crossWriteCounts: Map<String, Int> = try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            Gson().fromJson(preferences[PreferencesKeys.CROSS_WRITE_COUNTS] ?: "{}", type)
        } catch (_: Exception) {
            emptyMap()
        }
        val selfGuardCounts: Map<String, Int> = try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            Gson().fromJson(preferences[PreferencesKeys.SELF_GUARD_COUNTS] ?: "{}", type)
        } catch (_: Exception) {
            emptyMap()
        }

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
                thirdPartyWidgetSupport = thirdPartyWidgetSupport,
                crossProjectWriteGuard = crossProjectWriteGuard,
                selfGuard = selfGuard,
                selectedAppIcon = selectedAppIcon,
                completionCaseSensitive = completionCaseSensitive,
                sortOrder = sortOrder,
                pinnedProjects = pinnedProjects,
                smartSortingEnabled = smartSortingEnabled,
                categories = categories,
                projectCategory = projectCategory,
                toastPosition = toastPosition,
                toastBorderEnabled = toastBorderEnabled,
                editorWordWrap = editorWordWrap,
                perProjectWordWrap = perProjectWordWrap,
                askUserInDialog = askUserInDialog,
                hexColorHighlightEnabled = hexColorHighlightEnabled,
                buildCount = buildCount,
                sponsorRound = sponsorRound,
                skipNextSponsor = skipNextSponsor,
                quickBarIconOnly = quickBarIconOnly,
                crossWriteCounts = crossWriteCounts,
                selfGuardCounts = selfGuardCounts,
                forumHideOtherAvatars = forumHideOtherAvatars,
                forumHideOtherImages = forumHideOtherImages,
                forumRelativeDate = forumRelativeDate
            )
        )
    }

    // 同步保存设置到 MMKV
    suspend fun saveSettingsAsync(context: Context) {
        StudioMmkv.ensureInit(context)
        val preferences = MmkvPrefs(context)
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
        preferences[PreferencesKeys.THIRD_PARTY_WIDGET_SUPPORT] =
            currentSettings.thirdPartyWidgetSupport
        preferences[PreferencesKeys.CROSS_PROJECT_WRITE_GUARD] =
            currentSettings.crossProjectWriteGuard
        preferences[PreferencesKeys.SELF_GUARD] =
            currentSettings.selfGuard
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

        preferences[PreferencesKeys.CATEGORIES] = Gson().toJson(currentSettings.categories)
        preferences[PreferencesKeys.PROJECT_CATEGORY] = Gson().toJson(currentSettings.projectCategory)

        preferences[PreferencesKeys.SMART_SORTING_ENABLED] = currentSettings.smartSortingEnabled

        preferences[PreferencesKeys.TOAST_POSITION] = currentSettings.toastPosition.name

        preferences[PreferencesKeys.TOAST_BORDER_ENABLED] = currentSettings.toastBorderEnabled

        preferences[PreferencesKeys.EDITOR_WORD_WRAP] = currentSettings.editorWordWrap
        preferences[PreferencesKeys.EDITOR_WORD_WRAP_INDEPENDENT] =
            currentSettings.perProjectWordWrap
        preferences[PreferencesKeys.ASK_USER_IN_DIALOG] = currentSettings.askUserInDialog

        // 【新增】保存十六进制颜色高亮开关
        preferences[PreferencesKeys.HEX_COLOR_HIGHLIGHT_ENABLED] = currentSettings.hexColorHighlightEnabled

        // 保存构建次数赞助提示相关设置
        preferences[PreferencesKeys.SPONSOR_BUILD_COUNT] = currentSettings.buildCount
        preferences[PreferencesKeys.SPONSOR_ROUND] = currentSettings.sponsorRound
        preferences[PreferencesKeys.SPONSOR_SKIP_NEXT] = currentSettings.skipNextSponsor

        // 【新增】快捷功能栏无字模式
        preferences[PreferencesKeys.QUICK_BAR_ICON_ONLY] = currentSettings.quickBarIconOnly

        // 防火墙拦截计数
        preferences[PreferencesKeys.CROSS_WRITE_COUNTS] = Gson().toJson(currentSettings.crossWriteCounts)
        preferences[PreferencesKeys.SELF_GUARD_COUNTS] = Gson().toJson(currentSettings.selfGuardCounts)

        // 源码论坛展示开关
        preferences[PreferencesKeys.FORUM_HIDE_OTHER_AVATARS] = currentSettings.forumHideOtherAvatars
        preferences[PreferencesKeys.FORUM_HIDE_OTHER_IMAGES] = currentSettings.forumHideOtherImages
        preferences[PreferencesKeys.FORUM_RELATIVE_DATE] = currentSettings.forumRelativeDate
        notifyListeners()
    }

    // 保存设置（在后台协程中执行）
    fun saveSettings(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            saveSettingsAsync(context)
        }
    }

    /** 记录一次防火墙拦截（按项目名分计），立即更新内存态并异步持久化。 */
    fun recordFirewallGuard(kind: FirewallKind, projectName: String, context: Context) {
        currentSettings = when (kind) {
            FirewallKind.CROSS_WRITE -> {
                val m = currentSettings.crossWriteCounts.toMutableMap()
                m[projectName] = (m[projectName] ?: 0) + 1
                currentSettings.copy(crossWriteCounts = m)
            }
            FirewallKind.SELF_GUARD -> {
                val m = currentSettings.selfGuardCounts.toMutableMap()
                m[projectName] = (m[projectName] ?: 0) + 1
                currentSettings.copy(selfGuardCounts = m)
            }
        }
        notifyListeners()
        saveSettings(context)
    }

    /** 防火墙拦截全局累计（两开关合计）→ 设置页「已守护您 X 次」。 */
    fun firewallGuardTotal(): Int =
        currentSettings.crossWriteCounts.values.sum() + currentSettings.selfGuardCounts.values.sum()

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

}

data class SettingsData(
    val themeType: ThemeType = ThemeType.GREEN,
    val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    val projectStoragePath: String = "/storage/emulated/0/LuaFabric-Studio/project/",
    val fontSizeScale: Float = 1.0f,
    val shapeSizeIndex: Int = 2,
    val fontFamilyType: FontFamilyType = FontFamilyType.DEFAULT,
    val dynamicColor: Boolean = true,
    val editorFontType: EditorFontType = EditorFontType.JETBRAINS_MONO,
    val customFontPath: String = "",
    val enableTabHistory: Boolean = true,
    val classNameColor: Color = Color(0xFF6E81D9),
    val localVariableColor: Color = Color(0xFFAAAA88),
    val keywordColor: Color = Color(0xFFFF565E),
    val functionNameColor: Color = Color(0xFF2196F3),
    val literalColor: Color = Color(0xFF008080),
    val commentColor: Color = Color(0xFFA7A8A8),
    val selectedLineColor: Color = Color(0x1A000000),
    val indentGuideEnabled: Boolean = true,
    val thirdPartyWidgetSupport: Boolean = true,
    /** 防火墙：越级写入拦截（阻止项目间互相写入/删除/修改，但允许读取） */
    val crossProjectWriteGuard: Boolean = true,
    /** 防火墙：自我守护（拦截项目对 LuaFabric-Studio/ 容器的删除/移动/改名） */
    val selfGuard: Boolean = true,
    val selectedAppIcon: IconManager.AppIcon = IconManager.AppIcon.PLAY_STORE,
    val completionCaseSensitive: Boolean = false,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val pinnedProjects: Set<String> = emptySet(),
    val smartSortingEnabled: Boolean = true,
    /** 自定义分类（有序，不含内置"收藏/所有"）。 */
    val categories: List<String> = emptyList(),
    /** 项目 id→分类；取值"收藏"/自定义名，缺省视为"所有"。 */
    val projectCategory: Map<String, String> = emptyMap(),
    val toastPosition: ToastPosition = ToastPosition.BOTTOM,
    val toastBorderEnabled: Boolean = false,
    val editorWordWrap: Boolean = false,
    /** 项目间自动换行独立：默认开启（每项目独立换行状态） */
    val perProjectWordWrap: Boolean = true,
    /** AI 询问展示方式：false=编辑区上方横幅（默认）；true=弹窗 */
    val askUserInDialog: Boolean = false,
    val hexColorHighlightEnabled: Boolean = true,  // 【新增】十六进制颜色高亮开关
    val buildCount: Int = 0,               // 全局累计构建次数
    val sponsorRound: Int = 0,             // 当前待评估的赞助轮次指针 r（0 视为 1）
    val skipNextSponsor: Boolean = false,  // 下一轮是否跳过（已赞助则跳过）
    val quickBarIconOnly: Boolean = false, // 【新增】快捷功能栏无字模式（默认关闭）
    /** 防火墙拦截计数：项目名 → 次数 */
    val crossWriteCounts: Map<String, Int> = emptyMap(),
    val selfGuardCounts: Map<String, Int> = emptyMap(),
    /** 源码论坛：隐藏非己头像（默认关闭） */
    val forumHideOtherAvatars: Boolean = false,
    /** 源码论坛：隐藏其他用户的帖子配图（默认关闭） */
    val forumHideOtherImages: Boolean = false,
    /** 源码论坛：相对发帖日期（默认开启） */
    val forumRelativeDate: Boolean = true,
)

/**
 * MMKV 兼容层：承接原有 DataStore 的 `preferences[key]` 读写调用点。
 * 键名/类型载体仍为 PreferencesKeys（Preferences.Key<T>），实际读写全部落在
 * StudioMmkv（Studio 私有根 + Keystore 派生密钥，与 Lua 侧 mmkv 双域隔离）。
 * 每次写入附带一条类型标记（"tag:" + 键名），读取时按标记分派到对应 MMKV 类型 API；
 * 标记缺失即视为键不存在（返回 null，由调用侧 `?: 默认值` 兜底）。
 */
private class MmkvPrefs(private val context: Context) {
    private val id = StudioMmkv.ID_SETTINGS

    private fun tagOf(key: String) = "tag:$key"

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: Preferences.Key<T>): T? {
        val value: Any? = when (StudioMmkv.getString(context, id, tagOf(key.name))) {
            "string" -> StudioMmkv.getString(context, id, key.name)
            "bool" -> StudioMmkv.getBoolean(context, id, key.name, false)
            "int" -> StudioMmkv.getInt(context, id, key.name, 0)
            "long" -> StudioMmkv.getLong(context, id, key.name, 0L)
            "float" -> StudioMmkv.getFloat(context, id, key.name, 0f)
            else -> null
        }
        return value as T?
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> set(key: Preferences.Key<T>, value: T?) {
        val name = key.name
        if (value == null) {
            StudioMmkv.remove(context, id, name)
            StudioMmkv.remove(context, id, tagOf(name))
            return
        }
        when (value) {
            is String -> {
                StudioMmkv.putString(context, id, name, value)
                StudioMmkv.putString(context, id, tagOf(name), "string")
            }
            is Boolean -> {
                StudioMmkv.putBoolean(context, id, name, value)
                StudioMmkv.putString(context, id, tagOf(name), "bool")
            }
            is Int -> {
                StudioMmkv.putInt(context, id, name, value)
                StudioMmkv.putString(context, id, tagOf(name), "int")
            }
            is Long -> {
                StudioMmkv.putLong(context, id, name, value)
                StudioMmkv.putString(context, id, tagOf(name), "long")
            }
            is Float -> {
                StudioMmkv.putFloat(context, id, name, value)
                StudioMmkv.putString(context, id, tagOf(name), "float")
            }
            else -> throw IllegalArgumentException(
                "不支持的设置值类型: ${value::class.java.name}"
            )
        }
    }
}