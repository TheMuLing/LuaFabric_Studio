package com.luafabric.studio.falling.ui.editor.viewmodel

import android.content.Context
import com.luafabric.studio.falling.langs.lua.completion.CompletionName
import com.luafabric.studio.falling.langs.lua.tools.ClassMethodScanner
import com.luafabric.studio.falling.langs.lua.tools.CompleteHashmapUtils
import com.luafabric.studio.falling.langs.lua.tools.PackageUtil
import muling.views.tool.utils.LogCatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.system.measureTimeMillis

object CompletionDataManager {

    private var isInitialized = false
    private var isInitializing = false
    private var initializationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var _completionClassMap: HashMap<String, List<String>>? = null
    private var _completionBaseMap: HashMap<String, HashMap<String, CompletionName>>? = null
    private var _androidClasses: MutableSet<String>? = null

    private val loadLock = Any()
    private val listeners = mutableListOf<OnCompletionDataListener>()

    interface OnCompletionDataListener {
        fun onCompletionDataLoaded(
            classMap: HashMap<String, List<String>>?,
            baseMap: HashMap<String, HashMap<String, CompletionName>>?,
            androidClasses: MutableSet<String>?
        )
        fun onCompletionDataLoadFailed(error: String? = null)
        fun onProgress(progress: Float)
    }

