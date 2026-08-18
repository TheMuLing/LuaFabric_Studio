package muling.views.tool.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.annotation.Keep
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import java.io.File

@Keep
object GlideUtil {

    /**
     * 加载图片到ImageView - 同步
     */
    @JvmStatic
    fun loadImage(
        context: Context,
        imgView: ImageView,
        url: String,
        placeholder: Any? = null,
        error: Any? = null,
        width: Int? = null,
        height: Int? = null,
        centerCrop: Boolean? = null,
        fitCenter: Boolean? = null,
        skipMemoryCache: Boolean? = null,
        diskCacheStrategy: String? = null,
        signature: Any? = null,
        crossFade: Boolean? = null,
        crossFadeDuration: Long? = null
    ) {
        try {
            // 获取完整路径
            val fullPath = getFullPath(context, url)

            // 创建RequestOptions
            val requestOptions = RequestOptions()

            // 设置占位符
            when (placeholder) {
                is Int -> requestOptions.placeholder(placeholder)
                is String -> {
                    val placeholderPath = getFullPath(context, placeholder)
                    if (File(placeholderPath).exists()) {
                        val placeholderBitmap = BitmapFactory.decodeFile(placeholderPath)
                        if (placeholderBitmap != null) {
                            requestOptions.placeholder(
                                BitmapDrawable(
                                    context.resources,
                                    placeholderBitmap
                                )
                            )
                        }
                    }
                }

                is BitmapDrawable -> requestOptions.placeholder(placeholder)
            }

            // 设置错误图
            when (error) {
                is Int -> requestOptions.error(error)
                is String -> {
                    val errorPath = getFullPath(context, error)
                    if (File(errorPath).exists()) {
                        val errorBitmap = BitmapFactory.decodeFile(errorPath)
                        if (errorBitmap != null) {
                            requestOptions.error(BitmapDrawable(context.resources, errorBitmap))
                        }
                    }
                }

                is BitmapDrawable -> requestOptions.error(error)
            }

            // 设置尺寸
            if (width != null && height != null) {
                requestOptions.override(width, height)
            }

            // 设置裁剪模式
            when {
                (centerCrop == true) -> requestOptions.centerCrop()
                (fitCenter == true) -> requestOptions.fitCenter()
            }

            // 设置缓存
            requestOptions.skipMemoryCache(skipMemoryCache ?: false)

            // 处理 diskCacheStrategy
            val cacheStrategy = diskCacheStrategy ?: "ALL"
            requestOptions.diskCacheStrategy(
                when (cacheStrategy.uppercase()) {
                    "NONE" -> DiskCacheStrategy.NONE
                    "DATA" -> DiskCacheStrategy.DATA
                    "RESOURCE" -> DiskCacheStrategy.RESOURCE
                    "AUTOMATIC" -> DiskCacheStrategy.AUTOMATIC
                    else -> DiskCacheStrategy.ALL
                }
            )

            // 设置缓存签名
            if (signature != null) {
                requestOptions.signature(ObjectKey(signature.toString()))
            }

            // 创建Glide请求
            val requestBuilder = Glide.with(context)
                .load(if (fullPath.startsWith("http")) fullPath else File(fullPath))
                .apply(requestOptions)

            // 添加淡入淡出动画
            if (crossFade == true) {
                if (crossFadeDuration != null && crossFadeDuration > 0) {
                    requestBuilder.transition(
                        DrawableTransitionOptions.withCrossFade(crossFadeDuration.toInt())
                    )
                } else {
                    requestBuilder.transition(DrawableTransitionOptions.withCrossFade())
                }
            }

            // 执行加载
            requestBuilder.into(imgView)

        } catch (e: Exception) {
            e.printStackTrace()
            // 加载错误图
            when (error) {
                is Int -> imgView.setImageResource(error)
                is String -> {
                    val errorPath = getFullPath(context, error)
                    if (File(errorPath).exists()) {
                        val errorBitmap = BitmapFactory.decodeFile(errorPath)
                        if (errorBitmap != null) {
                            imgView.setImageBitmap(errorBitmap)
                        }
                    }
                }

                is BitmapDrawable -> imgView.setImageDrawable(error)
            }
        }
    }

