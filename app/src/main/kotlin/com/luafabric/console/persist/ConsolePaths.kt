package com.luafabric.console.persist

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * 持久化根：android/data/<包名> 私有目录（getExternalFilesDir）。
 * 项目级日志按「项目文件夹路径哈希」归集到 projects/<hash>/ 下
 * （sessions / logcat 随项目隔离，避免同名/同包名项目反复删除重建导致的混入与覆盖）；
 * outputs（手动导出）与 crash（应用级崩溃）仍为全局目录。
 */
object ConsolePaths {

    @Volatile
    private var root: File? = null

    fun init(context: Context) {
        if (root == null) {
            synchronized(this) {
                if (root == null) {
                    val dir = File(context.getExternalFilesDir(null), "console")
                    dir.mkdirs()
                    root = dir
                }
            }
        }
    }

    fun root(): File = requireNotNull(root) { "ConsolePaths.init(context) must be called first" }

    private fun dir(name: String): File {
        val d = File(root(), name)
        d.mkdirs()
        return d
    }

    fun outputs(): File = dir("outputs")
    fun crash(): File = dir("crash")

    /** 项目唯一目录：console/projects/<sha256(项目文件夹路径) 前 12 位>。 */
    fun projectDir(projectDirPath: String?): File {
        val d = File(dir("projects"), projectHash(projectDirPath))
        d.mkdirs()
        return d
    }

    /** 项目会话归档目录：projects/<hash>/sessions/<毫秒时间戳>/outputs.txt。 */
    fun projectSessions(projectDirPath: String?): File {
        val d = File(projectDir(projectDirPath), "sessions")
        d.mkdirs()
        return d
    }

    /** 项目 logcat 目录：projects/<hash>/logcat/logcat_<项目名>_<时间戳>.log。 */
    fun projectLogcat(projectDirPath: String?): File {
        val d = File(projectDir(projectDirPath), "logcat")
        d.mkdirs()
        return d
    }

    /** 项目文件夹路径 → 稳定哈希唯一标识（SHA-256 前 12 位十六进制）。 */
    private fun projectHash(projectDirPath: String?): String {
        val path = projectDirPath?.trim()?.trimEnd('/', '\\').orEmpty()
        if (path.isEmpty()) return "unknown"
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(path.toByteArray(Charsets.UTF_8))
            digest.take(6).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            path.replace(Regex("[^A-Za-z0-9]"), "").takeLast(12).ifBlank { "unknown" }
        }
    }
}