    fun addListener(listener: OnCompletionDataListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
                LogCatcher.i("CompletionDataManager", "添加监听器，当前总数: ${listeners.size}")
                if (isInitialized) {
                    LogCatcher.i("CompletionDataManager", "数据已初始化，立即通知新监听器")
                    listener.onCompletionDataLoaded(
                        _completionClassMap,
                        _completionBaseMap,
                        _androidClasses
                    )
                }
            }
        }
    }

    fun removeListener(listener: OnCompletionDataListener) {
        synchronized(listeners) {
            listeners.remove(listener)
            LogCatcher.i("CompletionDataManager", "移除监听器，剩余: ${listeners.size}")
        }
    }

    private fun notifyProgress(progress: Float) {
        synchronized(listeners) {
            listeners.forEach { it.onProgress(progress) }
        }
    }

    fun initialize(context: Context) {
        synchronized(loadLock) {
            if (isInitialized || isInitializing) {
                LogCatcher.i("CompletionDataManager", "初始化已在进行或已完成，跳过")
                return
            }

            // 如果 scope 已被取消，重建一个
            if (initializationScope.coroutineContext[Job]?.isActive != true) {
                initializationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                LogCatcher.i("CompletionDataManager", "scope 已取消，重建新 scope")
            }

            LogCatcher.i("CompletionDataManager", "开始初始化补全数据，准备启动协程")
            isInitializing = true
        }

        // 在同步块外启动协程，避免锁的持有影响协程调度
        initializationScope.launch {
            LogCatcher.i("CompletionDataManager", "协程已启动")
            try {
                // 步骤1：加载 classMap
                notifyProgress(0.0f)
                LogCatcher.i("CompletionDataManager", "步骤1: 加载 classMap - 开始")
                val classMapLoadTime = measureTimeMillis {
                    _completionClassMap = loadClassMap(context)
                }
                LogCatcher.i("CompletionDataManager", "classMap 加载完成，大小: ${_completionClassMap?.size}，耗时: ${classMapLoadTime}ms")
                notifyProgress(0.33f)

                yield()

                // 步骤2：提取 androidClasses
                LogCatcher.i("CompletionDataManager", "步骤2: 提取 androidClasses - 开始")
                val androidClassesExtractTime = measureTimeMillis {
                    _androidClasses = extractAndroidClassNames(_completionClassMap)
                }
                LogCatcher.i("CompletionDataManager", "androidClasses 提取完成，大小: ${_androidClasses?.size}，耗时: ${androidClassesExtractTime}ms")
                notifyProgress(0.5f)

                yield()

                // 步骤3：加载 baseMap
                LogCatcher.i("CompletionDataManager", "步骤3: 加载 baseMap - 开始")
                val baseMapLoadTime = measureTimeMillis {
                    _completionBaseMap = loadBaseMap(context, _androidClasses) { scanProgress ->
                        // scanProgress 是 0..1 表示扫描阶段的进度，映射到总进度的 0.5..1.0 区间
                        notifyProgress(0.5f + scanProgress * 0.5f)
                    }
                }
                LogCatcher.i("CompletionDataManager", "baseMap 加载完成，大小: ${_completionBaseMap?.size}，耗时: ${baseMapLoadTime}ms")
                notifyProgress(1.0f)

                isInitialized = true
                isInitializing = false
                LogCatcher.i("CompletionDataManager", "初始化成功，准备通知 ${listeners.size} 个监听器")

                synchronized(listeners) {
                    listeners.forEach { listener ->
                        try {
                            listener.onCompletionDataLoaded(
                                _completionClassMap,
                                _completionBaseMap,
                                _androidClasses
                            )
                        } catch (e: Exception) {
                            LogCatcher.e("CompletionDataManager", "通知监听器时发生异常", e)
                        }
                    }
                }
                LogCatcher.i("CompletionDataManager", "所有监听器通知完成")
            } catch (e: Exception) {
                isInitializing = false
                LogCatcher.e("CompletionDataManager", "初始化异常: ${e.message}", e)
                notifyProgress(1.0f) // 异常时也结束进度
                synchronized(listeners) {
                    listeners.forEach { listener ->
                        try {
                            listener.onCompletionDataLoadFailed(e.message)
                        } catch (ex: Exception) {
                            LogCatcher.e("CompletionDataManager", "通知监听器失败异常", ex)
                        }
                    }
                }
            }
        }
    }

    fun getCompletionData(): Triple<HashMap<String, List<String>>?,
            HashMap<String, HashMap<String, CompletionName>>?,
            MutableSet<String>?> {
        return Triple(_completionClassMap, _completionBaseMap, _androidClasses)
    }

    fun isInitialized(): Boolean = isInitialized

    fun clear() {
        synchronized(loadLock) {
            LogCatcher.i("CompletionDataManager", "清理数据")
            initializationScope.cancel()
            _completionClassMap?.clear()
            _completionBaseMap?.clear()
            _androidClasses?.clear()
            _completionClassMap = null
            _completionBaseMap = null
            _androidClasses = null
            isInitialized = false
            isInitializing = false
            synchronized(listeners) { listeners.clear() }
            LogCatcher.i("CompletionDataManager", "清理完成")
        }
    }

    fun reload(context: Context) {
        synchronized(loadLock) {
            LogCatcher.i("CompletionDataManager", "重新加载数据")
            clear()
            initialize(context)
        }
    }

    private suspend fun loadClassMap(context: Context): HashMap<String, List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val loadedMap = CompleteHashmapUtils.loadHashMapFromFile2(context, "classMap.dat")
                if (loadedMap != null) {
                    LogCatcher.i("CompletionDataManager", "从文件加载 classMap 成功，大小: ${loadedMap.size}")
                    return@withContext loadedMap
                }
                LogCatcher.i("CompletionDataManager", "classMap.dat 文件不存在，从 PackageUtil 加载")
                val packageUtilMap = PackageUtil.load(context)
                LogCatcher.i("CompletionDataManager", "PackageUtil 加载完成，大小: ${packageUtilMap.size}")
                val result = HashMap<String, List<String>>()
                packageUtilMap.forEach { (key, value) -> result[key] = value }
                CompleteHashmapUtils.saveHashMapToFile2(context, result, "classMap.dat")
                LogCatcher.i("CompletionDataManager", "classMap 已保存到文件")
                result
            } catch (e: Exception) {
                LogCatcher.e("CompletionDataManager", "loadClassMap 异常: ${e.message}", e)
                HashMap()
            }
        }

    private suspend fun loadBaseMap(
        context: Context,
        androidClasses: MutableSet<String>?,
        onScanProgress: (Float) -> Unit
    ): HashMap<String, HashMap<String, CompletionName>> =
        withContext(Dispatchers.IO) {
            try {
                val loadedMap = CompleteHashmapUtils.loadHashMapFromFile(context, "baseMap.dat")
                if (loadedMap != null) {
                    LogCatcher.i("CompletionDataManager", "从文件加载 baseMap 成功，大小: ${loadedMap.size}")
                    return@withContext loadedMap
                }
                LogCatcher.i("CompletionDataManager", "baseMap.dat 文件不存在，开始动态扫描")
                val classNames = androidClasses?.toList() ?: emptyList()
                LogCatcher.i("CompletionDataManager", "准备扫描 ${classNames.size} 个类")
                if (classNames.isNotEmpty()) {
                    val scanner = ClassMethodScanner()
                    val scannedMap = scanner.scanClassesAndMethods(classNames) { progress ->
                        onScanProgress(progress)
                    }
                    LogCatcher.i("CompletionDataManager", "扫描完成，生成 baseMap 大小: ${scannedMap.size}")
                    CompleteHashmapUtils.saveHashMapToFile(context, scannedMap, "baseMap.dat")
                    LogCatcher.i("CompletionDataManager", "baseMap 已保存到文件")
                    return@withContext scannedMap
                }
                LogCatcher.w("CompletionDataManager", "没有类可扫描，返回空 baseMap")
                HashMap()
            } catch (e: Exception) {
                LogCatcher.e("CompletionDataManager", "loadBaseMap 异常: ${e.message}", e)
                HashMap()
            }
        }

    private fun extractAndroidClassNames(classMap: HashMap<String, List<String>>?): MutableSet<String> {
        if (classMap == null) {
            LogCatcher.w("CompletionDataManager", "classMap 为空，返回空集合")
            return mutableSetOf()
        }
        val classNames = mutableSetOf<String>()
        classMap.values.forEach { classList ->
            classList.forEach { className -> classNames.add(className) }
        }
        LogCatcher.i("CompletionDataManager", "提取出 ${classNames.size} 个类名")
        return classNames
    }
}