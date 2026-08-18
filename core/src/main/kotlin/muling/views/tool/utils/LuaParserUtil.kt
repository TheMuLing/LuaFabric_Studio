package muling.views.tool.utils

import androidx.annotation.Keep
import org.json.JSONObject

/**
 * Lua语法分析器工具类
 * 调用luaparser.so库进行Lua代码语法分析
 */
@Keep
object LuaParserUtil {

    // 加载原生库
    init {
        try {
            System.loadLibrary("luaparser")
        } catch (e: Exception) {
            // 静默处理加载失败
        }
    }

    /**
     * 检查解析器是否可用
     * @return 如果解析器可用返回true，否则返回false
     */
    private external fun isParserAvailable(): Boolean

    /**
     * 释放解析器资源
     */
    private external fun releaseParser()

    /**
     * 分析Lua代码语法
     * @param luaCode Lua代码字符串
     * @return 包含分析结果的JSON字符串
     */
    private external fun parseLuaSyntax(luaCode: String): String

    /**
     * 解析Lua代码语法并返回JSON字符串
     * @param luaCode Lua代码字符串
     * @return JSON字符串格式的解析结果
     */
    @JvmStatic
    fun parse(luaCode: String): String {
        return try {
            // 检查输入是否为空
            if (luaCode.isBlank()) {
                return createSuccessJson("请输入内容")
            }

            // 调用JNI函数解析
            parseLuaSyntax(luaCode)

        } catch (e: UnsatisfiedLinkError) {
            createErrorJson("原生库未加载或函数未找到")
        } catch (e: org.json.JSONException) {
            createErrorJson("JSON解析失败")
        } catch (e: Exception) {
            createErrorJson("解析异常")
        }
    }

    /**
     * 解析Lua代码语法并返回JSON对象（供Java端使用）
     * @param luaCode Lua代码字符串
     * @return JSONObject格式的解析结果
     */
    @JvmStatic
    fun parseToJson(luaCode: String): JSONObject {
        val jsonString = parse(luaCode)
        return try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            createErrorJsonObject("JSON解析失败")
        }
    }

    /**
     * 创建成功JSON字符串
     */
    private fun createSuccessJson(message: String): String {
        return JSONObject().apply {
            put("status", true)
            put("message", message)
        }.toString()
    }

    /**
     * 创建错误JSON字符串
     */
    private fun createErrorJson(errorMessage: String): String {
        return JSONObject().apply {
            put("status", false)
            put("line", 1)
            put("message", errorMessage)
        }.toString()
    }

    /**
     * 创建错误JSON对象
     */
    private fun createErrorJsonObject(errorMessage: String): JSONObject {
        return JSONObject().apply {
            put("status", false)
            put("line", 1)
            put("message", errorMessage)
        }
    }

    /**
     * 清理解析器资源
     */
    @JvmStatic
    fun release() {
        try {
            releaseParser()
        } catch (e: Exception) {
            // 静默处理异常
        }
    }

    /**
     * 检查解析器是否初始化成功
     */
    @JvmStatic
    fun isAvailable(): Boolean {
        return try {
            isParserAvailable()
        } catch (e: Exception) {
            false
        }
    }
}