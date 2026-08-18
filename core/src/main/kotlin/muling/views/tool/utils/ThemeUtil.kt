package muling.views.tool.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.annotation.Keep
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 主题工具类
 * 提供系统主题检测、状态栏/导航栏样式设置及WindowInsets处理功能
 *
 * @version 1.0.0
 */
@Keep
object ThemeUtil {

    /**
     * 检测系统是否处于深色模式
     * @param context 上下文
     * @return true 如果系统处于深色模式，否则返回 false
     */
    @JvmStatic
    fun isSystemDarkTheme(context: Context): Boolean {
        val configuration = context.resources.configuration
        val uiMode = configuration.uiMode
        val nightModeFlag = uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlag == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * 根据系统主题自动设置状态栏/导航栏样式并处理 WindowInsets
     * @param activity 当前的 Activity 实例
     */
    @JvmStatic
    fun applySystemThemeSettings(activity: Activity) {
        // 检测系统是否处于深色模式
        val isDarkTheme = isSystemDarkTheme(activity)

        // 获取 WindowInsetsController 并设置状态栏/导航栏颜色
        val controller =
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller?.let {
            // 设置状态栏图标颜色（深色主题时用浅色图标，反之亦然）
            it.isAppearanceLightStatusBars = !isDarkTheme
            // 设置导航栏图标颜色
            it.isAppearanceLightNavigationBars = !isDarkTheme
        }

        // 处理 WindowInsets 避免内容被系统栏遮挡
        setupWindowInsets(activity)
    }

    /**
     * 处理 WindowInsets，为内容视图设置适当的Padding以避免被系统栏遮挡
     * @param activity 当前的 Activity 实例
     */
    @JvmStatic
    fun setupWindowInsets(activity: Activity) {
        val contentView = activity.findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * 手动设置状态栏和导航栏的浅色外观模式
     * @param activity 当前的 Activity 实例
     * @param lightStatusBars 是否为浅色状态栏（true=图标深色，false=图标浅色）
     * @param lightNavigationBars 是否为浅色导航栏（true=图标深色，false=图标浅色）
     */
    @JvmStatic
    fun setLightBars(
        activity: Activity,
        lightStatusBars: Boolean,
        lightNavigationBars: Boolean = lightStatusBars
    ) {
        val controller =
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller?.let {
            it.isAppearanceLightStatusBars = lightStatusBars
            it.isAppearanceLightNavigationBars = lightNavigationBars
        }
    }
}