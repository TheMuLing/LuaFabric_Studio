package muling.views.tool.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.luafabric.studio.falling.ui.settings.SettingsManager

object IconManager {

    enum class AppIcon(val aliasName: String, val displayName: String) {
        PLAY_STORE(".MainActivityPlayStore", "Play Store图标"),
        ADAPTIVE(".MainActivityDefault", "自适应图标")
    }

    /**
     * 获取当前使用的图标类型
     */
    fun getCurrentIcon(context: Context): AppIcon {
        val packageManager = context.packageManager

        // 检查 Play Store 图标是否启用
        val playStoreComponent = ComponentName(
            context,
            "${context.packageName}${AppIcon.PLAY_STORE.aliasName}"
        )

        return try {
            when (packageManager.getComponentEnabledSetting(playStoreComponent)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> AppIcon.PLAY_STORE
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> AppIcon.ADAPTIVE
                else -> {
                    // 如果两者都未明确设置，默认使用 Play Store
                    AppIcon.PLAY_STORE
                }
            }
        } catch (e: Exception) {
            // 出现异常时默认使用 Play Store 图标
            AppIcon.PLAY_STORE
        }
    }

    /**
     * 切换应用图标
     */
    fun switchAppIcon(context: Context, newIcon: AppIcon) {
        val packageManager = context.packageManager

        // 禁用所有图标别名
        AppIcon.values().forEach { icon ->
            val componentName = ComponentName(
                context,
                "${context.packageName}${icon.aliasName}"
            )
            try {
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                // 忽略异常
            }
        }

        // 启用选中的图标别名
        val targetComponent = ComponentName(
            context,
            "${context.packageName}${newIcon.aliasName}"
        )
        packageManager.setComponentEnabledSetting(
            targetComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // 保存到设置
        val settings = SettingsManager.currentSettings
        if (settings.selectedAppIcon != newIcon) {
            val newSettings = settings.copy(selectedAppIcon = newIcon)
            SettingsManager.updateSettings(newSettings)
            SettingsManager.saveSettings(context)
        }
    }

    /**
     * 初始化图标设置 - 确保只有一个图标启用
     */
    fun initIconSetting(context: Context) {
        val currentIcon = getCurrentIcon(context)

        // 确保选中的图标处于启用状态
        try {
            switchAppIcon(context, currentIcon)
        } catch (e: Exception) {
            // 如果初始化失败，确保至少有一个图标启用
            val playStoreComponent = ComponentName(
                context,
                "${context.packageName}${AppIcon.PLAY_STORE.aliasName}"
            )
            context.packageManager.setComponentEnabledSetting(
                playStoreComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}