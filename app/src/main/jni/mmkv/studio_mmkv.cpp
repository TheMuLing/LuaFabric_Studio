// Studio 私有 MMKV 桥（luafabric-studio 专用，仅供 :app 进程调用）。
// 与 lua_mmkv.cpp 的「默认根目录 + 明文」域天然隔离：本桥每次都以显式
// rootPath（filesDir/.studio_mmkv/）+ AES-256 cryptKey 打开实例，绝不触碰
// 全局默认根目录，也不设置全局 MMKV 状态，因此 Lua 侧 mmkv.initialize()
// 无法把 Studio 已打开的私有实例重定向或降级为明文。
//
// 崩溃防护（实机 SIGSEGV 定位）：MMKV::mmkvWithID 在构造失败（如 rootPath
// 目录不可用）时返回 nullptr，此前链路未判空直接 m->xxx 导致
// MMKV::containsKey 空指针解引用（fault addr 0xe0）。故所有入口均判空，
// 失败时返回默认值/缺失语义，绝不令进程崩溃；nativeInit 预先确保目录存在。
#include <jni.h>
#include <string>
#include <sys/stat.h>

#include "MMKV.h"

static std::string s_rootDir;
static std::string s_cryptKey;

// 以显式 root + cryptKey 打开单实例 MMKV（非多进程，单进程内 Studio 专用）
static MMKV *sms(const std::string &id) {
    MMKVConfig config;
    config.mode = MMKV_SINGLE_PROCESS;
    config.rootPath = &s_rootDir;
    config.aes256 = true;
    config.cryptKey = &s_cryptKey;
    return MMKV::mmkvWithID(id, config);
}

static std::string jstr(JNIEnv *env, jstring s) {
    if (!s) return {};
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string r = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return r;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_luafabric_studio_falling_core_StudioMmkv_nativeInit(
        JNIEnv *env, jobject /*thiz*/, jstring root, jbyteArray key) {
    s_rootDir = jstr(env, root);
    // 确保私有根目录存在：目录缺失是 mmkvWithID 返回 nullptr 的最常见原因
    if (!s_rootDir.empty()) {
        mkdir(s_rootDir.c_str(), 0700);
    }
    jsize n = key ? env->GetArrayLength(key) : 0;
    if (n > 0) {
        jbyte *kb = env->GetByteArrayElements(key, nullptr);
        if (kb) {
            s_cryptKey.assign(reinterpret_cast<const char *>(kb), static_cast<size_t>(n));
            env->ReleaseByteArrayElements(key, kb, JNI_ABORT);
        }
    }
}

JNIEXPORT jstring JNICALL
Java_com_luafabric_studio_falling_core_StudioMmkv_nativeGetString(
        JNIEnv *env, jobject /*thiz*/, jstring id, jstring key) {
    MMKV *m = sms(jstr(env, id));
    if (!m) return nullptr;
    std::string k = jstr(env, key);
    std::string result;
    bool has = m->containsKey(k) && m->getString(k, result);
    return has ? env->NewStringUTF(result.c_str()) : nullptr;
}

JNIEXPORT void JNICALL
Java_com_luafabric_studio_falling_core_StudioMmkv_nativePutString(
        JNIEnv *env, jobject /*thiz*/, jstring id, jstring key, jstring value) {
    MMKV *m = sms(jstr(env, id));
    if (!m) return;
    m->set(jstr(env, value), jstr(env, key));
}

JNIEXPORT jboolean JNICALL
Java_com_luafabric_studio_falling_core_StudioMmkv_nativeGetBoolean(
        JNIEnv *env, jobject /*thiz*/, jstring id, jstring key, jboolean def) {
    MMKV *m = sms(jstr(env, id));
    if (!m) return def;
    std::string k = jstr(env, key);
    return m->getBool(k, def != JNI_FALSE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_luafabric_studio_falling_core_StudioMmkv_nativePutBoolean(
        JNIEnv *env, jobject /*thiz*/, jstring id, jstring key, jboolean value) {
    MMKV *m = sms(jstr(env, id));
    if (!m) return;
    m->set(value != JNI_FALSE, jstr(env, key));
}

JNIEXPORT jint JNICALL
Java_com_luafabric_studio_falling_core_StudioMmkv_nativeGetInt(
        JNIEnv *env, jobject /*thiz*/, jstring id, jstring key, jint def) {
    MMKV *m = sms(jstr(env, id));
    if (!m) return def;
    std::string k = jstr(env, key);
    return m->getInt32(k, def);
}

JNIEXPORT void JNICALL
Java_com_luafabric_studio_falling_core_StudioMmkv_nativePutInt(
        JNIEnv *env, jobject /*thiz*/, jstring id, jstring key, jint value) {
    MMKV *m = sms(jstr(env, id));
    if (!m) return;
    m->set(static_cast<int32_t>(value), jstr(env, key));
}

JNIEXPORT jlong JNICALL
Java_com_luafabric_studio_falling_core_StudioMmkv_nativeGetLong(
        JNIEnv *env, jobject /*thiz*/, jstring id, jstring key, jlong def) {
    MMKV *m = sms(jstr(env, id));
    if (!m) return def;
    std::string k = jstr(env, key);
    return m->getInt64(k, static_cast<int64_t>(def));
}

JNIEXPORT void JNICALL
Java_com_luafabric_studio_falling_core_StudioMmkv_nativePutLong(
        JNIEnv *env, jobject /*thiz*/, jstring id, jstring key, jlong value) {
    MMKV *m = sms(jstr(env, id));
    if (!m) return;
    m->set(static_cast<int64_t>(value), jstr(env, key));
}

JNIEXPORT jfloat JNICALL
Java_com_luafabric_studio_falling_core_StudioMmkv_nativeGetFloat(
        JNIEnv *env, jobject /*thiz*/, jstring id, jstring key, jfloat def) {
    MMKV *m = sms(jstr(env, id));
    if (!m) return def;
    std::string k = jstr(env, key);
    return m->getFloat(k, def);
}

JNIEXPORT void JNICALL
Java_com_luafabric_studio_falling_core_StudioMmkv_nativePutFloat(
        JNIEnv *env, jobject /*thiz*/, jstring id, jstring key, jfloat value) {
    MMKV *m = sms(jstr(env, id));
    if (!m) return;
    m->set(value, jstr(env, key));
}

JNIEXPORT void JNICALL
Java_com_luafabric_studio_falling_core_StudioMmkv_nativeContains(
        JNIEnv *env, jobject /*thiz*/, jstring id, jstring key) {
    // 占位：contains 语义由 Kotlin 侧经 nativeGetString/Get*+默认值实现，
    // 此处保留导出符号便于未来扩展。
}

JNIEXPORT void JNICALL
Java_com_luafabric_studio_falling_core_StudioMmkv_nativeRemove(
        JNIEnv *env, jobject /*thiz*/, jstring id, jstring key) {
    MMKV *m = sms(jstr(env, id));
    if (!m) return;
    m->removeValueForKey(jstr(env, key));
}

JNIEXPORT void JNICALL
Java_com_luafabric_studio_falling_core_StudioMmkv_nativeClear(
        JNIEnv *env, jobject /*thiz*/, jstring id) {
    MMKV *m = sms(jstr(env, id));
    if (!m) return;
    m->clearAll();
}

} // extern "C"