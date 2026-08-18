package muling.views.tool.utils

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

object FileUtil {

    /**
     * 获取项目存储路径
     */
    fun getProjectsPath(context: Context): String {
        val settings = com.luafabric.studio.falling.ui.settings.SettingsManager.currentSettings
        return settings.projectStoragePath.ifBlank {
            val externalDir = context.getExternalFilesDir(null)
            externalDir?.let { File(it, "projects").absolutePath } ?: ""
        }
    }

    /**
     * 获取项目目录
     */
    fun getProjectsDirectory(context: Context): File {
        val settings = com.luafabric.studio.falling.ui.settings.SettingsManager.currentSettings
        return if (settings.projectStoragePath.isNotBlank()) {
            File(settings.projectStoragePath)
        } else {
            val externalDir = context.getExternalFilesDir(null)
            File(externalDir ?: context.filesDir, "projects").apply {
                mkdirs()
            }
        }
    }

    /**
     * 检查文件是否存在
     */
    fun fileExists(filePath: String): Boolean {
        return File(filePath).exists()
    }

    /**
     * 创建目录（如果不存在）
     */
    fun createDirectoryIfNotExists(directoryPath: String): Boolean {
        val dir = File(directoryPath)
        return if (!dir.exists()) {
            dir.mkdirs()
        } else {
            true
        }
    }

    /**
     * 删除文件或目录
     */
    fun deleteFileOrDirectory(path: String): Boolean {
        val file = File(path)
        return if (file.exists()) {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } else {
            false
        }
    }

    /**
     * 获取文件大小（字节）
     */
    fun getFileSize(filePath: String): Long {
        val file = File(filePath)
        return if (file.exists()) {
            if (file.isDirectory) {
                file.walk().filter { it.isFile }.sumOf { it.length() }
            } else {
                file.length()
            }
        } else {
            0L
        }
    }

    /**
     * 获取文件扩展名
     */
    fun getFileExtension(fileName: String): String {
        return if (fileName.contains(".")) {
            fileName.substringAfterLast(".", "")
        } else {
            ""
        }
    }

    /**
     * 获取不带扩展名的文件名
     */
    fun getFileNameWithoutExtension(fileName: String): String {
        return if (fileName.contains(".")) {
            fileName.substringBeforeLast(".")
        } else {
            fileName
        }
    }

    // ========== ZIP 操作函数 ==========

    /**
     * 读取 ZIP 文件中指定条目（文件）的内容为字符串
     * @param zipFile ZIP 文件
     * @param entryName 条目名称（例如 "settings.json"）
     * @return 文件内容，如果条目不存在或读取失败则返回 null
     */
    fun readFileFromZip(zipFile: File, entryName: String): String? {
        return try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry(entryName) ?: return null
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 解压 ZIP 文件到目标目录
     * @param zipFile ZIP 文件
     * @param destinationDir 目标目录（如果不存在会自动创建）
     * @return true 表示成功，false 表示失败
     */
    fun extractZip(zipFile: File, destinationDir: File): Boolean {
        return try {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val targetFile = File(destinationDir, entry.name)
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}