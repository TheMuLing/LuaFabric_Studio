/**
 * @file luaparser_jni.c
 * @brief JNI 接口实现 - Lua语法分析器（线程安全版）
 * 
 * 实现与 Java 层的 LuaParserUtil 类对接
 * 使用 Lua 官方解析器进行语法检查
 * 修改：添加互斥锁保护全局 Lua 状态机，防止多线程并发访问
 */

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>
#include <android/log.h>
#include <pthread.h>  // 添加互斥锁支持

#include "lua.h"
#include "lauxlib.h"
#include "lualib.h"

// 日志宏
#define LOG_TAG "luaparser_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// 全局Lua状态机，用于语法检查
static lua_State *gLuaState = NULL;

// 互斥锁，保护对 gLuaState 的所有访问
static pthread_mutex_t gLuaMutex = PTHREAD_MUTEX_INITIALIZER;

/**
 * @brief 内部初始化Lua状态机（调用者必须已持有锁）
 * @return 成功返回1，失败返回0
 */
static int ensure_lua_state_locked() {
    if (gLuaState != NULL) {
        return 1;
    }

    LOGD("初始化Lua状态机");

    // 创建新的Lua状态机
    gLuaState = luaL_newstate();
    if (gLuaState == NULL) {
        LOGE("无法创建Lua状态机");
        return 0;
    }

    // 打开基础库（只打开必要的，减少内存占用）
    luaL_openlibs(gLuaState);

    LOGD("Lua状态机初始化完成");
    return 1;
}

/**
 * @brief 初始化Lua状态机（对外接口，自动加锁）
 * @return 成功返回1，失败返回0
 */
static int init_lua_state() {
    pthread_mutex_lock(&gLuaMutex);
    int ret = ensure_lua_state_locked();
    pthread_mutex_unlock(&gLuaMutex);
    return ret;
}

/**
 * @brief 清理Lua状态机（调用者必须已持有锁）
 */
static void cleanup_lua_state_locked() {
    if (gLuaState != NULL) {
        LOGD("清理Lua状态机");
        lua_close(gLuaState);
        gLuaState = NULL;
    }
}

/**
 * @brief 清理Lua状态机（对外接口，自动加锁）
 */
static void cleanup_lua_state() {
    pthread_mutex_lock(&gLuaMutex);
    cleanup_lua_state_locked();
    pthread_mutex_unlock(&gLuaMutex);
}

/**
 * @brief JSON转义函数
 * @param input 输入字符串
 * @param output 输出缓冲区
 * @param out_size 输出缓冲区大小
 * @return 转义后的字符串指针
 */
static const char *json_escape(const char *input, char *output, size_t out_size) {
    if (!input || !output || out_size == 0) {
        return "";
    }

    char *p = output;
    size_t remaining = out_size - 1;

    while (*input != '\0' && remaining > 1) {
        switch (*input) {
            case '\"':
                if (remaining < 2) break;
                *p++ = '\\';
                *p++ = '\"';
                remaining -= 2;
                break;
            case '\\':
                if (remaining < 2) break;
                *p++ = '\\';
                *p++ = '\\';
                remaining -= 2;
                break;
            case '\b':
                if (remaining < 2) break;
                *p++ = '\\';
                *p++ = 'b';
                remaining -= 2;
                break;
            case '\f':
                if (remaining < 2) break;
                *p++ = '\\';
                *p++ = 'f';
                remaining -= 2;
                break;
            case '\n':
                if (remaining < 2) break;
                *p++ = '\\';
                *p++ = 'n';
                remaining -= 2;
                break;
            case '\r':
                if (remaining < 2) break;
                *p++ = '\\';
                *p++ = 'r';
                remaining -= 2;
                break;
            case '\t':
                if (remaining < 2) break;
                *p++ = '\\';
                *p++ = 't';
                remaining -= 2;
                break;
            default:
                // 只允许可打印字符
                if ((unsigned char) *input >= 32 && (unsigned char) *input <= 126) {
                    *p++ = *input;
                    remaining -= 1;
                }
                break;
        }
        input++;
    }

    *p = '\0';
    return output;
}

/**
 * @brief 解析Lua错误信息，提取行号和错误描述
 * @param errmsg Lua错误信息
 * @param line [out] 行号
 * @param message [out] 错误描述
 * @param message_size 错误描述缓冲区大小
 */
