package com.luafabric.console.env

import dalvik.system.DexFile
import dalvik.system.PathClassLoader
import java.io.File

/** Java 类库：仅扫描项目根目录 libs/ 下的 .dex 文件（dex 内类名 + 反射方法签名）。 */
object DexLibraries {

    data class DexLib(val dexFile: String, val classes: List<ModuleTracker.JavaLib>)

    /** 扫项目 luaDir/libs 目录下的 dex 文件，返回每个 dex 的类列表（类名 → 方法签名）。 */
    fun scan(luaDir: String?): List<DexLib> {
        if (luaDir.isNullOrBlank()) return emptyList()
        val dir = File(luaDir, "libs")
        if (!dir.isDirectory) return emptyList()
        val dexFiles = dir.listFiles { f -> f.isFile && f.extension.equals("dex", true) }
            ?: return emptyList()
        return dexFiles.sortedBy { it.name }.mapNotNull { f -> scanDex(f) }
    }

    private fun scanDex(f: File): DexLib? {
        return try {
            val loader = PathClassLoader(f.absolutePath, DexLibraries::class.java.classLoader)
            val dex = DexFile(f)
            val classes = dex.entries()
                .toList()
                .filter { !it.startsWith("META-INF") }
                .sorted()
                .mapNotNull { name ->
                    try {
                        val c = dex.loadClass(name, loader) ?: return@mapNotNull null
                        val methods = c.declaredMethods
                            .filter { it.declaringClass == c }
                            .sortedBy { it.name }
                            .take(200)
                            .map { "${it.name}(${it.parameterTypes.joinToString(", ") { p -> p.simpleName }}) : ${it.returnType.simpleName}" }
                        ModuleTracker.JavaLib(c.name, methods)
                    } catch (e: Exception) {
                        null
                    }
                }
            dex.close()
            if (classes.isEmpty()) null else DexLib(f.name, classes)
        } catch (e: Exception) {
            null
        }
    }
}
