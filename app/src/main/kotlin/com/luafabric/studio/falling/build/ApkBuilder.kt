package com.luafabric.studio.falling.build

import android.content.Context
import com.luafabric.studio.falling.build.maven.*
import muling.views.tool.utils.ConsoleUtil
import muling.views.tool.utils.LogCatcher
import com.luajava.LuaState
import com.luajava.LuaStateFactory
import java.io.*
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.regex.Pattern
import java.util.zip.*
// ECJ 和 D8 依赖
import org.eclipse.jdt.internal.compiler.batch.Main as EcjMain
import com.android.tools.r8.D8
import kotlinx.coroutines.runBlocking

class ApkBuilder {

    companion object {
        // 正则表达式模式数组
        private val PATTERNS = arrayOf(
            "require\\s+\"([a-zA-Z0-9_.]+)\"",  // require "aaa"
            "require\"([a-zA-Z0-9_.]+)\"",       // require"aaa"
            "require\\(\"([a-zA-Z0-9_.]+)\"\\)",  // require("aaa")
            "require\\(\"([a-zA-Z0-9_.]+)\"\\s*,\\s*[^)]+\\)",  // require("aaa", data)
            "require\\s+\'([a-zA-Z0-9_.]+)\'",  // require 'aaa'
            "require\'([a-zA-Z0-9_.]+)\'",       // require'aaa'
            "require\\(\'([a-zA-Z0-9_.]+)\'\\)",  // require('aaa')
            "require\\(\'([a-zA-Z0-9_.]+)\'\\s*,\\s*[^)]+\\)",  // require('aaa', data)

            // import 语句
            "import\\s+\"([a-zA-Z0-9_.]+)\"",  // import "aaa"
            "import\"([a-zA-Z0-9_.]+)\"",       // import"aaa"
            "import\\(\"([a-zA-Z0-9_.]+)\"\\)",  // import("aaa")
            "import\\(\"([a-zA-Z0-9_.]+)\"\\s*,\\s*[^)]+\\)",  // import("aaa", data)
            "import\\s+\'([a-zA-Z0-9_.]+)\'",  // import 'aaa'
            "import\'([a-zA-Z0-9_.]+)\'",       // import'aaa'
            "import\\(\'([a-zA-Z0-9_.]+)\'\\)",  // import('aaa')
            "import\\(\'([a-zA-Z0-9_.]+)\'\\s*,\\s*[^)]+\\)"  // import('aaa', data)
        )

        // LuaState 单例实例
        private var sharedLuaState: LuaState? = null

        // 获取共享的 LuaState 实例
        private fun getSharedLuaState(): LuaState {
            synchronized(this) {
                if (sharedLuaState == null || sharedLuaState!!.isClosed) {
                    sharedLuaState = LuaStateFactory.newLuaState()
                    sharedLuaState!!.openLibs()
                    LogCatcher.i("ApkBuilder", "创建共享 LuaState 实例")
                }
                return sharedLuaState!!
            }
        }

        // 清理共享 LuaState
        private fun cleanupSharedLuaState() {
            synchronized(this) {
                sharedLuaState?.let { state ->
                    if (!state.isClosed) {
                        try {
                            state.gc(LuaState.LUA_GCCOLLECT, 1)
                            state.top = 0
                            LogCatcher.i("ApkBuilder", "清理共享 LuaState")
                        } catch (e: Exception) {
                            LogCatcher.e("ApkBuilder", "清理共享 LuaState 失败", e)
                        }
                    }
                }
            }
        }

        // 核心构建方法
        fun bin(
            context: Context,
            mRootDir: String,
            projectPath: String,
            appName: String,
            packageName: String,
            versionName: String,
            versionCode: String,
            iconPath: String?,
            permissions: Array<String>?,
            isDebug: Boolean,
            outputPath: String,
            minSdkVersion: Int,
            targetSdkVersion: Int,
            mavenDependencies: List<String> = emptyList()
        ): String {
            LogCatcher.i("ApkBuilder", "开始构建APK")
            LogCatcher.i("ApkBuilder", "项目路径: $projectPath")
            LogCatcher.i("ApkBuilder", "输出路径: $outputPath")
            LogCatcher.i("ApkBuilder", "包名: $packageName")
            LogCatcher.i("ApkBuilder", "应用名称: $appName")
            LogCatcher.i("ApkBuilder", "版本号: $versionCode")
            LogCatcher.i("ApkBuilder", "版本名称: $versionName")
            LogCatcher.i("ApkBuilder", "minSdkVersion: $minSdkVersion")
            LogCatcher.i("ApkBuilder", "targetSdkVersion: $targetSdkVersion")
            LogCatcher.i("ApkBuilder", "调试模式: $isDebug")
            LogCatcher.i("ApkBuilder", "权限数量: ${permissions?.size ?: 0}")
            LogCatcher.i("ApkBuilder", "Maven依赖数量: ${mavenDependencies.size}")

            var unsignedApkPath: String? = null

            try {
                // 1. 确保输出目录存在
                val outputFile = File(outputPath)
                val outputDir = outputFile.parentFile
                outputDir?.takeIf { !it.exists() }?.mkdirs()

                // 2. 获取核心APK路径（从assets复制到缓存）
                val coreApkPath = extractCoreApkToCache(context)
                if (coreApkPath == null) {
                    return "error: 无法提取核心APK文件"
                }

                // 对权限进行预处理，自动补全系统权限前缀
                val processedPermissions = permissions?.map { perm ->
                    if (!perm.contains('.')) {
                        "android.permission.$perm"
                    } else {
                        perm
                    }
                }?.toTypedArray()

                // 3. 修改AndroidManifest.xml
                val tempApkPath = modifyAndroidManifest(
                    context,
                    coreApkPath,
                    projectPath,
                    appName,
                    packageName,
                    versionName,
                    versionCode,
                    processedPermissions,
                    minSdkVersion,
                    targetSdkVersion,
                    isDebug
                )

                if (tempApkPath == null) {
                    return "error: 修改AndroidManifest.xml失败"
                }

                // 4. 下载 Maven JAR 依赖
                val mavenJars = processMavenJarDependencies(context, mavenDependencies)

                // 5. 生成未签名APK的临时路径
                val unsignedApkFile = File(outputDir, "unsigned_${System.currentTimeMillis()}.apk")
                unsignedApkPath = unsignedApkFile.absolutePath

                // 6. 复制项目文件到临时APK（生成未签名APK）
                unsignedApkPath = addProjectFilesToApk(
                    context,
                    tempApkPath,
                    projectPath,
                    unsignedApkPath,
                    mavenJars
                )

                if (unsignedApkPath == null) {
                    return "error: 添加项目文件到APK失败"
                }

                // 7. 清理临时文件
                File(tempApkPath).delete()
                File(coreApkPath).delete()

                // 8. 对未签名APK进行签名
                val signedApkPath = signApkWithDefaultKey(context, unsignedApkPath, outputPath)

                // 9. 清理临时文件
                File(unsignedApkPath).delete()

                if (signedApkPath == null) {
                    return "error: APK签名失败"
                }

                LogCatcher.i("ApkBuilder", "APK构建并签名成功: $signedApkPath")
                return signedApkPath

            } catch (e: Exception) {
                LogCatcher.e("ApkBuilder", "构建APK失败", e)

                // 清理临时文件
                unsignedApkPath?.let { File(it).delete() }

                return "error: 构建失败: ${e.message}"
            } finally {
                // 构建完成后清理共享资源
                cleanupSharedLuaState()
                System.gc()
            }
        }

        /**
         * 处理 Maven JAR 依赖
         * @return 下载的 JAR 文件列表
         */
        private fun processMavenJarDependencies(
            context: Context,
            mavenDependencies: List<String>
        ): List<File> {
            if (mavenDependencies.isEmpty()) {
                return emptyList()
            }
            
            LogCatcher.i("ApkBuilder", "开始处理 Maven JAR 依赖，共 ${mavenDependencies.size} 个")
            
            // 解析依赖字符串
            val parsedDependencies = mavenDependencies.mapNotNull { MavenDependency.parse(it) }
            if (parsedDependencies.isEmpty()) {
                LogCatcher.w("ApkBuilder", "没有有效的 Maven 依赖")
                return emptyList()
            }
            
            val downloadedJars = mutableListOf<File>()
            val downloader = MavenDownloader(context)
            val repositories = MavenRepository.getDefaultRepositories()
            
            for ((index, dep) in parsedDependencies.withIndex()) {
                LogCatcher.i("ApkBuilder", "处理依赖 [${index + 1}/${parsedDependencies.size}]: ${dep.getCoordinates()}")
                
                // 同步下载
                val result = runBlocking {
                    downloader.downloadDependency(dep, repositories, null)
                }
                
                when (result) {
                    is MavenDownloader.DownloadResult.Success -> {
                        // 查找 JAR 文件
                        val jarFile = result.files.find { it.extension == "jar" }
                        if (jarFile != null && jarFile.exists()) {
                            downloadedJars.add(jarFile)
                            LogCatcher.i("ApkBuilder", "  - 下载成功: ${jarFile.name} (${jarFile.length() / 1024} KB)")
                        } else {
                            LogCatcher.w("ApkBuilder", "  - 未找到 JAR 文件，可能不是 JAR 类型")
                        }
                    }
                    is MavenDownloader.DownloadResult.Failure -> {
                        LogCatcher.w("ApkBuilder", "  - 下载失败: ${result.reason}")
                    }
                }
            }
            
            LogCatcher.i("ApkBuilder", "Maven JAR 依赖处理完成，成功下载 ${downloadedJars.size} 个 JAR")
            return downloadedJars
        }

        /**
         * 将 Maven JAR 依赖添加到 DEX 编译流程
         */
        private fun addMavenJarsToDexCompilation(
            context: Context,
            jarFiles: List<File>,
            workDir: File,
            androidJarPath: String?
        ) {
            if (jarFiles.isEmpty()) return
            
            LogCatcher.i("ApkBuilder", "将 ${jarFiles.size} 个 JAR 添加到 DEX 编译")
            
            try {
                val dexOutputDir = File(workDir, "maven_dex_output")
                dexOutputDir.mkdirs()
                
                val d8Args = mutableListOf<String>()
                d8Args.add("--lib")
                d8Args.add(androidJarPath ?: extractAndroidJarToCache(context)!!)
                d8Args.add("--output")
                d8Args.add(dexOutputDir.absolutePath)
                
                // 添加所有 JAR 文件
                jarFiles.forEach { jar ->
                    d8Args.add(jar.absolutePath)
                }
                
                d8Args.add("--min-api")
                d8Args.add("21")
                
                LogCatcher.i("ApkBuilder", "执行 D8 转换 Maven JAR")
                D8.main(d8Args.toTypedArray())
                
                // 将生成的 DEX 复制到 APK 工作目录
                addDexFilesToApkWorkDir(dexOutputDir, workDir)
                
                // 清理临时目录
                dexOutputDir.deleteRecursively()
                
                LogCatcher.i("ApkBuilder", "Maven JAR 转 DEX 完成")
                
            } catch (e: Exception) {
                LogCatcher.e("ApkBuilder", "Maven JAR 转 DEX 失败", e)
            }
        }

        /**
         * 构建包含 Maven JAR 的 classpath 字符串
         */
        private fun buildClasspathWithMavenJars(
            androidJarPath: String,
            mavenJars: List<File>
        ): String {
            val classpathParts = mutableListOf(androidJarPath)
            classpathParts.addAll(mavenJars.map { it.absolutePath })
            return classpathParts.joinToString(File.pathSeparator)
        }

        // 使用默认签名密钥对APK进行签名
        private fun signApkWithDefaultKey(
            context: Context,
            unsignedApkPath: String,
            outputPath: String
        ): String? {
            LogCatcher.i("ApkBuilder", "开始对APK进行签名")

            try {
                // 1. 从assets提取签名文件到缓存
                val jksPath = extractJksToCache(context)
                if (jksPath == null) {
                    LogCatcher.e("ApkBuilder", "无法提取签名文件")
                    return null
                }

                LogCatcher.i("ApkBuilder", "签名文件路径: $jksPath")
                LogCatcher.i("ApkBuilder", "输入APK路径: $unsignedApkPath")
                LogCatcher.i("ApkBuilder", "输出APK路径: $outputPath")

                // 2. 验证输入文件是否存在
                val inputApkFile = File(unsignedApkPath)
                if (!inputApkFile.exists()) {
                    LogCatcher.e("ApkBuilder", "输入APK文件不存在: $unsignedApkPath")
                    return null
                }

                LogCatcher.i("ApkBuilder", "输入APK文件大小: ${inputApkFile.length()} 字节")

                // 3. 调用签名方法
                val success = signerApk(
                    context,                     // context
                    jksPath,                     // keyPath
                    "LuaFabric-Studio",          // pass
                    "LuaFabric-Studio",          // alias
                    "LuaFabric-Studio",          // keyPass
                    unsignedApkPath,            // inPath
                    outputPath                  // outPath
                )

                // 4. 清理临时签名文件
                File(jksPath).delete()

                if (success) {
                    val signedApkFile = File(outputPath)
                    if (signedApkFile.exists()) {
                        LogCatcher.i(
                            "ApkBuilder",
                            "APK签名成功: $outputPath, 大小: ${signedApkFile.length()} 字节"
                        )
                        return outputPath
                    } else {
                        LogCatcher.e("ApkBuilder", "签名成功但输出文件不存在: $outputPath")
                        return null
                    }
                } else {
                    LogCatcher.e("ApkBuilder", "APK签名失败")
                    return null
                }

            } catch (e: Exception) {
                LogCatcher.e("ApkBuilder", "签名过程异常", e)
                e.printStackTrace()
                return null
            }
        }

        // 从assets提取JKS签名文件到缓存
        private fun extractJksToCache(context: Context): String? {
            LogCatcher.i("ApkBuilder", "开始提取签名文件")

            try {
                // 创建缓存目录
                val cacheDir = File(context.cacheDir, "apk_sign")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }

                // 目标文件路径
                val jksFile = File(cacheDir, "signature.jks")

                // 从assets复制文件
                val assetManager = context.assets
                assetManager.open("signature.jks").use { input ->
                    FileOutputStream(jksFile).use { output ->
                        val buffer = ByteArray(8192)
                        var length: Int
                        while (input.read(buffer).also { length = it } > 0) {
                            output.write(buffer, 0, length)
                        }
                        output.flush()
                    }
                }

                LogCatcher.i(
                    "ApkBuilder",
                    "签名文件已提取到: ${jksFile.absolutePath}, 大小: ${jksFile.length()} 字节"
                )
                return jksFile.absolutePath

            } catch (e: IOException) {
                LogCatcher.e("ApkBuilder", "提取签名文件失败", e)
                return null
            }
        }

