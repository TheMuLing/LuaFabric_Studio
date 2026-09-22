package muling.views.tool.utils

import android.content.Context
import android.net.Uri
import com.luafabric.studio.falling.ProjectItem
import com.luafabric.studio.falling.ui.project.TemplateItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.zip.ZipFile

object ProjectUtil {

    /**
     * 合法全局工具白名单（已删除/非法辅助库在读取时过滤并落盘）
     */
    private val VALID_GLOBAL_UTILS = setOf("UiUtil", "RecyclerAdapterUtil")

    /**
     * 项目类型判定：存在 build.gradle.b85 → Compose 项目（无 settings.json）
     */
    fun isComposeProject(projectDir: File): Boolean =
        File(projectDir, ComposeConfig.FILE_NAME).isFile

    /**
     * 统一读取项目配置：Compose 项目走 b85 解码，否则回退 settings.json。
     * 返回 JSONMap 语义的 Map（b85 字段名与 gen_conf.py 一致：name/packageId/versionCode/...），
     * 无效 b85 返回 null（调用方从而走兜底/重生语义）。
     */
    fun loadProjectConfig(projectDir: File): Map<String, Any?>? {
        return if (isComposeProject(projectDir)) {
            try {
                val b85File = File(projectDir, ComposeConfig.FILE_NAME)
                ComposeConfig.load(b85File.readText())
            } catch (e: Exception) {
                null
            }
        } else {
            val settingsFile = File(projectDir, "settings.json")
            if (settingsFile.isFile) {
                try {
                    JsonUtil.parseObject(settingsFile.readText())
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    /** 主页图标路径（相对项目根）：Compose 项目读 b85 icon 字段（净化越界/绝对路径） */
    fun projectIconPath(projectDir: File): String {
        val raw = loadProjectConfigInfo(projectDir, "icon")
            ?: loadProjectConfigInfo(projectDir, "iconPath")
            ?: "icon.png"
        return if (raw.isEmpty() || raw.startsWith("/") || raw.contains("..")) "icon.png" else raw
    }

    /** 配置文件里取单个字段（b85 或 settings.json），取不到返回 null */
    private fun loadProjectConfigInfo(projectDir: File, key: String): String? {
        val cfg = loadProjectConfig(projectDir) ?: return null
        val v = when (key) {
            "icon" -> cfg["icon"]
            "iconPath" -> cfg["iconPath"]
            else -> cfg[key]
        }
        return v as? String
    }

    /**
     * 从目录加载项目
     */
    suspend fun loadProjectsFromDirectory(
        projectsPath: String,
        onProjectItemsChanged: (List<ProjectItem>) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            if (projectsPath.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onProjectItemsChanged(emptyList())
                }
                return@withContext
            }

            val projectsDir = File(projectsPath)

            if (!projectsDir.exists() || !projectsDir.isDirectory) {
                // 尝试创建目录
                projectsDir.mkdirs()
                withContext(Dispatchers.Main) {
                    onProjectItemsChanged(emptyList())
                }
                return@withContext
            }

            val projectList = mutableListOf<ProjectItem>()

            projectsDir.listFiles()?.forEach { projectDir ->
                if (projectDir.isDirectory) {
                    val projectItem = ProjectItem(
                        id = projectDir.name,
                        name = projectDir.name,
                        path = projectDir.absolutePath,
                        createdDate = Date(projectDir.lastModified()),
                        modifiedDate = Date(projectDir.lastModified())
                    )
                    projectList.add(projectItem)
                }
            }

            // 按修改时间排序，最新的在前面
            projectList.sortByDescending { it.modifiedDate }

            withContext(Dispatchers.Main) {
                onProjectItemsChanged(projectList)
            }
        }
    }

    /**
     * 生成默认项目名称
     */
    fun generateDefaultProjectName(projectsDir: File): String {
        val baseName = "My Application"
        var counter = 1

        while (true) {
            val projectName = "$baseName$counter"
            val projectDir = File(projectsDir, projectName)
            if (!projectDir.exists()) {
                return projectName
            }
            counter++
        }
    }

    /**
     * 生成包名（汉译英转小写）
     */
    fun generatePackageName(projectName: String): String {
        // 简单实现：将非字母数字字符替换为点，转为小写
        val cleanedName = projectName
            .replace("[^a-zA-Z0-9]".toRegex(), " ")
            .trim()
            .replace("\\s+".toRegex(), ".")
            .lowercase()

        return if (cleanedName.isNotEmpty()) {
            "myluaapp.$cleanedName"
        } else {
            "myluaapp.myapplication"
        }
    }

    /**
     * 验证包名格式
     */
    fun isValidPackageName(packageName: String): Boolean {
        return packageName.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$".toRegex())
    }

    /**
     * 模板 zip 文件名 → 显示名 映射
     */
    private val templateDisplayNames = mapOf(
        "Default.zip" to "空项目",
        "Floating Window.zip" to "悬浮窗"
    )

    /**
     * 加载模板列表
     */
    suspend fun loadTemplates(
        context: Context,
        onLoaded: (List<TemplateItem>) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val templates = mutableListOf<TemplateItem>()

                // 列出 assets/templates 目录下的 zip 文件
                val assetManager = context.assets
                val templateFiles = assetManager.list("templates") ?: emptyArray()

                templateFiles.forEach { fileName ->
                    if (fileName.endsWith(".zip")) {
                        val templateName = templateDisplayNames[fileName] ?: fileName
                            .removeSuffix(".zip")
                            .replace("_", " ")
                            .replace("-", " ")
                            .split(" ")
                            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

                        // 尝试提取预览图片
                        val previewUri = extractPreviewImage(context, fileName)

                        templates.add(
                            TemplateItem(
                                name = templateName,
                                zipFileName = fileName,
                                previewUri = previewUri
                            )
                        )
                    }
                }

                // 按名称排序，但把Default.zip放在第一个
                templates.sortWith(
                    compareBy(
                        { !it.zipFileName.equals("Default.zip", ignoreCase = true) },
                        { it.name }
                    ))

                withContext(Dispatchers.Main) {
                    onLoaded(templates)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onLoaded(emptyList())
                }
            }
        }
    }