    /**
     * 加载圆形图片 - 同步
     */
    @JvmStatic
    fun loadCircleImage(
        context: Context,
        imgView: ImageView,
        url: String,
        placeholder: Any? = null,
        error: Any? = null
    ) {
        try {
            val fullPath = getFullPath(context, url)

            val requestOptions = RequestOptions()
                .transform(CircleCrop())  // 圆形裁剪

            when (placeholder) {
                is Int -> requestOptions.placeholder(placeholder)
                is String -> {
                    val placeholderPath = getFullPath(context, placeholder)
                    if (File(placeholderPath).exists()) {
                        val placeholderBitmap = BitmapFactory.decodeFile(placeholderPath)
                        if (placeholderBitmap != null) {
                            requestOptions.placeholder(
                                BitmapDrawable(
                                    context.resources,
                                    placeholderBitmap
                                )
                            )
                        }
                    }
                }
            }
            when (error) {
                is Int -> requestOptions.error(error)
                is String -> {
                    val errorPath = getFullPath(context, error)
                    if (File(errorPath).exists()) {
                        val errorBitmap = BitmapFactory.decodeFile(errorPath)
                        if (errorBitmap != null) {
                            requestOptions.error(BitmapDrawable(context.resources, errorBitmap))
                        }
                    }
                }
            }

            Glide.with(context)
                .load(if (fullPath.startsWith("http")) fullPath else File(fullPath))
                .apply(requestOptions)
                .into(imgView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 加载圆角图片 - 同步
     */
    @JvmStatic
    fun loadRoundedImage(
        context: Context,
        imgView: ImageView,
        url: String,
        radius: Int,  // 圆角半径，单位px
        placeholder: Any? = null,
        error: Any? = null
    ) {
        try {
            val fullPath = getFullPath(context, url)

            val requestOptions = RequestOptions()
                .transform(RoundedCorners(radius))

            when (placeholder) {
                is Int -> requestOptions.placeholder(placeholder)
                is String -> {
                    val placeholderPath = getFullPath(context, placeholder)
                    if (File(placeholderPath).exists()) {
                        val placeholderBitmap = BitmapFactory.decodeFile(placeholderPath)
                        if (placeholderBitmap != null) {
                            requestOptions.placeholder(
                                BitmapDrawable(
                                    context.resources,
                                    placeholderBitmap
                                )
                            )
                        }
                    }
                }
            }
            when (error) {
                is Int -> requestOptions.error(error)
                is String -> {
                    val errorPath = getFullPath(context, error)
                    if (File(errorPath).exists()) {
                        val errorBitmap = BitmapFactory.decodeFile(errorPath)
                        if (errorBitmap != null) {
                            requestOptions.error(BitmapDrawable(context.resources, errorBitmap))
                        }
                    }
                }
            }

            Glide.with(context)
                .load(if (fullPath.startsWith("http")) fullPath else File(fullPath))
                .apply(requestOptions)
                .into(imgView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 预加载图片 - 同步
     */
    @JvmStatic
    fun preloadImage(context: Context, url: String) {
        try {
            val fullPath = getFullPath(context, url)
            Glide.with(context)
                .load(if (fullPath.startsWith("http")) fullPath else File(fullPath))
                .preload()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 清理缓存 - 同步
     */
    @JvmStatic
    fun clearGlideCache(context: Context) {
        try {
            // 清理内存缓存
            Glide.get(context).clearMemory()

            // 在后台线程清理磁盘缓存
            Thread {
                Glide.get(context).clearDiskCache()
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 暂停请求 - 同步
     */
    @JvmStatic
    fun pauseRequests(context: Context) {
        Glide.with(context).pauseRequests()
    }

    /**
     * 恢复请求 - 同步
     */
    @JvmStatic
    fun resumeRequests(context: Context) {
        Glide.with(context).resumeRequests()
    }

    // ========== 私有辅助方法 ==========

    private fun getFullPath(context: Context, url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/")) {
            return url
        }

        // 尝试从Context中获取Lua目录
        val luaDir = getLuaDir(context)

        // 尝试多个可能的路径
        val pathsToTry = mutableListOf<File>()

        if (luaDir != null) {
            pathsToTry.add(File(luaDir, url))
        }

        pathsToTry.addAll(
            listOf(
                File(context.filesDir, url),
                File(context.externalCacheDir ?: context.cacheDir, url),
                File(context.getExternalFilesDir(null) ?: context.filesDir, url),
                File(url)
            )
        )

        for (file in pathsToTry) {
            if (file.exists()) {
                return file.absolutePath
            }
        }

        // 如果都不存在，优先返回基于LuaDir的路径
        if (luaDir != null) {
            return File(luaDir, url).absolutePath
        }

        // 否则返回基于filesDir的路径
        return File(context.filesDir, url).absolutePath
    }

    private fun getLuaDir(context: Context): String? {
        return try {
            // 尝试通过反射获取LuaContext的luaDir
            if (context is com.androlua.LuaContext) {
                return context.luaDir
            }

            // 检查父类
            var currentContext: Context? = context
            while (currentContext != null) {
                if (currentContext is com.androlua.LuaContext) {
                    return currentContext.luaDir
                }
                if (currentContext is android.content.ContextWrapper) {
                    currentContext = currentContext.baseContext
                } else {
                    break
                }
            }

            // 尝试获取application
            val app = context.applicationContext
            if (app is com.androlua.LuaApplication) {
                return app.localDir
            }

            null
        } catch (e: Exception) {
            null
        }
    }
}