static void parse_lua_error(const char *errmsg, int *line, char *message, size_t message_size) {
    // 默认值
    *line = 1;

    if (!errmsg) {
        snprintf(message, message_size, "未知错误");
        return;
    }

    LOGD("原始错误信息: %s", errmsg);

    // 新格式错误信息: tokenpos: 0, Line: 10, LastToken: '\"i', description: [string "..."]:10: unfinished string near '"i'
    // 我们需要提取description:之后的内容

    const char *description_start = strstr(errmsg, "description:");
    if (description_start) {
        // 找到description:，移动到description:之后
        description_start += strlen("description:");

        // 跳过可能的空格
        while (*description_start && isspace((unsigned char) *description_start)) {
            description_start++;
        }

        // 复制description之后的内容作为错误信息
        size_t copy_len = 0;
        const char *p = description_start;

        // 计算要复制的长度（直到字符串结束）
        while (*p && copy_len < message_size - 1) {
            p++;
            copy_len++;
        }

        if (copy_len > 0) {
            strncpy(message, description_start, message_size - 1);
            message[message_size - 1] = '\0';
        } else {
            snprintf(message, message_size, "%s", errmsg);
        }

        // 解析行号（从原始错误信息中提取）
        // 查找 "Line: " 模式
        const char *line_start = strstr(errmsg, "Line:");
        if (line_start) {
            line_start += strlen("Line:");
            while (*line_start && isspace((unsigned char) *line_start)) {
                line_start++;
            }

            char line_str[32] = {0};
            int i = 0;
            while (*line_start && i < sizeof(line_str) - 1 &&
                   isdigit((unsigned char) *line_start)) {
                line_str[i++] = *line_start++;
            }
            line_str[i] = '\0';

            if (strlen(line_str) > 0) {
                *line = atoi(line_str);
                if (*line <= 0) *line = 1;
            }
        }
    } else {
        // 旧的Lua错误格式: [string "chunkname"]:行号: 错误信息
        // 或者: chunkname:行号: 错误信息

        // 查找最后一个冒号的位置（错误信息前的冒号）
        const char *last_colon = NULL;
        const char *second_last_colon = NULL;
        const char *p = errmsg;

        while (*p) {
            if (*p == ':') {
                second_last_colon = last_colon;
                last_colon = p;
            }
            p++;
        }

        if (last_colon && second_last_colon) {
            // 解析行号（第二个冒号和最后一个冒号之间的内容）
            const char *line_start = second_last_colon + 1;
            const char *line_end = last_colon;

            // 跳过空格
            while (line_start < line_end && isspace((unsigned char) *line_start)) {
                line_start++;
            }

            // 提取行号
            char line_str[32] = {0};
            int i = 0;
            while (line_start < line_end && i < sizeof(line_str) - 1 &&
                   isdigit((unsigned char) *line_start)) {
                line_str[i++] = *line_start++;
            }
            line_str[i] = '\0';

            if (strlen(line_str) > 0) {
                *line = atoi(line_str);
                if (*line <= 0) *line = 1;
            }

            // 错误信息是最后一个冒号之后的内容
            const char *msg_start = last_colon + 1;
            while (*msg_start && isspace((unsigned char) *msg_start)) {
                msg_start++;
            }

            // 复制错误信息
            snprintf(message, message_size, "%s", msg_start);
        } else {
            // 无法解析，直接使用整个错误信息
            snprintf(message, message_size, "%s", errmsg);
        }
    }

    // 清理换行符
    for (char *q = message; *q; q++) {
        if (*q == '\n' || *q == '\r') {
            *q = ' ';
        }
    }

    // 去除末尾空格
    size_t len = strlen(message);
    while (len > 0 && isspace((unsigned char) message[len - 1])) {
        message[len - 1] = '\0';
        len--;
    }

    LOGD("解析后: 行%d, 消息: %s", *line, message);
}

/**
 * @brief JNI函数：检查解析器是否可用
 */
JNIEXPORT jboolean JNICALL
Java_muling_views_tool_utils_LuaParserUtil_isParserAvailable(JNIEnv *env, jclass clazz) {
    LOGI("isParserAvailable() called");

    // 尝试初始化Lua状态机（init_lua_state 内部已加锁）
    if (init_lua_state()) {
        LOGI("解析器可用");
        return JNI_TRUE;
    } else {
        LOGW("解析器不可用");
        return JNI_FALSE;
    }
}

/**
 * @brief JNI函数：释放解析器资源
 */
JNIEXPORT void JNICALL
Java_muling_views_tool_utils_LuaParserUtil_releaseParser(JNIEnv *env, jclass clazz) {
    LOGI("releaseParser() called");
    cleanup_lua_state();  // 内部已加锁
}

