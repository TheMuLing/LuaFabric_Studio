package muling.views.tool.utils

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.Window
import androidx.annotation.Keep
import com.google.android.material.internal.EdgeToEdgeUtils

/**
 * 显示工具类
 * 提供屏幕尺寸、密度、单位转换等常用显示相关功能
 *
 * @version 1.0.0
 */
@Keep
object UiUtil {

    /**
     * dp转px
     * @param context 上下文
     * @param dp dp值
     * @return 转换后的px整数值
     */
    @JvmStatic
    fun dp2px(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * px转dp
     * @param context 上下文
     * @param px px值
     * @return 转换后的dp浮点值
     */
    @JvmStatic
    fun px2dp(context: Context, px: Float): Float {
        return px / context.resources.displayMetrics.density
    }

    /**
     * sp转px
     * @param context 上下文
     * @param sp sp值
     * @return 转换后的px浮点值
     */
    @JvmStatic
    fun sp2px(context: Context, sp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
    }

    /**
     * px转sp
     * @param context 上下文
     * @param px px值
     * @return 转换后的sp整数值
     */
    @JvmStatic
    fun px2sp(context: Context, px: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            px,
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * 获取屏幕宽度（像素）
     * @param context 上下文
     * @return 屏幕宽度像素值
     */
    @JvmStatic
    fun width(context: Context): Int {
        return context.resources.displayMetrics.widthPixels
    }

    /**
     * 获取屏幕高度（像素）
     * @param context 上下文
     * @return 屏幕高度像素值
     */
    @JvmStatic
    fun height(context: Context): Int {
        return context.resources.displayMetrics.heightPixels
    }

    /**
     * 获取状态栏高度
     * @param context 上下文
     * @return 状态栏高度像素值
     */
    @JvmStatic
    fun statusBarHeight(context: Context): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier(
            "status_bar_height", "dimen", "android"
        )
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    /**
     * 判断是否为横屏
     * @param context 上下文
     * @return true=横屏，false=竖屏
     */
    @JvmStatic
    fun isLandscape(context: Context): Boolean {
        val orientation = context.resources.configuration.orientation
        return orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    /**
     * 判断是否为竖屏
     * @param context 上下文
     * @return true=竖屏，false=横屏
     */
    @JvmStatic
    fun isPortrait(context: Context): Boolean {
        val orientation = context.resources.configuration.orientation
        return orientation == Configuration.ORIENTATION_PORTRAIT
    }

    /**
     * 根据屏幕宽度百分比计算像素值
     * @param context 上下文
     * @param percent 百分比（0-100）
     * @return 对应的像素值
     */
    @JvmStatic
    fun widthPercent(context: Context, percent: Double): Int {
        val width = width(context)
        return (width * percent / 100).toInt()
    }

    /**
     * 根据屏幕高度百分比计算像素值
     * @param context 上下文
     * @param percent 百分比（0-100）
     * @return 对应的像素值
     */
    @JvmStatic
    fun heightPercent(context: Context, percent: Double): Int {
        val height = height(context)
        return (height * percent / 100).toInt()
    }

    /**
     * 计算最小边长的百分比
     * @param context 上下文
     * @param percent 百分比（0-100）
     * @return 对应的像素值
     */
    @JvmStatic
    fun minPercent(context: Context, percent: Double): Int {
        val width = width(context)
        val height = height(context)
        val minDimension = minOf(width, height)
        return (minDimension * percent / 100).toInt()
    }

    @JvmStatic
    fun applyEdgeToEdgePreference(window: Window, edgeToEdgeEnabled: Boolean) {
        runCatching {
            EdgeToEdgeUtils.applyEdgeToEdge(window, edgeToEdgeEnabled)
        }.onFailure {
            it.printStackTrace()
        }
    }

    @JvmStatic
    fun actionBarSize(context: Context): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            TypedValue.complexToDimensionPixelSize(
                typedValue.data,
                context.resources.displayMetrics
            )
        } else {
            0
        }
    }

}