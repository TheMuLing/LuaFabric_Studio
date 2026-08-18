package muling.views.tool.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.util.TypedValue
import androidx.annotation.Keep
import com.androlua.LuaApplication
import com.androlua.LuaBitmap
import com.androlua.LuaBitmapDrawable
import com.androlua.LuaContext
import java.io.File

@Keep
object BitmapUtil {

    /**
     * 获取图标Drawable - 使用老方法
     * 参数说明：
     * 1. context: Context
     * 2. input: String - 图片路径
     * 3. 可选参数1: Any? - 颜色
     * 4. 可选参数2: Any? - 大小
     * 5. 可选参数3: Any? - 单位
     */
    @JvmStatic
    fun getIconDrawable(
        context: Context,
        input: String,
        vararg args: Any?
    ): Any? {
        return try {
            // 解析参数
            val color = if (args.isNotEmpty()) parseColor(args[0]) else null
            val size = if (args.size > 1) args[1] else null
            val unit = if (args.size > 2) args[2] else null

            // 获取完整路径
            val fullPath = getFullPath(context, input)

            // 先尝试获取LuaContext
            val luaContext = getLuaContext(context)

            if (luaContext != null) {
                // 使用LuaBitmapDrawable
                val drawable = LuaBitmapDrawable(luaContext, fullPath)

                // 处理颜色参数
                if (color != null && color != 0) {
                    // 对于LuaBitmapDrawable，我们无法直接设置颜色
                    // 这里我们创建一个新的BitmapDrawable并应用颜色
                    return createColoredDrawable(context, fullPath, color, size, unit)
                } else {
                    // 直接返回LuaBitmapDrawable
                    return drawable
                }
            } else {
                // 如果没有LuaContext，使用普通方法
                createColoredDrawable(context, fullPath, color, size, unit)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取Bitmap - 使用老方法
     */
    @JvmStatic
    fun getBitmap(context: Context, input: String): Bitmap? {
        return try {
            val fullPath = getFullPath(context, input)
            val luaContext = getLuaContext(context)

            if (luaContext != null) {
                LuaBitmap.getBitmap(luaContext, fullPath)
            } else {
                // 尝试直接使用LuaBitmap
                val luaApp = LuaApplication.getInstance()
                LuaBitmap.getBitmap(luaApp, fullPath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 创建带颜色的Drawable
     */
    private fun createColoredDrawable(
        context: Context,
        path: String,
        color: Int?,
        size: Any?,
        unit: Any?
    ): BitmapDrawable? {
        return try {
            // 获取Bitmap
            val bitmap = getBitmapFromPath(context, path) ?: return null

            // 处理大小参数
            val (sizeFloat, unitInt) = parseSize(size, unit)

            // 应用缩放
            var processedBitmap = bitmap
            if (sizeFloat != null && sizeFloat > 0) {
                processedBitmap = scaleBitmapInternal(bitmap, sizeFloat, unitInt)
            }

            // 创建Drawable并应用颜色过滤
            val drawable = BitmapDrawable(context.resources, processedBitmap)

            if (color != null && color != 0) {
                val colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
                drawable.colorFilter = colorFilter
            }

            drawable
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从路径获取Bitmap
     */
    private fun getBitmapFromPath(context: Context, path: String): Bitmap? {
        return try {
            if (path.startsWith("http://") || path.startsWith("https://")) {
                // 网络图片
                val luaApp = LuaApplication.getInstance()
                LuaBitmap.getHttpBitmap(luaApp, path)
            } else {
                // 本地图片
                val file = File(path)
                if (file.exists()) {
                    val options = BitmapFactory.Options()
                    BitmapFactory.decodeFile(path, options)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 缩放Bitmap
     */
    private fun scaleBitmapInternal(bitmap: Bitmap, size: Float, unit: Int?): Bitmap {
        val originalSize = bitmap.width.toFloat()
        unit ?: TypedValue.COMPLEX_UNIT_DIP
        val targetSizePx = size // 这里需要根据单位转换，简化处理

        val scale = targetSizePx / originalSize

        if (scale == 1f) return bitmap

        val matrix = Matrix()
        matrix.postScale(scale, scale)
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    /**
     * 解析颜色值
     */
    @JvmStatic
    fun parseColor(color: Any?): Int? {
        if (color == null) return null

        return when (color) {
            is Int -> color
            is Long -> color.toInt()
            is Number -> color.toInt()
            else -> {
                try {
                    // 尝试解析颜色字符串
                    when {
                        color.toString().startsWith("#") -> {
                            val colorStr = color.toString()
                            when (colorStr.length) {
                                7 -> Color.parseColor(colorStr) // #RRGGBB
                                9 -> Color.parseColor(colorStr) // #AARRGGBB
                                else -> null
                            }
                        }

                        color.toString().startsWith("0x") -> {
                            val colorStr = color.toString().substring(2)
                            colorStr.toIntOrNull(16)
                        }

                        else -> color.toString().toIntOrNull()
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 解析大小和单位参数
     */
    private fun parseSize(size: Any?, unit: Any?): Pair<Float?, Int?> {
        var sizeFloat: Float? = null
        var unitInt: Int? = null

        if (size != null) {
            sizeFloat = when (size) {
                is Float -> size
                is Double -> size.toFloat()
                is Int -> size.toFloat()
                is Long -> size.toFloat()
                is Number -> size.toFloat()
                else -> {
                    try {
                        size.toString().toFloatOrNull()
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            if (unit != null) {
                unitInt = when (unit) {
                    is Int -> unit
                    is Long -> unit.toInt()
                    is Number -> unit.toInt()
                    else -> {
                        try {
                            unit.toString().toIntOrNull()
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
        }

        return Pair(sizeFloat, unitInt)
    }

    /**
     * 获取完整路径
     */
    private fun getFullPath(context: Context, url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/")) {
            return url
        }

        val luaDir = getLuaDir(context) ?: return url
        val luaExtDir = getLuaExtDir(context)

        val pathsToTry = listOf(
            File(luaDir, url),
            File(luaExtDir, url),
            File(context.filesDir, url),
            File(context.externalCacheDir, url),
            File(url)
        )

        for (file in pathsToTry) {
            if (file.exists()) {
                return file.absolutePath
            }
        }

        return File(luaDir, url).absolutePath
    }

    private fun getLuaDir(context: Context): String? {
        return when (context) {
            is LuaContext -> context.luaDir
            else -> {
                val app = context.applicationContext
                if (app is LuaApplication) {
                    app.localDir
                } else {
                    null
                }
            }
        }
    }

    private fun getLuaExtDir(context: Context): String {
        return when (context) {
            is LuaContext -> context.luaExtDir
            else -> {
                val app = context.applicationContext
                if (app is LuaApplication) {
                    app.luaExtDir
                } else {
                    context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
                }
            }
        }
    }

    private fun getLuaContext(context: Context): LuaContext? {
        return when (context) {
            is LuaContext -> context
            else -> {
                var currentContext: Context? = context
                while (currentContext != null) {
                    if (currentContext is LuaContext) {
                        return currentContext
                    }
                    if (currentContext is android.content.ContextWrapper) {
                        currentContext = currentContext.baseContext
                    } else {
                        break
                    }
                }
                null
            }
        }
    }
}