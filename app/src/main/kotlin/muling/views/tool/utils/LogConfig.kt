package muling.views.tool.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogConfigState(
    val isLogEnabled: Boolean = true,
    val logFilePath: String = "",
    val isLoaded: Boolean = false
)

class LogConfigRepository(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("webide_log_config", Context.MODE_PRIVATE)

    private val LOG_ENABLED_KEY = "log_enabled"

    private val defaultLogFilePath: String =
        File(context.filesDir, "logs/LuaFabric.log").absolutePath

    fun getLogConfig(): LogConfigState {
        val isEnabled = sharedPreferences.getBoolean(LOG_ENABLED_KEY, true)
        return LogConfigState(
            isLogEnabled = isEnabled,
            logFilePath = defaultLogFilePath,
            isLoaded = true
        )
    }

    fun saveLogConfig(isEnabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(LOG_ENABLED_KEY, isEnabled)
            .apply()

        // 立即更新
        LogCatcher.updateConfig(
            LogConfigState(
                isLogEnabled = isEnabled,
                logFilePath = defaultLogFilePath,
                isLoaded = true
            )
        )
    }
}

/**
 * 增强的日志捕获器，支持文件输出和开关控制
 */
object LogCatcher {

    private var logConfig: LogConfigState = LogConfigState()

    @Volatile
    private var isInitialized = false

    @JvmStatic
    fun updateConfig(config: LogConfigState) {
        logConfig = config
        isInitialized = true
        android.util.Log.e(
            "LogCatcher",
            "日志系统配置已更新 - 启用: ${config.isLogEnabled}, 路径: ${config.logFilePath}"
        )
    }

    /**
     * 初始化并在启动时写入强制日志
     */
    @JvmStatic
    fun init(context: Context) {
        android.util.Log.e("LogCatcher", "开始初始化日志系统...")
        val repository = LogConfigRepository(context)
        updateConfig(repository.getLogConfig())

        // 清空日志文件内容（每次启动时）
        clearLogFile()

        // 写入初始化日志
        i("LogCatcher", "日志系统初始化完成，日志文件已清空并重新开始记录")
    }

    /**
     * 清空日志文件内容
     */
    @JvmStatic
    fun clearLogFile() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val logFile = File(logConfig.logFilePath)

                // 确保父目录存在
                val parentDir = logFile.parentFile
                if (!parentDir.exists()) {
                    parentDir.mkdirs()
                }

                // 如果文件存在，清空内容；如果不存在，创建空文件
                if (logFile.exists()) {
                    logFile.writeText("")
                    android.util.Log.i("LogCatcher", "已清空日志文件: ${logConfig.logFilePath}")
                } else {
                    logFile.createNewFile()
                    android.util.Log.i(
                        "LogCatcher",
                        "已创建新的空日志文件: ${logConfig.logFilePath}"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("LogCatcher", "清空日志文件失败: ${e.message}")
            }
        }
    }

    @JvmStatic
    fun d(tag: String, message: String) {
        android.util.Log.d(tag, message)
        if (shouldLog()) {
            writeToFile("DEBUG", tag, message)
        }
    }

    @JvmStatic
    fun i(tag: String, message: String) {
        android.util.Log.i(tag, message)
        if (shouldLog()) {
            writeToFile("INFO", tag, message)
        }
    }

    @JvmStatic
    fun w(tag: String, message: String) {
        android.util.Log.w(tag, message)
        if (shouldLog()) {
            writeToFile("WARN", tag, message)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun e(tag: String, message: String, exception: Exception? = null) {
        android.util.Log.e(tag, message, exception)
        if (shouldLog()) {
            val exceptionInfo =
                exception?.let { " - ${it.message}\n${it.stackTraceToString()}" } ?: ""
            writeToFile("ERROR", tag, "$message$exceptionInfo")
        }
    }

    @JvmStatic
    fun permission(tag: String, action: String, result: String) {
        val message = "权限操作: $action - $result"
        android.util.Log.i("$tag-Permission", message)
        if (shouldLog()) {
            writeToFile("PERMISSION", tag, message)
        }
    }

    @JvmStatic
    fun fileOperation(tag: String, operation: String, filePath: String, result: String) {
        val message = "文件操作: $operation - $filePath - $result"
        android.util.Log.i("$tag-File", message)
        if (shouldLog()) {
            writeToFile("FILE", tag, message)
        }
    }

    private fun shouldLog(): Boolean {
        val ready = isInitialized && logConfig.isLogEnabled
        if (!ready) {
            android.util.Log.w(
                "LogCatcher",
                "日志未就绪 - isInitialized: $isInitialized, isEnabled: ${logConfig.isLogEnabled}"
            )
        }
        return ready
    }

    private fun writeToFile(level: String, tag: String, message: String) {
        // 使用 IO 调度器在后台线程执行
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val logFile = File(logConfig.logFilePath)

                // 确保父目录存在
                val parentDir = logFile.parentFile
                if (!parentDir.exists()) {
                    parentDir.mkdirs()
                }

                // 确保文件存在
                if (!logFile.exists()) {
                    logFile.createNewFile()
                }

                val timestamp =
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                val logEntry = "[$timestamp] [$level] [$tag] $message\n"

                logFile.appendText(logEntry)
            } catch (e: Exception) {
                android.util.Log.e("LogCatcher", "写入日志文件失败: ${e.message}")
            }
        }
    }
}