        // 从assets提取核心APK到缓存
        private fun extractCoreApkToCache(context: Context): String? {
            try {
                // 创建缓存目录
                val cacheDir = File(context.cacheDir, "apk_build")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }

                // 目标文件路径
                val coreApkFile = File(cacheDir, "core.apk")

                // 如果文件已存在且较新，直接使用
                if (coreApkFile.exists()) {
                    val lastModified = coreApkFile.lastModified()
                    val currentTime = System.currentTimeMillis()
                    // 如果文件在5分钟内创建过，直接使用
                    if (currentTime - lastModified < 5 * 60 * 1000) {
                        return coreApkFile.absolutePath
                    }
                }

                // 从assets复制文件
                val assetManager = context.assets
                assetManager.open("core.apk").use { input ->
                    FileOutputStream(coreApkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var length: Int
                        while (input.read(buffer).also { length = it } > 0) {
                            output.write(buffer, 0, length)
                        }
                        output.flush()
                    }
                }

                LogCatcher.i("ApkBuilder", "核心APK已提取到: ${coreApkFile.absolutePath}")
                return coreApkFile.absolutePath

            } catch (e: IOException) {
                LogCatcher.e("ApkBuilder", "提取核心APK失败", e)
                return null
            }
        }

        // 修改AndroidManifest.xml
        private fun modifyAndroidManifest(
            context: Context,
            coreApkPath: String,
            projectPath: String,
            appName: String,
            packageName: String,
            versionName: String,
            versionCode: String,
            permissions: Array<String>?,
            minSdkVersion: Int,
            targetSdkVersion: Int,
            isDebug: Boolean
        ): String? {
            try {
                // 1. 创建临时目录
                val tempDir = File(context.cacheDir, "apk_temp_${System.currentTimeMillis()}")
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }

                // 2. 解压APK到临时目录
                unzipFile(coreApkPath, tempDir.absolutePath)

                // 3. 直接获取AndroidManifest.xml文件（标准APK结构）
                val manifestFile = File(tempDir, "AndroidManifest.xml")
                if (!manifestFile.exists()) {
                    LogCatcher.e("ApkBuilder", "未找到AndroidManifest.xml，APK结构可能异常")
                    deleteDirectory(tempDir)
                    return null
                }

                // 4. 替换图标文件（如果存在）
                replaceIconFile(projectPath, tempDir)

                // 5. 使用AxmlEditor修改AndroidManifest.xml
                modifyManifestWithAxmlEditor(
                    manifestFile,
                    appName,
                    packageName,
                    versionName,
                    versionCode,
                    permissions,
                    minSdkVersion,
                    targetSdkVersion,
                    isDebug
                )

                // 6. 重新打包成临时APK
                val tempApkPath = File(tempDir.parent, "temp_modified.apk").absolutePath

                // 删除已存在的临时文件
                val tempApkFile = File(tempApkPath)
                if (tempApkFile.exists()) {
                    tempApkFile.delete()
                }

                // 压缩目录为ZIP文件
                compressDirectoryToZip(tempDir.absolutePath, tempApkPath)

                // 验证临时APK是否创建成功
                if (!tempApkFile.exists()) {
                    LogCatcher.e("ApkBuilder", "临时APK文件未创建成功: $tempApkPath")
                    deleteDirectory(tempDir)
                    return null
                }

                LogCatcher.i(
                    "ApkBuilder",
                    "临时APK创建成功: $tempApkPath, 大小: ${tempApkFile.length()} 字节"
                )

                // 7. 清理临时目录
                deleteDirectory(tempDir)

                return tempApkPath

            } catch (e: Exception) {
                LogCatcher.e("ApkBuilder", "修改AndroidManifest.xml失败", e)
                return null
            }
        }

        // 替换图标文件
        private fun replaceIconFile(projectPath: String, tempDir: File) {
            try {
                // 检查项目目录中是否有icon.png
                val projectIconFile = File(projectPath, "icon.png")

                if (!projectIconFile.exists() || !projectIconFile.isFile) {
                    LogCatcher.i("ApkBuilder", "项目目录中未找到icon.png，跳过图标替换")
                    return
                }

                LogCatcher.i(
                    "ApkBuilder",
                    "找到项目图标: ${projectIconFile.absolutePath}, 大小: ${projectIconFile.length()} 字节"
                )

                // 在解压目录中查找res/9T.png文件
                val resDir = File(tempDir, "res")
                if (!resDir.exists() || !resDir.isDirectory) {
                    LogCatcher.w("ApkBuilder", "解压目录中未找到res目录，跳过图标替换")
                    return
                }

                val icon9TFile = File(resDir, "9T.png")
                if (!icon9TFile.exists() || !icon9TFile.isFile) {
                    LogCatcher.w("ApkBuilder", "在res目录中未找到9T.png文件，跳过图标替换")
                    return
                }

                LogCatcher.i(
                    "ApkBuilder",
                    "找到9T.png文件: ${icon9TFile.absolutePath}, 大小: ${icon9TFile.length()} 字节"
                )

                // 复制项目图标到9T.png位置
                FileInputStream(projectIconFile).use { input ->
                    FileOutputStream(icon9TFile).use { output ->
                        val buffer = ByteArray(8192)
                        var length: Int
                        while (input.read(buffer).also { length = it } > 0) {
                            output.write(buffer, 0, length)
                        }
                    }
                }

                LogCatcher.i(
                    "ApkBuilder",
                    "图标已替换: ${projectIconFile.absolutePath} -> ${icon9TFile.absolutePath}, 新大小: ${icon9TFile.length()} 字节"
                )

            } catch (e: Exception) {
                LogCatcher.e("ApkBuilder", "替换图标文件失败", e)
            }
        }

        // 使用AxmlEditor修改AndroidManifest.xml
        private fun modifyManifestWithAxmlEditor(
            manifestFile: File,
            appName: String,
            packageName: String,
            versionName: String,
            versionCode: String,
            permissions: Array<String>?,
            minSdkVersion: Int,
            targetSdkVersion: Int,
            isDebug: Boolean
        ) {
            LogCatcher.i("ApkBuilder", "开始修改AndroidManifest.xml")

            val manifestData = readFileToBytes(manifestFile)

            val originalPackageName = getOriginalPackageName(manifestData)
            LogCatcher.i("ApkBuilder", "原始包名: $originalPackageName, 目标包名: $packageName")

            val editor = com.nwdxlgzs.AxmlEditor.AxmlEditor(manifestData)

            if (packageName.isNotEmpty()) {
                editor.setPackageName(packageName)
            }

            if (appName.isNotEmpty()) {
                editor.setAppName(appName)
            }

            try {
                val versionCodeInt = versionCode.toInt()
                editor.setVersionCode(versionCodeInt)
            } catch (e: NumberFormatException) {
                LogCatcher.w("ApkBuilder", "版本号格式错误: $versionCode")
                editor.setVersionCode(1)
            }

            if (versionName.isNotEmpty()) {
                editor.setVersionName(versionName)
            }

            if (minSdkVersion > 0) {
                editor.setMinimumSdk(minSdkVersion)
            }

            if (targetSdkVersion > 0) {
                editor.setTargetSdk(targetSdkVersion)
            }

            if (permissions != null && permissions.isNotEmpty()) {
                editor.setUsePermissions(permissions)
            }

            editor.commit()

            if (originalPackageName != null && packageName.isNotEmpty() && originalPackageName != packageName) {
                editor.fixPermissionNames(originalPackageName, packageName)
                LogCatcher.i(
                    "ApkBuilder",
                    "已修复权限名称中的包名: $originalPackageName -> $packageName"
                )
            }

            FileOutputStream(manifestFile).use { fos ->
                editor.writeTo(fos)
            }

            LogCatcher.i("ApkBuilder", "AndroidManifest.xml修改完成")
        }

        // 辅助方法：从二进制AndroidManifest中读取原始包名
        private fun getOriginalPackageName(manifestData: ByteArray): String? {
            try {
                val reader = com.nwdxlgzs.AxmlEditor.rt.Reader(manifestData)
                val packageName = arrayOfNulls<String>(1)

                reader.accept(object : com.nwdxlgzs.AxmlEditor.rt.Visitor() {
                    override fun child(
                        ns: String?,
                        name: String?
                    ): com.nwdxlgzs.AxmlEditor.rt.NodeVisitor {
                        return object :
                            com.nwdxlgzs.AxmlEditor.rt.NodeVisitor(super.child(ns, name)) {
                            override fun attr(
                                ns: String?,
                                name: String?,
                                resourceId: Int,
                                type: Int,
                                value: Any?
                            ) {
                                if (name.equals("package", ignoreCase = true) && value is String) {
                                    packageName[0] = value
                                }
                                super.attr(ns, name, resourceId, type, value)
                            }
                        }
                    }
                })

                return packageName[0]
            } catch (e: IOException) {
                LogCatcher.e("ApkBuilder", "读取原始包名失败", e)
                return null
            }
        }

        // 添加项目文件到APK
        private fun addProjectFilesToApk(
            context: Context,
            tempApkPath: String,
            projectPath: String,
            outputPath: String,
            mavenJars: List<File> = emptyList()
        ): String? {
            val L = getSharedLuaState()

            try {
                // 1. 创建临时工作目录
                val workDir = File(context.cacheDir, "apk_work_${System.currentTimeMillis()}")
                if (!workDir.exists()) {
                    workDir.mkdirs()
                }

                // 2. 解压临时APK
                unzipFile(tempApkPath, workDir.absolutePath)

                // 3. 递归分析项目文件和库文件，获取所有引用的模块
                val referencedModules = recursivelyAnalyzeModules(projectPath, workDir)
                LogCatcher.i(
                    "ApkBuilder",
                    "总共找到 ${referencedModules.size} 个引用的模块: $referencedModules"
                )

                // 4. 清理未引用的库文件
                cleanUnusedLibraries(workDir, referencedModules, projectPath)

                // 5. 加密core.apk中引用的库文件
                encryptCoreLibraries(L, workDir, referencedModules)

                // 6. 创建assets目录
                val assetsDir = File(workDir, "assets")
                if (!assetsDir.exists()) {
                    assetsDir.mkdirs()
                }

                // 7. 复制项目文件到assets
                copyProjectToAssets(projectPath, assetsDir.absolutePath)

                // 8. 加密项目文件
                encryptProjectFiles(L, assetsDir)

                // ------------------ Java 编译与 DEX 生成 ------------------
                val androidJarPath = extractAndroidJarToCache(context)

                if (androidJarPath != null) {
                    // 先处理 Maven JAR 转 DEX（如果没有 Java 源文件也要处理）
                    if (mavenJars.isNotEmpty()) {
                        addMavenJarsToDexCompilation(context, mavenJars, workDir, androidJarPath)
                    }
                    
                    // 编译 Java 并生成 DEX（传入 Maven JAR）
                    compileJavaAndGenerateDexWithD8(
                        context,
                        projectPath,
                        workDir,
                        androidJarPath,
                        mavenJars
                    )

                    // 删除 assets 下的 java 源文件（避免打包进 APK）
                    val assetsJavaDir = File(assetsDir, "java")
                    if (assetsJavaDir.exists() && assetsJavaDir.isDirectory) {
                        assetsJavaDir.deleteRecursively()
                        LogCatcher.i("ApkBuilder", "已删除 assets 中的 java 源文件目录")
                    }
                } else {
                    LogCatcher.w("ApkBuilder", "缺少 android.jar，跳过 Java 编译")
                }
                // ---------------------------------------------------------

                // 9. 删除已存在的输出文件
                val outputFile = File(outputPath)
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                // 10. 重新打包成最终APK
                compressDirectoryToZip(workDir.absolutePath, outputPath)

                // 11. 验证APK文件
                if (!outputFile.exists()) {
                    LogCatcher.e("ApkBuilder", "APK文件未创建成功: $outputPath")
                    deleteDirectory(workDir)
                    return null
                }

                val fileSize = outputFile.length()
                LogCatcher.i("ApkBuilder", "APK文件创建成功: $outputPath, 大小: $fileSize 字节")

                // 12. 清理工作目录
                deleteDirectory(workDir)

                return outputPath

            } catch (e: Exception) {
                LogCatcher.e("ApkBuilder", "添加项目文件失败", e)
                return null
            }
        }

        // 加密core.apk中的库文件
        private fun encryptCoreLibraries(
            L: LuaState,
            workDir: File,
            referencedModules: Set<String>
        ) {
            LogCatcher.i("ApkBuilder", "开始加密core.apk中的库文件")

            if (L.isClosed) {
                LogCatcher.e("ApkBuilder", "LuaState无效或已关闭，无法加密库文件")
                throw RuntimeException("LuaState无效或已关闭")
            }

            val luaDir = File(workDir, "lua")
            if (!luaDir.exists() || !luaDir.isDirectory) {
                LogCatcher.i("ApkBuilder", "core.apk中未找到lua目录，跳过库文件加密")
                return
            }

            var encryptedCount = 0
            var failedCount = 0

            for (moduleName in referencedModules) {
                val luaFilePath = moduleName.replace('.', '/') + ".lua"
                val luaFile = File(luaDir, luaFilePath)

                if (luaFile.exists() && luaFile.isFile) {
                    val success = encryptLuaFile(L, luaFile)
                    if (success) {
                        encryptedCount++
                    } else {
                        failedCount++
                    }
                }
            }

            LogCatcher.i(
                "ApkBuilder",
                "core.apk库文件加密完成: 成功 $encryptedCount 个, 失败 $failedCount 个"
            )
        }

        // 加密项目文件
        private fun encryptProjectFiles(L: LuaState, assetsDir: File) {
            LogCatcher.i("ApkBuilder", "开始加密项目文件")

            if (L.isClosed) {
                LogCatcher.e("ApkBuilder", "LuaState无效或已关闭，无法加密项目文件")
                throw RuntimeException("LuaState无效或已关闭")
            }

            val counters =
                intArrayOf(0, 0, 0, 0) // [luaEncrypted, alyEncrypted, luaFailed, alyFailed]
            encryptDirectoryRecursive(L, assetsDir, assetsDir, counters)

            LogCatcher.i(
                "ApkBuilder",
                "项目文件加密完成: Lua成功 ${counters[0]} 个, ALY成功 ${counters[1]} 个, Lua失败 ${counters[2]} 个, ALY失败 ${counters[3]} 个"
            )
        }

        // 递归加密目录
        private fun encryptDirectoryRecursive(
            L: LuaState,
            baseDir: File,
            dir: File,
            counters: IntArray
        ) {
            val files = dir.listFiles() ?: return

            for (file in files) {
                when {
                    file.isDirectory -> encryptDirectoryRecursive(L, baseDir, file, counters)
                    file.isFile -> {
                        val fileName = file.name.lowercase(Locale.getDefault())

                        when {
                            fileName.endsWith(".lua") -> {
                                val success = encryptLuaFile(L, file)
                                if (success) {
                                    counters[0]++ // luaEncrypted
                                } else {
                                    counters[2]++ // luaFailed
                                }
                            }

                            fileName.endsWith(".aly") -> {
                                val success = encryptAlyFile(L, file)
                                if (success) {
                                    counters[1]++ // alyEncrypted
                                } else {
                                    counters[3]++ // alyFailed
                                }
                            }
                        }
                    }
                }
            }
        }

        // 加密Lua文件
        private fun encryptLuaFile(L: LuaState, luaFile: File): Boolean {
            return try {
                // 使用 ConsoleUtil.build 编译文件
                val result = ConsoleUtil.build(L, luaFile.absolutePath)

                // 从返回的 table 中获取 path
                val resultTable = result as? Map<*, *>
                val encryptedPath = resultTable?.get("path") as? String
                val errorMsg = resultTable?.get("error") as? String

                if (encryptedPath != null) {
                    val encryptedFile = File(encryptedPath)

                    if (encryptedFile.exists()) {
                        if (!luaFile.delete()) {
                            LogCatcher.w("ApkBuilder", "无法删除原Lua文件: ${luaFile.absolutePath}")
                            encryptedFile.delete()
                            false
                        } else {
                            val renamedFile = File(luaFile.absolutePath)
                            if (!encryptedFile.renameTo(renamedFile)) {
                                LogCatcher.e(
                                    "ApkBuilder",
                                    "无法重命名.luac文件为.lua: $encryptedPath"
                                )
                                encryptedFile.delete()
                                false
                            } else {
                                true
                            }
                        }
                    } else {
                        // 如果加密后的文件不存在，抛出异常停止构建
                        throw RuntimeException("加密后的文件不存在: $encryptedPath")
                    }
                } else {
                    // 如果编译出错，抛出异常停止构建
                    throw RuntimeException("Lua文件编译失败: ${errorMsg ?: "未知错误"}")
                }
            } catch (e: Exception) {
                // 捕获异常并重新抛出，停止构建进程
                LogCatcher.e("ApkBuilder", "加密Lua文件异常: ${luaFile.absolutePath}", e)
                throw RuntimeException("加密Lua文件失败: ${e.message}")
            }
        }

        // 加密Aly文件
        private fun encryptAlyFile(L: LuaState, alyFile: File): Boolean {
            return try {
                // 空文件无法编译，跳过加密
                if (alyFile.length() == 0L) {
                    LogCatcher.w("ApkBuilder", "跳过空ALY文件: ${alyFile.absolutePath}")
                    return true
                }

                // 使用 ConsoleUtil.build 编译文件
                val result = ConsoleUtil.build(L, alyFile.absolutePath)

                // 从返回的 table 中获取 path
                val resultTable = result as? Map<*, *>
                val encryptedPath = resultTable?.get("path") as? String
                val errorMsg = resultTable?.get("error") as? String

                if (encryptedPath != null) {
                    val encryptedFile = File(encryptedPath)

                    if (encryptedFile.exists()) {
                        if (!alyFile.delete()) {
                            LogCatcher.w("ApkBuilder", "无法删除原ALY文件: ${alyFile.absolutePath}")
                            encryptedFile.delete()
                            false
                        } else {
                            val newPath = alyFile.absolutePath.replace(".aly", ".lua")
                            val renamedFile = File(newPath)

                            if (!encryptedFile.renameTo(renamedFile)) {
                                LogCatcher.e(
                                    "ApkBuilder",
                                    "无法重命名.alyc文件为.lua: $encryptedPath"
                                )
                                encryptedFile.delete()
                                false
                            } else {
                                true
                            }
                        }
                    } else {
                        // 如果加密后的文件不存在，抛出异常停止构建
                        throw RuntimeException("加密后的文件不存在: $encryptedPath")
                    }
                } else {
                    // 如果编译出错，抛出异常停止构建
                    throw RuntimeException("ALY文件编译失败: ${errorMsg ?: "未知错误"}")
                }
            } catch (e: Exception) {
                // 捕获异常并重新抛出，停止构建进程
                LogCatcher.e("ApkBuilder", "加密ALY文件异常: ${alyFile.absolutePath}", e)
                throw RuntimeException("加密ALY文件失败: ${e.message}")
            }
        }

        // 获取相对路径
        private fun getRelativePath(baseDir: File, file: File): String {
            val basePath = baseDir.absolutePath
            val filePath = file.absolutePath

            return if (filePath.startsWith(basePath)) {
                filePath.substring(basePath.length + 1)
            } else filePath
        }

        // 递归分析项目文件和库文件，获取所有引用的模块
        private fun recursivelyAnalyzeModules(projectPath: String, workDir: File): Set<String> {
            val allModules = HashSet<String>()
            val analyzedFiles = HashSet<String>()
            val moduleQueue = LinkedList<String>()

            val projectModules = analyzeProjectForModules(projectPath)
            allModules.addAll(projectModules)
            moduleQueue.addAll(projectModules)

            LogCatcher.i("ApkBuilder", "项目文件分析完成，找到 ${projectModules.size} 个模块")

            while (moduleQueue.isNotEmpty()) {
                val module = moduleQueue.poll()

                val luaFilePath = module.replace('.', '/') + ".lua"
                val luaFile = File(workDir, "lua/$luaFilePath")

                val fileKey = luaFile.absolutePath
                if (analyzedFiles.contains(fileKey)) {
                    continue
                }

                if (luaFile.exists() && luaFile.isFile) {
                    analyzedFiles.add(fileKey)

                    val newModules = analyzeFileForModules(luaFile)

                    for (newModule in newModules) {
                        if (!allModules.contains(newModule)) {
                            allModules.add(newModule)
                            moduleQueue.add(newModule)
                        }
                    }
                }
            }

            return allModules
        }

        // 分析项目文件，提取所有引用的模块
        private fun analyzeProjectForModules(projectPath: String): Set<String> {
            val modules = HashSet<String>()
            val projectDir = File(projectPath)

            analyzeDirectoryForModules(projectDir, modules)

            LogCatcher.i("ApkBuilder", "项目文件中共找到 ${modules.size} 个引用的模块")
            return modules
        }

        // 递归分析目录中的Lua和ALY文件
        private fun analyzeDirectoryForModules(dir: File, modules: MutableSet<String>) {
            val files = dir.listFiles() ?: return

            for (file in files) {
                when {
                    file.isDirectory -> analyzeDirectoryForModules(file, modules)
                    file.isFile && (file.name.lowercase(Locale.getDefault()).endsWith(".lua") ||
                            file.name.lowercase(Locale.getDefault()).endsWith(".aly")) -> {
                        analyzeFileAndAddToSet(file, modules)
                    }
                }
            }
        }

        // 分析单个文件，提取require和import语句，将结果添加到Set中
        private fun analyzeFileAndAddToSet(file: File, modules: MutableSet<String>) {
            val fileModules = analyzeFileForModules(file)
            modules.addAll(fileModules)
        }

        // 分析单个文件，提取require和import语句，返回Set<String>
        private fun analyzeFileForModules(file: File): Set<String> {
            val modules = HashSet<String>()
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    for (pattern in PATTERNS) {
                        val p = Pattern.compile(pattern)
                        val m = p.matcher(line)

                        while (m.find()) {
                            val moduleName = m.group(1)
                            if (moduleName != null && moduleName.trim().isNotEmpty()) {
                                modules.add(moduleName)
                            }
                        }
                    }
                }
            }
            return modules
        }

        // 清理未引用的库文件
        private fun cleanUnusedLibraries(
            workDir: File,
            referencedModules: Set<String>,
            projectPath: String
        ) {
            // 必须保留的文件集合
            val mustKeepFiles = hashSetOf("libluajava.so")

            // 检查项目中是否使用了 LuaParserUtil，如果是则保留 libluaparse.so
            val projectDir = File(projectPath)
            if (projectDir.exists()) {
                val usesLuaParserUtil = checkProjectForLuaParserUtil(projectDir)
                if (usesLuaParserUtil) {
                    mustKeepFiles.add("libluaparser.so")
                }
            }

            val luaDir = File(workDir, "lua")
            if (luaDir.exists() && luaDir.isDirectory) {
                cleanLuaDirectory(luaDir, referencedModules)
            }

            val libArmV7aDir = File(workDir, "lib/armeabi-v7a")
            if (libArmV7aDir.exists() && libArmV7aDir.isDirectory) {
                cleanLibDirectory(libArmV7aDir, referencedModules, mustKeepFiles)
            }

            val libArm64V8aDir = File(workDir, "lib/arm64-v8a")
            if (libArm64V8aDir.exists() && libArm64V8aDir.isDirectory) {
                cleanLibDirectory(libArm64V8aDir, referencedModules, mustKeepFiles)
            }

            LogCatcher.i("ApkBuilder", "库文件清理完成")
        }

        // 检查项目目录中是否存在 LuaParserUtil 的引用
        private fun checkProjectForLuaParserUtil(projectDir: File): Boolean {
            return checkDirectoryForLuaParserUtil(projectDir)
        }

        // 递归检查目录中的文件
        private fun checkDirectoryForLuaParserUtil(dir: File): Boolean {
            val files = dir.listFiles() ?: return false

            for (file in files) {
                when {
                    file.isDirectory -> {
                        if (checkDirectoryForLuaParserUtil(file)) {
                            return true
                        }
                    }

                    file.isFile -> {
                        val fileName = file.name.lowercase(Locale.getDefault())
                        // 只检查 Lua 和 ALY 文件
                        if (fileName.endsWith(".lua") || fileName.endsWith(".aly")) {
                            if (checkFileForLuaParserUtil(file)) {
                                return true
                            }
                        }
                    }
                }
            }
            return false
        }

        // 检查单个文件是否包含 LuaParserUtil 引用
        private fun checkFileForLuaParserUtil(file: File): Boolean {
            return try {
                BufferedReader(FileReader(file)).use { reader ->
                    val content = reader.readText()
                    content.contains("muling.views.tool.utils.LuaParserUtil")
                }
            } catch (e: Exception) {
                false
            }
        }

        // 清理lua目录中的文件
        private fun cleanLuaDirectory(luaDir: File, referencedModules: Set<String>) {
            cleanLuaDirectoryRecursive(luaDir, "", referencedModules)
        }

        private fun cleanLuaDirectoryRecursive(
            dir: File,
            relativePath: String,
            referencedModules: Set<String>
        ) {
            val files = dir.listFiles() ?: return

            for (file in files) {
                val newRelativePath =
                    if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"

                when {
                    file.isDirectory -> cleanLuaDirectoryRecursive(
                        file,
                        newRelativePath,
                        referencedModules
                    )

                    file.isFile && file.name.lowercase(Locale.getDefault()).endsWith(".lua") -> {
                        val isReferenced = isLuaFileReferenced(newRelativePath, referencedModules)

                        if (!isReferenced) {
                            if (!file.delete()) {
                                LogCatcher.w("ApkBuilder", "无法删除文件: ${file.absolutePath}")
                            }
                        }
                    }
                }
            }
        }

        // 检查lua文件是否被引用
        private fun isLuaFileReferenced(
            luaFilePath: String,
            referencedModules: Set<String>
        ): Boolean {
            val moduleName = luaFilePath.substring(0, luaFilePath.length - 4).replace("/", ".")
            return referencedModules.contains(moduleName)
        }

        // 清理lib目录中的文件
        private fun cleanLibDirectory(
            libDir: File,
            referencedModules: Set<String>,
            mustKeepFiles: Set<String>
        ) {
            val files = libDir.listFiles() ?: return

            for (file in files) {
                if (file.isFile && file.name.lowercase(Locale.getDefault()).endsWith(".so")) {
                    val soName = file.name

                    if (mustKeepFiles.contains(soName)) {
                        continue
                    }

                    val isReferenced = isSoFileReferenced(soName, referencedModules)

                    if (!isReferenced) {
                        if (!file.delete()) {
                            LogCatcher.w("ApkBuilder", "无法删除文件: ${file.absolutePath}")
                        }
                    }
                }
            }
        }

        // 检查so文件是否被引用
        private fun isSoFileReferenced(
            soFileName: String,
            referencedModules: Set<String>
        ): Boolean {
            if (soFileName.startsWith("lib") && soFileName.endsWith(".so")) {
                val moduleName = soFileName.substring(3, soFileName.length - 3)
                if (!moduleName.contains(".")) {
                    return referencedModules.contains(moduleName)
                }
            }
            return false
        }

        // 复制项目文件到assets目录
        private fun copyProjectToAssets(projectPath: String, assetsPath: String) {
            try {
                val projectDir = File(projectPath)
                val assetsDir = File(assetsPath)

                if (!projectDir.exists() || !projectDir.isDirectory) {
                    throw IOException("项目目录不存在: $projectPath")
                }

                copyDirectory(projectDir, assetsDir)
                LogCatcher.i("ApkBuilder", "项目文件已复制到assets: $assetsPath")

            } catch (e: IOException) {
                LogCatcher.e("ApkBuilder", "复制项目文件失败", e)
                throw RuntimeException(e)
            }
        }

        // 工具方法：读取文件到字节数组
        private fun readFileToBytes(file: File): ByteArray {
            FileInputStream(file).use { fis ->
                ByteArrayOutputStream().use { bos ->
                    val buffer = ByteArray(8192)
                    var length: Int
                    while (fis.read(buffer).also { length = it } > 0) {
                        bos.write(buffer, 0, length)
                    }
                    return bos.toByteArray()
                }
            }
        }

        // 工具方法：解压ZIP文件
        private fun unzipFile(zipFilePath: String, destDirectory: String) {
            val destDir = File(destDirectory)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            ZipFile(zipFilePath).use { zipFile ->
                val entries = zipFile.entries()

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val filePath = destDirectory + File.separator + entry.name

                    if (!entry.isDirectory) {
                        File(filePath).parentFile?.mkdirs()

                        zipFile.getInputStream(entry).use { input ->
                            FileOutputStream(filePath).use { output ->
                                val buffer = ByteArray(8192)
                                var length: Int
                                while (input.read(buffer).also { length = it } > 0) {
                                    output.write(buffer, 0, length)
                                }
                            }
                        }
                    } else {
                        val dir = File(filePath)
                        dir.mkdirs()
                    }
                }
            }

            LogCatcher.i("ApkBuilder", "ZIP文件解压完成: $zipFilePath -> $destDirectory")
        }

        // 压缩目录为ZIP文件
        private fun compressDirectoryToZip(sourceDir: String, zipFilePath: String) {
            val sourceDirFile = File(sourceDir)
            if (!sourceDirFile.exists()) {
                throw IOException("源目录不存在: $sourceDir")
            }

            val zipFile = File(zipFilePath)
            val parentDir = zipFile.parentFile
            parentDir?.takeIf { !it.exists() }?.mkdirs()

            if (zipFile.exists()) {
                zipFile.delete()
            }

            FileOutputStream(zipFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    compressDirectoryRecursive(sourceDirFile, "", zos)
                }
            }

            LogCatcher.i(
                "ApkBuilder",
                "目录压缩完成: $sourceDir -> $zipFilePath, 大小: ${zipFile.length()} 字节"
            )
        }

        // 递归压缩目录
        private fun compressDirectoryRecursive(
            file: File,
            relativePath: String,
            zos: ZipOutputStream
        ) {
            if (file.isHidden) {
                return
            }

            if (file.isDirectory) {
                var dirPath = relativePath
                if (dirPath.isNotEmpty() && !dirPath.endsWith("/")) {
                    dirPath += "/"
                }

                if (dirPath.isNotEmpty()) {
                    zos.putNextEntry(ZipEntry(dirPath))
                    zos.closeEntry()
                }

                val children = file.listFiles()
                children?.forEach { childFile ->
                    val childPath = dirPath + childFile.name
                    compressDirectoryRecursive(childFile, childPath, zos)
                }
                return
            }

            val filePath = relativePath
            val zipEntry = ZipEntry(filePath)

            // Android R+要求resources.arsc必须STORED
            if ("resources.arsc" == file.name) {
                // 读取文件数据
                val fileData = file.readBytes()

                zipEntry.method = ZipEntry.STORED
                zipEntry.size = fileData.size.toLong()
                zipEntry.compressedSize = fileData.size.toLong()
                zipEntry.crc = calculateCrc32(fileData)  // 现在调用 ByteArray 版本

                // 直接写入数据，不需要 FileInputStream
                zos.putNextEntry(zipEntry)
                zos.write(fileData)
                zos.closeEntry()
            } else {
                zipEntry.method = ZipEntry.DEFLATED
                FileInputStream(file).use { fis ->
                    zos.putNextEntry(zipEntry)
                    val buffer = ByteArray(8192)
                    var length: Int
                    while (fis.read(buffer).also { length = it } > 0) {
                        zos.write(buffer, 0, length)
                    }
                    zos.closeEntry()
                }
            }
        }

        // 计算文件的CRC32值（File版本）
        private fun calculateCrc32(file: File): Long {
            val crc = CRC32()
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var length: Int
                while (fis.read(buffer).also { length = it } != -1) {
                    crc.update(buffer, 0, length)
                }
            }
            return crc.value
        }

        // 计算字节数组的CRC32值（ByteArray版本）
        private fun calculateCrc32(data: ByteArray): Long {
            val crc = CRC32()
            crc.update(data)
            return crc.value
        }

        // 工具方法：复制目录
        private fun copyDirectory(source: File, destination: File) {
            if (source.isDirectory) {
                if (!destination.exists()) {
                    destination.mkdirs()
                }

                val files = source.list()
                files?.forEach { file ->
                    val srcFile = File(source, file)
                    val destFile = File(destination, file)
                    copyDirectory(srcFile, destFile)
                }
            } else {
                FileInputStream(source).use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(8192)
                        var length: Int
                        while (input.read(buffer).also { length = it } > 0) {
                            output.write(buffer, 0, length)
                        }
                    }
                }
            }
        }

        // 工具方法：删除目录
        private fun deleteDirectory(dir: File) {
            if (dir.exists()) {
                val files = dir.listFiles()
                files?.forEach { file ->
                    if (file.isDirectory) {
                        deleteDirectory(file)
                    } else {
                        file.delete()
                    }
                }
                dir.delete()
            }
        }

        // 签名APK
        private fun signerApk(
            context: Context,  // 添加 context 参数
            keyPath: String,
            pass: String,
            alias: String,
            keyPass: String,
            inPath: String,
            outPath: String
        ): Boolean {
            return try {
                // 先对输入的APK进行对齐处理（使用真正的 zipalign）
                val alignedPath = inPath + ".aligned"
                val aligned = zipAlignApk(context, inPath, alignedPath)

                val inputFile = if (aligned) {
                    File(alignedPath)
                } else {
                    LogCatcher.w("ApkBuilder", "对齐失败，使用原始文件签名")
                    File(inPath)
                }

                // 确保输入文件存在
                if (!inputFile.exists()) {
                    LogCatcher.e("ApkBuilder", "输入文件不存在: ${inputFile.absolutePath}")
                    return false
                }

                val signer = com.mcal.apksigner.ApkSigner(inputFile, File(outPath))
                signer.v1SigningEnabled = true
                signer.v2SigningEnabled = true
                signer.v3SigningEnabled = true
                signer.signRelease(File(keyPath), pass, alias, keyPass)

                // 清理临时对齐文件
                if (aligned && File(alignedPath).exists()) {
                    File(alignedPath).delete()
                }

                true
            } catch (e: Throwable) {
                LogCatcher.e("ApkBuilder", "签名过程异常", e as Exception?)
                e.printStackTrace()
                false
            }
        }

        /**
         * 使用系统 zipalign 工具对齐 APK
         * 从 /data/app/.../lib/arm64/libzipalign.so 直接执行
         */
        private fun zipAlignApk(
            context: Context,
            inputPath: String,
            outputPath: String
        ): Boolean {
            return try {
                // 获取 zipalign 路径：/data/app/.../lib/arm64/libzipalign.so
                val zipalignPath = File(context.applicationInfo.nativeLibraryDir, "libzipalign.so").absolutePath
                
                // 检查文件是否存在
                if (!File(zipalignPath).exists()) {
                    LogCatcher.e("ApkBuilder", "zipalign 不存在: $zipalignPath")
                    return simpleZipAlign(inputPath, outputPath)
                }

                LogCatcher.i("ApkBuilder", "使用 zipalign: $zipalignPath")

                // 删除已存在的输出文件
                File(outputPath).delete()

                // 构建命令：zipalign -f -p -v 4 input.apk output.apk
                val cmd = arrayOf(
                    zipalignPath,
                    "-f",           // 强制覆盖
                    "-p",           // 页对齐（4KB）
                    "-v",           // 详细输出
                    "4",            // 4字节对齐
                    inputPath,
                    outputPath
                )

                LogCatcher.i("ApkBuilder", "执行: ${cmd.joinToString(" ")}")

                // 执行进程
                val process = Runtime.getRuntime().exec(cmd)
                
                // 读取标准输出
                Thread {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            LogCatcher.i("ApkBuilder", "zipalign: $line")
                        }
                    }
                }.start()
                
                // 读取错误输出
                Thread {
                    process.errorStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            LogCatcher.e("ApkBuilder", "zipalign err: $line")
                        }
                    }
                }.start()

                // 等待完成
                val exitCode = process.waitFor()
                
                // 检查是否成功
                val success = exitCode == 0 && File(outputPath).exists() && File(outputPath).length() > 0
                
                if (success) {
                    LogCatcher.i("ApkBuilder", "zipalign 对齐成功: $outputPath")
                } else {
                    LogCatcher.e("ApkBuilder", "zipalign 失败，exit=$exitCode，回退到简单对齐")
                    // 失败后回退到简单对齐
                    return simpleZipAlign(inputPath, outputPath)
                }

                success

            } catch (e: Exception) {
                LogCatcher.e("ApkBuilder", "zipalign 异常，回退到简单对齐", e)
                simpleZipAlign(inputPath, outputPath)
            }
        }

        // 简化的 zipalign 实现 - 作为后备方案
        private fun simpleZipAlign(inputPath: String, outputPath: String): Boolean {
            var success = false
            var inputZip: ZipFile? = null
            var fos: FileOutputStream? = null
            var zos: ZipOutputStream? = null

            try {
                inputZip = ZipFile(inputPath)
                fos = FileOutputStream(outputPath)
                zos = ZipOutputStream(fos)

                val entries = inputZip.entries().toList().sortedBy { it.name }

                for (entry in entries) {
                    val newEntry = ZipEntry(entry.name)
                    val data = inputZip.getInputStream(entry).use { it.readBytes() }

                    // 对于需要 STORED 的文件（如 resources.arsc），确保正确设置
                    if (entry.method == ZipEntry.STORED || entry.name == "resources.arsc") {
                        newEntry.method = ZipEntry.STORED
                        newEntry.size = data.size.toLong()
                        newEntry.compressedSize = data.size.toLong()
                        newEntry.crc = calculateCrc32(data)
                    } else {
                        newEntry.method = ZipEntry.DEFLATED
                    }

                    zos.putNextEntry(newEntry)
                    zos.write(data)
                    zos.closeEntry()
                }

                success = true
            } catch (e: Exception) {
                LogCatcher.e("ApkBuilder", "简单对齐APK失败", e)
                success = false
            } finally {
                try {
                    zos?.close()
                } catch (_: Exception) {
                }
                try {
                    fos?.close()
                } catch (_: Exception) {
                }
                try {
                    inputZip?.close()
                } catch (_: Exception) {
                }
            }

            return success
        }

        // ------------------ Java 编译相关函数 ------------------
        // 从 assets 提取 android.jar 到缓存
        private fun extractAndroidJarToCache(context: Context): String? {
            try {
                val cacheDir = File(context.cacheDir, "java_build")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val androidJar = File(cacheDir, "android.jar")
                if (androidJar.exists() && androidJar.length() > 0) {
                    return androidJar.absolutePath
                }

                context.assets.open("android.jar").use { input ->
                    FileOutputStream(androidJar).use { output ->
                        input.copyTo(output)
                    }
                }
                LogCatcher.i("ApkBuilder", "android.jar 已提取到: ${androidJar.absolutePath}")
                return androidJar.absolutePath
            } catch (e: Exception) {
                LogCatcher.e("ApkBuilder", "提取 android.jar 失败", e)
                return null
            }
        }

        // 编译 Java 文件并生成 DEX（使用 ECJ 和 D8）
        private fun compileJavaAndGenerateDexWithD8(
            context: Context,
            projectPath: String,
            workDir: File,
            androidJarPath: String,
            mavenJars: List<File> = emptyList()
        ) {
            // 1. 检查 java 目录
            val javaDir = File(projectPath, "java")
            val hasJavaSource = javaDir.exists() && javaDir.isDirectory && javaDir.walk().any { it.isFile && it.name.endsWith(".java", ignoreCase = true) }
            
            // 2. 构建 classpath（包含 android.jar 和 Maven JAR）
            val classpath = if (mavenJars.isNotEmpty()) {
                buildClasspathWithMavenJars(androidJarPath, mavenJars)
            } else {
                androidJarPath
            }
            
            // 3. 如果没有 Java 源文件，只处理 Maven JAR 转 DEX（已在前面处理，这里跳过）
            if (!hasJavaSource) {
                LogCatcher.i("ApkBuilder", "项目中没有 java 目录或 Java 文件，跳过 Java 编译")
                return
            }

            // 4. 收集所有 Java 文件
            val javaFiles = mutableListOf<File>()
            javaDir.walk().forEach { file ->
                if (file.isFile && file.name.endsWith(".java", ignoreCase = true)) {
                    javaFiles.add(file)
                }
            }
            
            if (javaFiles.isEmpty()) {
                LogCatcher.i("ApkBuilder", "java 目录下没有 Java 文件，跳过编译")
                return
            }

            LogCatcher.i("ApkBuilder", "开始编译 Java 文件，共 ${javaFiles.size} 个")
            LogCatcher.i("ApkBuilder", "编译 classpath: $classpath")

            // 5. 创建临时输出目录
            val classOutputDir = File(workDir, "java_build_classes")
            classOutputDir.mkdirs()

            // 6. 使用 ECJ 编译 Java 源文件
            val outputStream = ByteArrayOutputStream()
            val errorStream = ByteArrayOutputStream()
            val outWriter = PrintWriter(outputStream)
            val errWriter = PrintWriter(errorStream)
            val ecj = EcjMain(outWriter, errWriter, false, null, null)
            
            val args = mutableListOf<String>()
            args.add("-d")
            args.add(classOutputDir.absolutePath)
            args.add("-cp")
            args.add(classpath)
            args.add("-1.8")
            javaFiles.forEach { file -> args.add(file.absolutePath) }

            try {
                val success = ecj.compile(args.toTypedArray())
                outWriter.flush()
                errWriter.flush()
                val output = outputStream.toString()
                val error = errorStream.toString()
                
                if (!success) {
                    LogCatcher.e("ApkBuilder", "Java 编译失败，输出: $output, 错误: $error")
                    throw RuntimeException("Java 编译失败，返回码: $success\n$error")
                }
                LogCatcher.i("ApkBuilder", "Java 编译成功")
            } catch (e: Exception) {
                LogCatcher.e("ApkBuilder", "Java 编译异常", e)
                throw RuntimeException("Java 编译失败: ${e.message}", e)
            }

            // 7. 将编译后的 class 文件打包为 JAR
            val jarFile = File(workDir, "project_classes.jar")
            createJarFromDirectory(classOutputDir, jarFile)

            // 8. 收集所有需要转 DEX 的输入
            val dexInputs = mutableListOf<File>()
            dexInputs.add(jarFile)
            dexInputs.addAll(mavenJars)

            // 9. 使用 D8 生成 DEX
            LogCatcher.i("ApkBuilder", "开始使用 D8 生成 DEX，输入: ${dexInputs.size} 个")
            try {
                val dexOutputDir = File(workDir, "java_build_dex")
                dexOutputDir.mkdirs()

                val d8Args = mutableListOf<String>()
                d8Args.add("--lib")
                d8Args.add(androidJarPath)
                d8Args.add("--output")
                d8Args.add(dexOutputDir.absolutePath)
                dexInputs.forEach { input ->
                    d8Args.add(input.absolutePath)
                }
                d8Args.add("--min-api")
                d8Args.add("21")

                D8.main(d8Args.toTypedArray())

                // 将生成的 dex 文件复制到 APK 工作目录
                addDexFilesToApkWorkDir(dexOutputDir, workDir)

                // 清理临时文件
                classOutputDir.deleteRecursively()
                jarFile.delete()
                dexOutputDir.deleteRecursively()
                
            } catch (e: Exception) {
                LogCatcher.e("ApkBuilder", "D8 DEX 生成失败", e)
                throw RuntimeException("D8 DEX 生成失败: ${e.message}", e)
            }
        }

        // 将目录中的所有 DEX 文件复制到目标目录，并自动重命名避免覆盖
        private fun addDexFilesToApkWorkDir(sourceDir: File, targetDir: File) {
            val dexFiles = sourceDir.listFiles { _, name -> name.endsWith(".dex", ignoreCase = true) } ?: return
            if (dexFiles.isEmpty()) return

            // 获取目标目录中现有的所有 classes*.dex
            val existingDex = targetDir.listFiles { _, name ->
                name.matches(Regex("classes(\\d*)\\.dex", RegexOption.IGNORE_CASE))
            } ?: emptyArray()

            // 计算现有最大的索引（classes.dex 索引为 1，classes2.dex 索引为 2...）
            val maxIndex = existingDex.mapNotNull { file ->
                val match = Regex("classes(\\d*)\\.dex", RegexOption.IGNORE_CASE).find(file.name)
                match?.groupValues?.get(1)?.toIntOrNull() ?: 1
            }.maxOrNull() ?: 1

            // 对每个新 dex 分配下一个可用索引
            var nextIndex = maxIndex + 1
            for (dexFile in dexFiles.sortedBy { it.name }) {
                val targetName = if (nextIndex == 1) "classes.dex" else "classes${nextIndex}.dex"
                val targetFile = File(targetDir, targetName)
                dexFile.copyTo(targetFile, overwrite = true)
                nextIndex++
            }
            LogCatcher.i("ApkBuilder", "已添加 ${dexFiles.size} 个 DEX 文件到 APK")
        }

        // 将目录打包为 JAR 文件
        private fun createJarFromDirectory(sourceDir: File, jarFile: File) {
            JarOutputStream(FileOutputStream(jarFile)).use { jos ->
                sourceDir.walk().forEach { file ->
                    if (file.isFile) {
                        val relativePath = file.relativeTo(sourceDir).path.replace('\\', '/')
                        val entry = JarEntry(relativePath)
                        jos.putNextEntry(entry)
                        file.inputStream().use { it.copyTo(jos) }
                        jos.closeEntry()
                    }
                }
            }
        }
        // ------------------ 结束 ------------------
    }
}