/**
 * @brief JNI函数：分析Lua代码语法
 */
JNIEXPORT jstring JNICALL
Java_muling_views_tool_utils_LuaParserUtil_parseLuaSyntax(JNIEnv *env, jclass clazz,
                                                            jstring luaCode) {
    LOGI("parseLuaSyntax() called");

    // 检查输入
    if (luaCode == NULL) {
        LOGE("输入代码为NULL");
        const char *errorJson = "{\"status\":false,\"line\":1,\"message\":\"输入代码为空\"}";
        return (*env)->NewStringUTF(env, errorJson);
    }

    // 获取Lua代码字符串
    const char *codeStr = (*env)->GetStringUTFChars(env, luaCode, NULL);
    if (codeStr == NULL) {
        LOGE("无法获取Java字符串");
        const char *errorJson = "{\"status\":false,\"line\":1,\"message\":\"无法读取代码字符串\"}";
        return (*env)->NewStringUTF(env, errorJson);
    }

    size_t codeLen = strlen(codeStr);
    LOGI("Lua代码长度: %zu", codeLen);

    // 检查代码长度（空字符串快速返回，无需加锁）
    if (codeLen == 0) {
        (*env)->ReleaseStringUTFChars(env, luaCode, codeStr);
        const char *successJson = "{\"status\":true,\"message\":\"空代码，语法正确\"}";
        return (*env)->NewStringUTF(env, successJson);
    }

    // 加锁保护 Lua 状态机
    pthread_mutex_lock(&gLuaMutex);

    // 确保Lua状态机已初始化（使用内部加锁版本，因为已经持有了锁）
    if (!ensure_lua_state_locked()) {
        pthread_mutex_unlock(&gLuaMutex);
        (*env)->ReleaseStringUTFChars(env, luaCode, codeStr);
        const char *errorJson = "{\"status\":false,\"line\":1,\"message\":\"Lua解析器初始化失败\"}";
        return (*env)->NewStringUTF(env, errorJson);
    }

    // 保存当前栈顶，以便在发生错误时恢复
    int stack_top = lua_gettop(gLuaState);

    // 尝试编译Lua代码
    int load_result = luaL_loadstring(gLuaState, codeStr);

    // 释放Java字符串（必须在解锁前完成，因为 codeStr 是 JNI 引用，与锁无关）
    (*env)->ReleaseStringUTFChars(env, luaCode, codeStr);

    jstring result;
    if (load_result == LUA_OK) {
        // 语法正确
        LOGI("Lua语法检查通过");

        // 清理栈（弹出编译后的代码块）
        lua_settop(gLuaState, stack_top);

        const char *successJson = "{\"status\":true,\"message\":\"语法检查通过\"}";
        result = (*env)->NewStringUTF(env, successJson);
    } else {
        // 语法错误，获取错误信息
        const char *luaError = lua_tostring(gLuaState, -1);
        LOGI("Lua语法错误: %s", luaError ? luaError : "(null)");

        // 解析错误信息
        int line = 1;
        char message[1024];
        char escaped_message[2048];

        parse_lua_error(luaError, &line, message, sizeof(message));
        json_escape(message, escaped_message, sizeof(escaped_message));

        // 清理栈（弹出错误信息）
        lua_settop(gLuaState, stack_top);

        // 生成JSON格式的错误信息（删除column字段）
        char jsonError[4096];
        snprintf(jsonError, sizeof(jsonError),
                 "{"
                 "\"status\":false,"
                 "\"line\":%d,"
                 "\"message\":\"%s\""
                 "}",
                 line,
                 escaped_message
        );

        LOGI("返回错误JSON: %s", jsonError);
        result = (*env)->NewStringUTF(env, jsonError);
    }

    pthread_mutex_unlock(&gLuaMutex);
    return result;
}

/**
 * @brief JNI加载时调用
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGI("JNI_OnLoad() called");

    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad: 获取JNIEnv失败");
        return JNI_ERR;
    }

    LOGI("JNI版本: %d", JNI_VERSION_1_6);

    // 初始化Lua状态机（init_lua_state 内部已加锁）
    if (!init_lua_state()) {
        LOGW("JNI_OnLoad: Lua状态机初始化失败");
    }

    return JNI_VERSION_1_6;
}

/**
 * @brief JNI卸载时调用
 */
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    LOGI("JNI_OnUnload() called");
    cleanup_lua_state();  // 内部已加锁
}