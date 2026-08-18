package com.luafabric.studio.falling.langs.lua.tools

import android.content.Context
import com.luafabric.studio.falling.langs.lua.completion.CompletionName
import muling.views.tool.utils.LogCatcher
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.system.measureTimeMillis

object CompleteHashmapUtils {

    /* ===============================
     * 版本1：HashMap<String, HashMap<String, CompletionName>>
     * 用于存储类信息映射
     * =============================== */

    @JvmStatic
    fun saveHashMapToFile(
        context: Context,
        hashMap: HashMap<String, HashMap<String, CompletionName>>,
        fileName: String
    ) {
        val dir = context.externalCacheDir
        if (dir == null) {
            LogCatcher.e("CompleteHashmapUtils", "externalCacheDir is null, cannot save $fileName")
            return
        }
        val file = File(dir, fileName)
        val time = measureTimeMillis {
            try {
                DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { dos ->
                    dos.writeInt(hashMap.size)
                    for ((outerKey, innerMap) in hashMap) {
                        dos.writeUTF(outerKey)
                        dos.writeInt(innerMap.size)
                        for ((innerKey, cn) in innerMap) {
                            dos.writeUTF(innerKey)
                            dos.writeUTF(cn.name)
                            dos.writeUTF(cn.type.name)
                            dos.writeUTF(cn.description)
                            dos.writeUTF(cn.generic)
                        }
                    }
                }
            } catch (e: IOException) {
                LogCatcher.e("CompleteHashmapUtils", "Failed to save $fileName", e)
            }
        }
        LogCatcher.i("CompleteHashmapUtils", "Saved $fileName, size=${hashMap.size}, path=${file.absolutePath}, took ${time}ms")
    }

    @JvmStatic
    fun loadHashMapFromFile(
        context: Context,
        fileName: String
    ): HashMap<String, HashMap<String, CompletionName>>? {
        val dir = context.externalCacheDir
        if (dir == null) {
            LogCatcher.e("CompleteHashmapUtils", "externalCacheDir is null, cannot load $fileName")
            return null
        }
        val file = File(dir, fileName)
        if (!file.exists()) {
            LogCatcher.i("CompleteHashmapUtils", "File $fileName does not exist at ${file.absolutePath}")
            return null
        }
        LogCatcher.i("CompleteHashmapUtils", "Loading $fileName from ${file.absolutePath}, size=${file.length()} bytes")
        val result = HashMap<String, HashMap<String, CompletionName>>()
        val time = measureTimeMillis {
            try {
                DataInputStream(BufferedInputStream(FileInputStream(file))).use { dis ->
                    val outerSize = dis.readInt()
                    for (i in 0 until outerSize) {
                        val outerKey = dis.readUTF()
                        val innerSize = dis.readInt()
                        val innerMap = HashMap<String, CompletionName>()
                        for (j in 0 until innerSize) {
                            val innerKey = dis.readUTF()
                            val name = dis.readUTF()
                            val type = CompletionItemKind.valueOf(dis.readUTF())
                            val desc = dis.readUTF()
                            val generic = dis.readUTF()
                            innerMap[innerKey] = CompletionName(name, type, desc, generic)
                        }
                        result[outerKey] = innerMap
                    }
                }
            } catch (e: IOException) {
                LogCatcher.e("CompleteHashmapUtils", "Failed to load $fileName", e)
                return null
            } catch (e: Exception) {
                LogCatcher.e("CompleteHashmapUtils", "Unexpected error loading $fileName", e)
                return null
            }
        }
        LogCatcher.i("CompleteHashmapUtils", "Loaded $fileName successfully, outerSize=${result.size}, took ${time}ms")
        return result
    }

    /* ===============================
     * 版本2：HashMap<String, List<String>>
     * 用于存储类名映射
     * =============================== */

    @JvmStatic
    fun saveHashMapToFile2(
        context: Context,
        hashMap: HashMap<String, List<String>>,
        fileName: String
    ) {
        val dir = context.externalCacheDir
        if (dir == null) {
            LogCatcher.e("CompleteHashmapUtils", "externalCacheDir is null, cannot save $fileName")
            return
        }
        val file = File(dir, fileName)
        val time = measureTimeMillis {
            try {
                DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { dos ->
                    dos.writeInt(hashMap.size)
                    for ((key, list) in hashMap) {
                        dos.writeUTF(key)
                        dos.writeInt(list.size)
                        for (s in list) {
                            dos.writeUTF(s)
                        }
                    }
                }
            } catch (e: IOException) {
                LogCatcher.e("CompleteHashmapUtils", "Failed to save $fileName", e)
            }
        }
        LogCatcher.i("CompleteHashmapUtils", "Saved $fileName, size=${hashMap.size}, path=${file.absolutePath}, took ${time}ms")
    }

    @JvmStatic
    fun loadHashMapFromFile2(
        context: Context,
        fileName: String
    ): HashMap<String, List<String>>? {
        val dir = context.externalCacheDir
        if (dir == null) {
            LogCatcher.e("CompleteHashmapUtils", "externalCacheDir is null, cannot load $fileName")
            return null
        }
        val file = File(dir, fileName)
        if (!file.exists()) {
            LogCatcher.i("CompleteHashmapUtils", "File $fileName does not exist at ${file.absolutePath}")
            return null
        }
        LogCatcher.i("CompleteHashmapUtils", "Loading $fileName from ${file.absolutePath}, size=${file.length()} bytes")
        val result = HashMap<String, List<String>>()
        val time = measureTimeMillis {
            try {
                DataInputStream(BufferedInputStream(FileInputStream(file))).use { dis ->
                    val size = dis.readInt()
                    for (i in 0 until size) {
                        val key = dis.readUTF()
                        val listSize = dis.readInt()
                        val list = ArrayList<String>(listSize)
                        for (j in 0 until listSize) {
                            list.add(dis.readUTF())
                        }
                        result[key] = list
                    }
                }
            } catch (e: IOException) {
                LogCatcher.e("CompleteHashmapUtils", "Failed to load $fileName", e)
                return null
            } catch (e: Exception) {
                LogCatcher.e("CompleteHashmapUtils", "Unexpected error loading $fileName", e)
                return null
            }
        }
        LogCatcher.i("CompleteHashmapUtils", "Loaded $fileName successfully, size=${result.size}, took ${time}ms")
        return result
    }
}