    /**
     * 提取模板预览图片
     */
    private fun extractPreviewImage(
        context: Context,
        zipFileName: String
    ): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "template_previews")
            cacheDir.mkdirs()

            val previewFile = File(cacheDir, "$zipFileName.preview.png")

            // 如果缓存文件已存在，直接返回
            if (previewFile.exists()) {
                return Uri.fromFile(previewFile)
            }

            // 从 assets 读取 zip 文件
            val assetManager = context.assets
            assetManager.open("templates/$zipFileName").use { inputStream ->
                // 将 zip 文件复制到临时文件
                val tempZipFile = File(cacheDir, "$zipFileName.temp")
                FileOutputStream(tempZipFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                // 从 zip 文件中提取 Preview.png
                ZipFile(tempZipFile).use { zipFile ->
                    val previewEntry = zipFile.entries().toList().find {
                        it.name.equals("Preview.png", ignoreCase = true)
                    }

                    previewEntry?.let { entry ->
                        zipFile.getInputStream(entry).use { zipInputStream ->
                            FileOutputStream(previewFile).use { fileOutputStream ->
                                zipInputStream.copyTo(fileOutputStream)
                            }
                        }

                        // 清理临时文件
                        tempZipFile.delete()

                        return Uri.fromFile(previewFile)
                    }
                }

                // 清理临时文件
                tempZipFile.delete()
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从缓存加载模板预览
     */
    fun loadTemplatePreview(
        context: Context,
        template: TemplateItem
    ): Any? {
        val cacheDir = File(context.cacheDir, "template_previews")
        val previewFile = File(cacheDir, "${template.zipFileName}.preview.png")

        return if (previewFile.exists()) {
            Uri.fromFile(previewFile)
        } else {
            null
        }
    }

    /**
     * 解压模板文件
     */
    fun extractTemplate(
        context: Context,
        template: TemplateItem,
        projectDir: File,
        projectName: String,
        packageName: String,
        debugMode: Boolean
    ) {
        val cacheDir = File(context.cacheDir, "template_extract")
        cacheDir.mkdirs()

        // 从 assets 读取 zip 文件
        val assetManager = context.assets
        assetManager.open("templates/${template.zipFileName}").use { inputStream ->
            // 将 zip 文件复制到临时文件
            val tempZipFile = File(cacheDir, "${template.zipFileName}.temp")
            FileOutputStream(tempZipFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            // 解压 zip 文件
            ZipFile(tempZipFile).use { zipFile ->
                for (entry in zipFile.entries()) {
                    // 跳过 Preview.png 文件
                    if (entry.name.equals("Preview.png", ignoreCase = true)) {
                        continue
                    }

                    val entryFile = File(projectDir, entry.name)

                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        // 确保父目录存在
                        entryFile.parentFile?.mkdirs()

                        // 写入文件
                        zipFile.getInputStream(entry).use { zipInputStream ->
                            FileOutputStream(entryFile).use { fileOutputStream ->
                                zipInputStream.copyTo(fileOutputStream)
                            }
                        }

                        // 如果是 settings.json 或 main.lua，需要替换内容
                        when {
                            entry.name.endsWith("settings.json") -> {
                                // 这里不处理globalUtils，由saveSettingsFile处理
                                updateSettingsFile(entryFile, projectName, packageName, debugMode)
                            }

                            entry.name.endsWith("main.lua") -> {
                                updateMainLuaFile(entryFile, projectName)
                            }
                        }
                    }
                }
            }

            // 清理临时文件
            tempZipFile.delete()
        }

        // 清理缓存目录
        cacheDir.deleteRecursively()
    }

    /**
     * 更新 main.lua 文件
     */
    fun updateMainLuaFile(mainLuaFile: File, projectName: String) {
        try {
            val content = mainLuaFile.readText()
            val updatedContent = content.replace("AppName", projectName)
            mainLuaFile.writeText(updatedContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 更新 settings.json 文件
     */
    fun updateSettingsFile(
        settingsFile: File,
        projectName: String,
        packageName: String,
        debugMode: Boolean
    ) {
        try {
            val jsonString = settingsFile.readText()
            val jsonMap = JsonUtil.parseObject(jsonString) as MutableMap<String, Any?>

            // 更新包名
            jsonMap["package"] = packageName

            // 更新应用信息
            val applicationMap =
                (jsonMap["application"] as? MutableMap<String, Any>) ?: mutableMapOf()
            applicationMap["label"] = projectName
            applicationMap["debugmode"] = debugMode
            jsonMap["application"] = applicationMap

            // 写回文件 - 使用格式化输出（缩进4个空格）
            val updatedJson = JSONObject(jsonMap).toString(4)
            settingsFile.writeText(updatedJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 更新 settings.json 文件（带全局Utils与模板信息）
     */
    fun updateSettingsFile(
        settingsFile: File,
        projectName: String,
        packageName: String,
        debugMode: Boolean,
        globalUtils: List<String>,
        template: String? = null
    ) {
        try {
            val jsonString = settingsFile.readText()
            val jsonMap = JsonUtil.parseObject(jsonString) as MutableMap<String, Any?>

            // 更新包名
            jsonMap["package"] = packageName

            // 更新应用信息
            val applicationMap =
                (jsonMap["application"] as? MutableMap<String, Any>) ?: mutableMapOf()
            applicationMap["label"] = projectName
            applicationMap["debugmode"] = debugMode
            jsonMap["application"] = applicationMap

            // 更新全局Utils
            if (globalUtils.isNotEmpty()) {
                jsonMap["global_utils"] = globalUtils
            } else {
                // 如果没有选择任何全局Utils，则设置为空数组
                jsonMap["global_utils"] = emptyList<String>()
            }

            // 记录创建时使用的模板（zip文件名）
            template?.let { jsonMap["template"] = it }

            // 写回文件 - 使用格式化输出（缩进4个空格）
            val updatedJson = JSONObject(jsonMap).toString(4)
            settingsFile.writeText(updatedJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 保存 settings.json 文件（基础版本）
     */
    fun saveSettingsFile(
        projectDir: File,
        projectName: String,
        packageName: String,
        debugMode: Boolean
    ) {
        saveSettingsFile(projectDir, projectName, packageName, debugMode, emptyList())
    }

    /**
     * 保存 settings.json 文件（带全局Utils与模板信息）
     */
    fun saveSettingsFile(
        projectDir: File,
        projectName: String,
        packageName: String,
        debugMode: Boolean,
        globalUtils: List<String>,
        template: String? = null
    ) {
        val settingsFile = File(projectDir, "settings.json")
        // 如果已经存在（从模板复制），则更新它
        if (settingsFile.exists()) {
            updateSettingsFile(
                settingsFile,
                projectName,
                packageName,
                debugMode,
                globalUtils,
                template
            )
            return
        }

        // 创建默认的 settings.json - 使用明确的类型转换
        val settings = mutableMapOf<String, Any?>(
            "versionName" to "1.0",
            "versionCode" to "1",
            "uses_sdk" to mapOf(
                "minSdkVersion" to "29",
                "targetSdkVersion" to "29"
            ),
            "package" to packageName,
            "application" to mapOf(
                "label" to projectName,
                "debugmode" to debugMode
            ),
            "user_permission" to listOf(
                "WRITE_EXTERNAL_STORAGE",
                "READ_EXTERNAL_STORAGE",
                "INTERNET"
            ),
            "implementation" to emptyList<String>(),
            "global_utils" to globalUtils  // 使用传入的globalUtils
        )

        // 记录创建时使用的模板（zip文件名）
        template?.let { settings["template"] = it }

        try {
            // 使用格式化输出（缩进4个空格）
            val jsonString = JSONObject(settings as Map<*, *>).toString(4)
            settingsFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Compose 项目：把创建表单参数编码为 build.gradle.b85（创建时 encode 落盘）。
     * 结构/标志位与 gen_conf.py CONFIG 对齐（schema v1，header flags bit0 = debugmode）。
     */
    fun saveComposeConfigFile(
        projectDir: File,
        projectName: String,
        packageName: String,
        debugMode: Boolean
    ) {
        val config = linkedMapOf<String, Any?>(
            "name" to projectName,
            "packageId" to packageName,
            "versionCode" to 1L,
            "versionName" to "1.0.0",
            "minSdk" to 29L,
            "targetSdk" to 36L,
            "uiMode" to "compose",
            "entry" to "main.lua",
            // 与 copyIconToProject 目标一致（项目根 icon.png）；无严格绝对/越界语义（创建时 encode 受表单约束）
            "icon" to "icon.png",
            "theme" to linkedMapOf(
                "dark" to false,
                "dynamic" to true,
                "seed" to 0x3A6CC8L
            ),
            "deps" to listOf("coil", "material3"),
            "global_utils" to emptyList<Any>()
        )
        val raw = ComposeConfig.pack(config, flags = if (debugMode) ComposeConfig.FLAG_DEBUG else 0)
        val b85 = ComposeConfig.b85Encode(raw)
        File(projectDir, ComposeConfig.FILE_NAME).writeText(b85 + "\n")
    }

    /**
     * Compose 项目：局部更新 b85 配置并落盘（属性页保存用）。
     * 保留文件中其余字段（theme/deps 等），仅覆盖 [updates]；debugmode 写回 header flags bit0。
     * 文件无效/不存在时以默认空档重建，避免属性页保存把脏 b85 覆盖成残缺配置。
     */
    fun updateComposeConfigFile(projectDir: File, updates: Map<String, Any?>, debugMode: Boolean) {
        val existing = loadProjectConfig(projectDir) ?: linkedMapOf()
        val config = linkedMapOf<String, Any?>()
        config.putAll(existing)
        config.putAll(updates)
        val raw = ComposeConfig.pack(config, flags = if (debugMode) ComposeConfig.FLAG_DEBUG else 0)
        File(projectDir, ComposeConfig.FILE_NAME).writeText(ComposeConfig.b85Encode(raw) + "\n")
    }

    /**
     * 创建默认的 main.lua 文件（备用）
     */
    fun createDefaultMainLuaFile(
        projectDir: File,
        projectName: String
    ) {
        val mainLuaFile = File(projectDir, "main.lua")

        // 如果已经存在（从模板复制），则跳过
        if (mainLuaFile.exists()) {
            return
        }

        val content = """
            require "import"
            import "android.app.*"
            import "android.os.*"
            import "android.widget.*"
            import "android.view.*"
            import "androidx.appcompat.widget.LinearLayoutCompat"

            activity
            .setTheme(R.style.Theme_Material3_Blue)
            .setTitle("$projectName")
            .setContentView(loadlayout("layout"))
        """.trimIndent()

        try {
            mainLuaFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 复制图标到项目
     */
    fun copyIconToProject(
        context: Context,
        sourceFile: File,
        projectDir: File
    ) {
        try {
            if (!sourceFile.exists() || !sourceFile.isFile) return
            val iconFile = File(projectDir, "icon.png")

            sourceFile.inputStream().use { inputStream ->
                FileOutputStream(iconFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取项目信息
     */
    suspend fun getProjectInfo(projectPath: String): Map<String, Any?> {
        return withContext(Dispatchers.IO) {
            val projectDir = File(projectPath)
            val info = mutableMapOf<String, Any?>()

            if (projectDir.exists() && projectDir.isDirectory) {
                // 基本信息
                info["name"] = projectDir.name
                info["path"] = projectDir.absolutePath
                info["lastModified"] = Date(projectDir.lastModified())

                // 检查settings.json文件
                val settingsFile = File(projectDir, "settings.json")
                if (settingsFile.exists() && settingsFile.isFile) {
                    try {
                        val jsonString = settingsFile.readText()
                        val jsonMap = JsonUtil.parseObject(jsonString)
                        info["settings"] = jsonMap

                        // 获取全局Utils信息，过滤非法项（已删除的旧辅助库）并落盘
                        val globalUtils = (jsonMap["global_utils"] as? List<*>) ?: emptyList<Any>()
                        val cleanedUtils =
                            globalUtils.filterIsInstance<String>().filter { it in VALID_GLOBAL_UTILS }
                        if (cleanedUtils.size != globalUtils.size) {
                            try {
                                val writeMap =
                                    JsonUtil.parseObject(jsonString) as MutableMap<String, Any?>
                                writeMap["global_utils"] = cleanedUtils
                                settingsFile.writeText(JSONObject(writeMap).toString(4))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        info["global_utils"] = cleanedUtils
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if (isComposeProject(projectDir)) {
                    // Compose 项目：配置在 build.gradle.b85。b85 项目不挂旧全局辅助库，
                    // global_utils 恒为空数组（白名单对 b85 不适用——配置即生成物，无净化语义）
                    val cfg = loadProjectConfig(projectDir)
                    if (cfg != null) {
                        info["settings"] = cfg
                        info["global_utils"] = emptyList<String>()
                    }
                }

                // 检查是否有icon.png
                val iconFile = File(projectDir, "icon.png")
                info["hasIcon"] = iconFile.exists() && iconFile.isFile

                // 统计文件数量
                val fileCount = projectDir.walk().filter { it.isFile }.count()
                info["fileCount"] = fileCount
            }

            info
        }
    }
}