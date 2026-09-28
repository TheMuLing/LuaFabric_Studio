package com.luafabric.studio.falling.core

import android.content.Context
import java.io.File

/**
 * Studio 私有 MMKV 桥（对应 native studio_mmkv.cpp）。
 * 存储域：私有根目录 filesDir/.studio_mmkv/（显式 rootPath，不触碰全局默认根）；
 * 密钥域：Android Keystore 运行时派生的 AES-256 cryptKey。
 * 与 Lua 侧 require "mmkv" 绑定（默认根+明文、无可指定目录/密钥的 API 面）完全隔离。
 *
 * 存量 DataStore 数据按用户决策「直接切换，存量作废」：首次初始化时删除旧
 * app_login / app_settings 的 .preferences_pb 残留（含明文 pass/user_json）。
 */
object StudioMmkv {
    const val ID_LOGIN = "studio_login"
    const val ID_SETTINGS = "studio_settings"

    private const val ROOT_DIR_NAME = ".studio_mmkv"

    @Volatile
    private var initialized = false

    private external fun nativeInit(root: String, key: ByteArray)
    private external fun nativeGetString(id: String, key: String): String?
    private external fun nativePutString(id: String, key: String, value: String)
    private external fun nativeGetBoolean(id: String, key: String, def: Boolean): Boolean
    private external fun nativePutBoolean(id: String, key: String, value: Boolean)
    private external fun nativeGetInt(id: String, key: String, def: Int): Int
    private external fun nativePutInt(id: String, key: String, value: Int)
    private external fun nativeGetLong(id: String, key: String, def: Long): Long
    private external fun nativePutLong(id: String, key: String, value: Long)
    private external fun nativeGetFloat(id: String, key: String, def: Float): Float
    private external fun nativePutFloat(id: String, key: String, value: Float)
    private external fun nativeRemove(id: String, key: String)
    private external fun nativeClear(id: String)

    @Synchronized
    fun ensureInit(context: Context) {
        if (initialized) return
        System.loadLibrary("mmkv")
        val root = File(context.filesDir, ROOT_DIR_NAME).absolutePath
        nativeInit(root, KeystoreCrypto.deriveMmkvKey())
        deleteLegacyDataStoreFiles(context)
        initialized = true
    }

    /** 删除旧版 DataStore 残留；仅命中 app_login/app_settings 相关文件，尽力而为 */
    private fun deleteLegacyDataStoreFiles(context: Context) {
        val dir = File(context.filesDir, "datastore")
        if (!dir.isDirectory) return
        val targets = arrayOf("app_login.preferences_pb", "app_settings.preferences_pb")
        dir.listFiles()?.forEach { f ->
            val name = f.name
            if (!f.isFile) return@forEach
            if (targets.any { name == it || name.startsWith("$it.") }) {
                runCatching { f.delete() }
            }
        }
    }

    fun contains(context: Context, id: String, key: String): Boolean {
        ensureInit(context)
        return nativeGetString(id, key) != null
    }

    fun getString(context: Context, id: String, key: String): String? {
        ensureInit(context)
        return nativeGetString(id, key)
    }

    fun putString(context: Context, id: String, key: String, value: String) {
        ensureInit(context)
        nativePutString(id, key, value)
    }

    fun getBoolean(context: Context, id: String, key: String, def: Boolean): Boolean {
        ensureInit(context)
        return nativeGetBoolean(id, key, def)
    }

    fun putBoolean(context: Context, id: String, key: String, value: Boolean) {
        ensureInit(context)
        nativePutBoolean(id, key, value)
    }

    fun getInt(context: Context, id: String, key: String, def: Int): Int {
        ensureInit(context)
        return nativeGetInt(id, key, def)
    }

    fun putInt(context: Context, id: String, key: String, value: Int) {
        ensureInit(context)
        nativePutInt(id, key, value)
    }

    fun getLong(context: Context, id: String, key: String, def: Long): Long {
        ensureInit(context)
        return nativeGetLong(id, key, def)
    }

    fun putLong(context: Context, id: String, key: String, value: Long) {
        ensureInit(context)
        nativePutLong(id, key, value)
    }

    fun getFloat(context: Context, id: String, key: String, def: Float): Float {
        ensureInit(context)
        return nativeGetFloat(id, key, def)
    }

    fun putFloat(context: Context, id: String, key: String, value: Float) {
        ensureInit(context)
        nativePutFloat(id, key, value)
    }

    fun remove(context: Context, id: String, key: String) {
        ensureInit(context)
        nativeRemove(id, key)
    }

    fun clear(context: Context, id: String) {
        ensureInit(context)
        nativeClear(id)
    }
}