package com.luafabric.studio.falling.build.maven

import muling.views.tool.utils.LogCatcher
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * 依赖解析器（处理传递依赖 + 版本仲裁）
 */
class DependencyResolver(
    private val downloader: MavenDownloader,
    private val repositories: List<MavenRepository>
) {
    // 已解析的依赖缓存 - 按groupId:artifactId存储，实现版本仲裁
    private val resolvedDependencies = mutableMapOf<String, MavenDependency>()
    
    // 依赖管理（版本锁定）
    private val dependencyManagement = mutableMapOf<String, String>()

    /**
     * 解析传递依赖（带版本仲裁）- 关键修复：递归解析传递依赖
     */
    suspend fun resolveTransitiveDependencies(
        rootDependencies: List<MavenDependency>,
        callback: MavenDownloader.DownloadCallback?
    ): List<MavenDependency> {
        
        val result = mutableListOf<MavenDependency>()
        val queue = ArrayDeque<MavenDependency>()
        
        // 初始化队列
        rootDependencies.forEach { 
            LogCatcher.i("DependencyResolver", "加入根依赖: ${it.getCoordinates()}")
            queue.add(it) 
        }

        var processedCount = 0
        val processedKeys = mutableSetOf<String>() // 关键修复：防止循环依赖和重复处理

        while (queue.isNotEmpty()) {
            val dep = queue.removeFirst()
            val key = "${dep.groupId}:${dep.artifactId}" // 不包含版本

            // 关键修复：检查是否已经处理过这个依赖（防止循环依赖）
            if (key in processedKeys) {
                // 检查版本是否需要升级
                val existing = resolvedDependencies[key]
                if (existing != null && compareVersions(dep.version, existing.version) > 0) {
                    LogCatcher.i("DependencyResolver", "版本仲裁: 升级 $key ${existing.version} -> ${dep.version}")
                    result.removeAll { it.groupId == dep.groupId && it.artifactId == dep.artifactId }
                    resolvedDependencies[key] = dep
                    result.add(dep)
                    // 重新加入队列处理其传递依赖（因为版本变了）
                    queue.add(dep)
                }
                continue
            }

            // 版本仲裁：如果已存在该库，比较版本，保留高的
            if (resolvedDependencies.containsKey(key)) {
                val existing = resolvedDependencies[key]!!
                if (compareVersions(dep.version, existing.version) > 0) {
                    LogCatcher.i("DependencyResolver", "版本仲裁: 升级 $key ${existing.version} -> ${dep.version}")
                    result.removeAll { it.groupId == dep.groupId && it.artifactId == dep.artifactId }
                    resolvedDependencies[key] = dep
                    result.add(dep)
                } else {
                    LogCatcher.d("DependencyResolver", "版本仲裁: 忽略旧版本 ${dep.getCoordinates()}，已有 ${existing.version}")
                    processedKeys.add(key) // 标记为已处理
                    continue
                }
            } else {
                // 全新的依赖，直接添加
                resolvedDependencies[key] = dep
                result.add(dep)
            }

            processedKeys.add(key) // 标记为已处理
            processedCount++
            LogCatcher.i("DependencyResolver", "[$processedCount] 开始处理: ${dep.getCoordinates()}")

            // 应用依赖管理版本锁定
            val managedDep = applyDependencyManagement(dep)
            
            // 关键修复：检查scope，只有compile和runtime的依赖才需要解析传递依赖
            if (managedDep.scope !in setOf("compile", "runtime", "default")) {
                LogCatcher.d("DependencyResolver", "  跳过scope=${managedDep.scope}的依赖: ${managedDep.getCoordinates()}")
                continue
            }
            
            LogCatcher.i("DependencyResolver", "  解析依赖: ${managedDep.getCoordinates()} (scope=${managedDep.scope}, optional=${managedDep.optional})")
            
            // 下载依赖获取POM（加超时保护）
            val downloadResult = try {
                withTimeout(30000) { // 单个POM下载30秒超时
                    downloader.downloadDependency(managedDep, repositories, null)
                }
            } catch (e: Exception) {
                LogCatcher.e("DependencyResolver", "下载POM失败: ${managedDep.getCoordinates()}", e)
                continue
            }
            
            if (downloadResult is MavenDownloader.DownloadResult.Success) {
                // 解析传递依赖
                val pomFile = downloadResult.files.find { it.name.endsWith(".pom") }
                if (pomFile != null) {
                    LogCatcher.i("DependencyResolver", "  解析POM: ${pomFile.name}")
                    val transitiveDeps = parseTransitiveDependencies(pomFile, managedDep)
                    LogCatcher.i("DependencyResolver", "  发现 ${transitiveDeps.size} 个传递依赖")

                    // 更新依赖管理
                    updateDependencyManagement(pomFile)

                    // 关键修复：递归处理传递依赖——将所有传递依赖加入队列
                    transitiveDeps.filter { !isExcluded(it, managedDep.exclusions) }
                        .forEach { childDep ->
                            val childKey = "${childDep.groupId}:${childDep.artifactId}"
                            
                            // 检查是否已经在队列中或已处理
                            if (childKey !in processedKeys) {
                                LogCatcher.i("DependencyResolver", "    加入队列: ${childDep.getCoordinates()}")
                                queue.add(childDep) // 关键：加入队列，后续会递归处理其传递依赖
                            } else {
                                // 已存在，检查版本是否需要升级
                                val existing = resolvedDependencies[childKey]
                                if (existing != null && compareVersions(childDep.version, existing.version) > 0) {
                                    LogCatcher.i("DependencyResolver", "    传递依赖升级: $childKey ${existing.version} -> ${childDep.version}")
                                    result.removeAll { it.groupId == childDep.groupId && it.artifactId == childDep.artifactId }
                                    resolvedDependencies[childKey] = childDep
                                    result.add(childDep)
                                    // 重新加入队列处理其传递依赖
                                    queue.add(childDep)
                                } else {
                                    LogCatcher.d("DependencyResolver", "    传递依赖忽略旧版本: ${childDep.getCoordinates()}，已有 ${existing?.version}")
                                }
                            }
                        }
                } else {
                    LogCatcher.w("DependencyResolver", "  未找到POM文件: ${managedDep.getCoordinates()}")
                }
            } else {
                val reason = (downloadResult as? MavenDownloader.DownloadResult.Failure)?.reason ?: "未知错误"
                LogCatcher.w("DependencyResolver", "  无法解析依赖: ${managedDep.getCoordinates()} - $reason")
            }
        }

        LogCatcher.i("DependencyResolver", "依赖解析完成，共 ${result.size} 个依赖（已去重和仲裁）")
        
        // 打印依赖树用于调试
        LogCatcher.i("DependencyResolver", getDependencyTree(result))
        
        return result
    }

    /**
     * Maven版本比较（支持SNAPSHOT、rc、alpha、beta等）
     * 返回：1表示v1>v2，-1表示v1<v2，0表示相等
     */
    private fun compareVersions(v1: String, v2: String): Int {
        // 标准化版本字符串
        val normalizedV1 = normalizeVersion(v1)
        val normalizedV2 = normalizeVersion(v2)
        
        val parts1 = normalizedV1.split(".", "-", "_").filter { it.isNotEmpty() }
        val parts2 = normalizedV2.split(".", "-", "_").filter { it.isNotEmpty() }
        
        val maxLen = maxOf(parts1.size, parts2.size)
        
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrNull(i) ?: "0"
            val p2 = parts2.getOrNull(i) ?: "0"
            
            // 尝试作为数字比较
            val num1 = p1.toIntOrNull()
            val num2 = p2.toIntOrNull()
            
            val cmp = if (num1 != null && num2 != null) {
                num1.compareTo(num2)
            } else {
                // 字符串比较（处理SNAPSHOT、rc等）
                comparePreRelease(p1, p2)
            }
            
            if (cmp != 0) return cmp
        }
        
        return 0
    }
    
    private fun normalizeVersion(version: String): String {
        return version.replace("-SNAPSHOT", ".999999-SNAPSHOT") // SNAPSHOT视为极高版本
            .replace("-rc", ".RC")
            .replace("-RC", ".RC")
            .replace("-beta", ".BETA")
            .replace("-alpha", ".ALPHA")
    }
    
    private fun comparePreRelease(s1: String, s2: String): Int {
        // 优先级：SNAPSHOT > RC > beta > alpha > 其他
        val order = mapOf(
            "SNAPSHOT" to 5,
            "RC" to 4, "BETA" to 3, "ALPHA" to 2,
            "RELEASE" to 1, "FINAL" to 1, "GA" to 1
        )
        
        val o1 = order[s1.uppercase()] ?: 0
        val o2 = order[s2.uppercase()] ?: 0
        
        return if (o1 != o2) o1.compareTo(o2) else s1.compareTo(s2)
    }

    /**
     * 从POM文件解析传递依赖
     */
    private fun parseTransitiveDependencies(
        pomFile: File,
        parentDependency: MavenDependency
    ): List<MavenDependency> {
        
        val parser = PomParser()
        val pomInfo = parser.parse(pomFile) ?: run {
            LogCatcher.w("DependencyResolver", "POM解析失败: ${pomFile.absolutePath}")
            return emptyList()
        }

        LogCatcher.d("DependencyResolver", "  POM解析结果: ${pomInfo.groupId}:${pomInfo.artifactId}:${pomInfo.version}")
        LogCatcher.d("DependencyResolver", "  - 显式dependencies: ${pomInfo.dependencies.size} 个")
        LogCatcher.d("DependencyResolver", "  - dependencyManagement: ${pomInfo.dependencyManagement.size} 个")

        // 关键修复：使用PomParser已经处理好的effectiveDependencies
        return pomInfo.dependencies.filter { dep ->
            // 过滤掉不需要的scope
            dep.scope !in setOf("test", "provided", "system") && !dep.optional
        }.map { dep ->
            // 继承exclusions
            val mergedExclusions = (dep.exclusions + parentDependency.exclusions).distinct()
            dep.copy(exclusions = mergedExclusions)
        }
    }

    /**
     * 更新依赖管理映射
     */
    private fun updateDependencyManagement(pomFile: File) {
        val parser = PomParser()
        val pomInfo = parser.parse(pomFile) ?: return

        pomInfo.dependencyManagement.forEach { dep ->
            val key = "${dep.groupId}:${dep.artifactId}"
            if (dep.version.isNotEmpty()) {
                dependencyManagement[key] = dep.version
                LogCatcher.d("DependencyResolver", "  版本锁定: $key -> ${dep.version}")
            }
        }
    }

    /**
     * 应用依赖管理版本锁定
     */
    private fun applyDependencyManagement(dep: MavenDependency): MavenDependency {
        if (dep.version.isNotEmpty() && !dep.isDynamicVersion()) {
            return dep // 已有明确版本
        }

        val key = "${dep.groupId}:${dep.artifactId}"
        val managedVersion = dependencyManagement[key]
        
        return if (managedVersion != null) {
            LogCatcher.i("DependencyResolver", "  应用版本锁定: ${dep.getCoordinates()} -> $managedVersion")
            dep.copy(version = managedVersion)
        } else {
            dep
        }
    }

    /**
     * 检查依赖是否被排除
     */
    private fun isExcluded(dep: MavenDependency, exclusions: List<Exclusion>): Boolean {
        return exclusions.any { it.matches(dep.groupId, dep.artifactId) }
    }

    /**
     * 获取依赖树（用于调试）
     */
    fun getDependencyTree(dependencies: List<MavenDependency>): String {
        val sb = StringBuilder()
        sb.appendLine("========== 依赖树（已仲裁，共${dependencies.size}个）==========")
        
        dependencies.forEachIndexed { index, dep ->
            sb.appendLine("${index + 1}. ${dep.getCoordinates()} (${dep.packaging}, scope=${dep.scope})")
            if (dep.exclusions.isNotEmpty()) {
                sb.appendLine("   排除: ${dep.exclusions.joinToString { 
                    if (it.artifactId == null) "${it.groupId}:*" else "${it.groupId}:${it.artifactId}"
                }}")
            }
        }
        
        sb.appendLine("=================================================")
        return sb.toString()
    }
}
