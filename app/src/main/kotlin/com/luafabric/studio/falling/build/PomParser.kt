package com.luafabric.studio.falling.build.maven

import muling.views.tool.utils.LogCatcher
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * POM文件解析器
 */
class PomParser {

    private val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true  // 关键：启用命名空间感知
        isIgnoringComments = true
        // 禁用DTD验证，避免网络请求和潜在的安全问题
        try {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        } catch (e: Exception) {
            LogCatcher.w("PomParser", "无法设置XML安全特性: ${e.message}")
        }
    }

    /**
     * POM解析结果
     */
    data class PomInfo(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val packaging: String = "aar",
        val parent: ParentInfo? = null,
        val dependencies: List<MavenDependency> = emptyList(),
        val dependencyManagement: List<MavenDependency> = emptyList(),
        val properties: Map<String, String> = emptyMap(),
        val repositories: List<MavenRepository> = emptyList()
    )

    data class ParentInfo(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val relativePath: String? = null
    )

    /**
     * 解析POM文件
     */
    fun parse(pomFile: File): PomInfo? {
        if (!pomFile.exists()) {
            LogCatcher.w("PomParser", "POM文件不存在: ${pomFile.absolutePath}")
            return null
        }

        return try {
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(pomFile)
            doc.documentElement.normalize()

            val projectElement = doc.documentElement

            // 调试信息：打印根节点信息
            LogCatcher.d("PomParser", "根节点: ${projectElement.nodeName}, 命名空间: ${projectElement.namespaceURI}")

            // 解析properties（用于变量替换）
            val properties = parseProperties(projectElement)

            // 解析parent
            val parent = parseParent(projectElement, properties)

            // 确定groupId, artifactId, version
            val groupId = resolveGroupId(projectElement, parent, properties)
            val artifactId = getTextContent(projectElement, "artifactId") ?: ""
            val version = resolveVersion(projectElement, parent, properties)
            val packaging = getTextContent(projectElement, "packaging") ?: "aar"

            // 解析 dependencyManagement 和 dependencies
            val dependencyManagement = parseDependencyManagement(projectElement, properties)
            val dependencies = parseDependencies(projectElement, properties)

            LogCatcher.i("PomParser", "解析完成: $groupId:$artifactId:$version")
            LogCatcher.i("PomParser", "  - dependencyManagement: ${dependencyManagement.size} 个")
            LogCatcher.i("PomParser", "  - dependencies: ${dependencies.size} 个")

            // 关键修复：合并依赖，优先使用显式声明的dependencies
            // 如果dependencies为空但dependencyManagement有内容，使用dependencyManagement
            val effectiveDependencies = if (dependencies.isNotEmpty()) {
                dependencies
            } else if (dependencyManagement.isNotEmpty()) {
                LogCatcher.w("PomParser", "显式dependencies为空，使用dependencyManagement作为有效依赖")
                dependencyManagement
            } else {
                emptyList()
            }

            // 解析仓库
            val repositories = parseRepositories(projectElement)

            PomInfo(
                groupId = groupId,
                artifactId = artifactId,
                version = version,
                packaging = packaging,
                parent = parent,
                dependencies = effectiveDependencies,
                dependencyManagement = dependencyManagement,
                properties = properties,
                repositories = repositories
            )
        } catch (e: Exception) {
            LogCatcher.e("PomParser", "解析POM失败: ${pomFile.absolutePath}", e)
            null
        }
    }

    private fun parseProperties(projectElement: Element): Map<String, String> {
        val properties = mutableMapOf<String, String>()
        
        val propsNode = getChildElement(projectElement, "properties")
        if (propsNode != null) {
            var child = propsNode.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val key = child.nodeName
                    val value = child.textContent?.trim() ?: ""
                    properties[key] = value
                    LogCatcher.d("PomParser", "  属性: $key = $value")
                }
                child = child.nextSibling
            }
        }

        // 添加标准属性
        properties["project.groupId"] = properties["groupId"] ?: ""
        properties["project.artifactId"] = properties["artifactId"] ?: ""
        properties["project.version"] = properties["version"] ?: ""

        return properties
    }

    private fun parseParent(projectElement: Element, properties: Map<String, String>): ParentInfo? {
        val parentNode = getChildElement(projectElement, "parent") ?: return null

        return ParentInfo(
            groupId = getTextContent(parentNode, "groupId")?.let { resolveProperty(it, properties) } ?: "",
            artifactId = getTextContent(parentNode, "artifactId") ?: "",
            version = getTextContent(parentNode, "version")?.let { resolveProperty(it, properties) } ?: "",
            relativePath = getTextContent(parentNode, "relativePath")
        )
    }

    private fun resolveGroupId(
        projectElement: Element,
        parent: ParentInfo?,
        properties: Map<String, String>
    ): String {
        val groupId = getTextContent(projectElement, "groupId")?.let { resolveProperty(it, properties) }
        return groupId ?: parent?.groupId ?: ""
    }

    private fun resolveVersion(
        projectElement: Element,
        parent: ParentInfo?,
        properties: Map<String, String>
    ): String {
        val version = getTextContent(projectElement, "version")?.let { resolveProperty(it, properties) }
        return version ?: parent?.version ?: ""
    }

    /**
     * 解析 dependencyManagement 节点
     */
    private fun parseDependencyManagement(
        projectElement: Element,
        properties: Map<String, String>
    ): List<MavenDependency> {
        val dmNode = getChildElement(projectElement, "dependencyManagement") ?: return emptyList()
        val depsNode = getChildElement(dmNode, "dependencies") ?: return emptyList()
        
        return parseDependencyList(depsNode, properties, "dependencyManagement")
    }

    /**
     * 解析 dependencies 节点
     */
    private fun parseDependencies(
        projectElement: Element,
        properties: Map<String, String>
    ): List<MavenDependency> {
        val depsNode = getChildElement(projectElement, "dependencies") ?: return emptyList()
        return parseDependencyList(depsNode, properties, "dependencies")
    }

    /**
     * 关键修复：使用多种策略解析依赖列表，确保能处理带命名空间的XML
     */
    private fun parseDependencyList(
        depsNode: Element, 
        properties: Map<String, String>,
        context: String = "dependencies"
    ): List<MavenDependency> {
        val dependencies = mutableListOf<MavenDependency>()
        val namespaceURI = depsNode.namespaceURI ?: "http://maven.apache.org/POM/4.0.0"

        // 策略1：使用命名空间感知的 getElementsByTagNameNS
        var depNodes = depsNode.getElementsByTagNameNS(namespaceURI, "dependency")
        LogCatcher.d("PomParser", "[$context] 策略1 (NS=$namespaceURI) 找到 ${depNodes.length} 个依赖")

        // 策略2：如果策略1失败，尝试不带命名空间
        if (depNodes.length == 0) {
            depNodes = depsNode.getElementsByTagName("dependency")
            LogCatcher.d("PomParser", "[$context] 策略2 (无NS) 找到 ${depNodes.length} 个依赖")
        }

        // 策略3：手动遍历所有子节点（兜底方案）
        if (depNodes.length == 0) {
            LogCatcher.d("PomParser", "[$context] 策略3 (手动遍历) 开始...")
            var child = depsNode.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val localName = child.nodeName.substringAfter(":")
                    if (localName == "dependency" || child.nodeName == "dependency") {
                        val depElement = child as Element
                        parseDependencyElement(depElement, properties)?.let {
                            dependencies.add(it)
                            LogCatcher.d("PomParser", "  手动解析依赖[${dependencies.size}]: ${it.getCoordinates()}")
                        }
                    }
                }
                child = child.nextSibling
            }
        } else {
            // 使用策略1或2找到的节点
            for (i in 0 until depNodes.length) {
                val node = depNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val depElement = node as Element
                    parseDependencyElement(depElement, properties)?.let {
                        dependencies.add(it)
                        LogCatcher.d("PomParser", "  解析依赖[${dependencies.size}]: ${it.getCoordinates()} (scope=${it.scope})")
                    }
                }
            }
        }

        LogCatcher.i("PomParser", "[$context] 共解析 ${dependencies.size} 个依赖")
        return dependencies
    }

    /**
     * 关键修复：更健壮的依赖节点解析，处理命名空间
     */
    private fun parseDependencyElement(depElement: Element, properties: Map<String, String>): MavenDependency? {
        val namespaceURI = depElement.namespaceURI ?: "http://maven.apache.org/POM/4.0.0"

        // 辅助函数：获取元素文本，尝试多种方式
        fun getElementText(elem: Element, tag: String): String? {
            // 方式1：使用命名空间
            var nodes = elem.getElementsByTagNameNS(namespaceURI, tag)
            
            // 方式2：不使用命名空间
            if (nodes.length == 0) {
                nodes = elem.getElementsByTagName(tag)
            }
            
            // 方式3：手动遍历
            if (nodes.length == 0) {
                var child = elem.firstChild
                while (child != null) {
                    if (child.nodeType == Node.ELEMENT_NODE) {
                        val localName = child.nodeName.substringAfter(":")
                        if (localName == tag || child.nodeName == tag) {
                            return child.textContent?.trim()?.let { resolveProperty(it, properties) }
                        }
                    }
                    child = child.nextSibling
                }
                return null
            }
            
            return nodes.item(0)?.textContent?.trim()?.let { resolveProperty(it, properties) }
        }

        val groupId = getElementText(depElement, "groupId") 
            ?: return null.also { LogCatcher.w("PomParser", "  依赖缺少 groupId") }
            
        val artifactId = getElementText(depElement, "artifactId") 
            ?: return null.also { LogCatcher.w("PomParser", "  依赖缺少 artifactId") }
            
        val version = getElementText(depElement, "version") ?: ""
        val classifier = getElementText(depElement, "classifier")
        val type = getElementText(depElement, "type") ?: "jar"
        val scope = getElementText(depElement, "scope") ?: "compile"
        val optionalStr = getElementText(depElement, "optional")
        val optional = optionalStr?.toBoolean() ?: false

        // 解析exclusions
        val exclusions = parseExclusions(depElement, namespaceURI)

        return MavenDependency(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            classifier = classifier,
            packaging = type,
            scope = scope,
            optional = optional,
            exclusions = exclusions
        )
    }

    /**
     * 解析排除规则
     */
    private fun parseExclusions(depElement: Element, namespaceURI: String): List<Exclusion> {
        val exclusionsNode = getChildElement(depElement, "exclusions") ?: return emptyList()
        val result = mutableListOf<Exclusion>()

        // 尝试多种方式获取exclusion节点
        var excNodes = exclusionsNode.getElementsByTagNameNS(namespaceURI, "exclusion")
        if (excNodes.length == 0) {
            excNodes = exclusionsNode.getElementsByTagName("exclusion")
        }

        for (i in 0 until excNodes.length) {
            val node = excNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val excElement = node as Element
                
                fun getText(tag: String): String? {
                    var nodes = excElement.getElementsByTagNameNS(namespaceURI, tag)
                    if (nodes.length == 0) nodes = excElement.getElementsByTagName(tag)
                    return nodes.item(0)?.textContent?.trim()
                }
                
                val groupId = getText("groupId") ?: "*"
                val artifactId = getText("artifactId")
                result.add(Exclusion(groupId, artifactId))
            }
        }

        return result
    }

    /**
     * 解析仓库配置
     */
    private fun parseRepositories(projectElement: Element): List<MavenRepository> {
        val reposNode = getChildElement(projectElement, "repositories") ?: return emptyList()
        val result = mutableListOf<MavenRepository>()
        val namespaceURI = projectElement.namespaceURI ?: "http://maven.apache.org/POM/4.0.0"

        var repoNodes = reposNode.getElementsByTagNameNS(namespaceURI, "repository")
        if (repoNodes.length == 0) {
            repoNodes = reposNode.getElementsByTagName("repository")
        }

        for (i in 0 until repoNodes.length) {
            val node = repoNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val repoElement = node as Element
                
                fun getText(tag: String): String? {
                    var nodes = repoElement.getElementsByTagNameNS(namespaceURI, tag)
                    if (nodes.length == 0) nodes = repoElement.getElementsByTagName(tag)
                    return nodes.item(0)?.textContent?.trim()
                }
                
                val name = getText("id") ?: getText("name") ?: "unknown"
                val url = getText("url") ?: continue
                
                result.add(MavenRepository(name, url))
            }
        }

        return result
    }

    /**
     * 解析属性占位符 ${...}
     */
    private fun resolveProperty(value: String, properties: Map<String, String>): String {
        if (!value.contains("\${")) return value
        
        var result = value
        val pattern = "\\$\\{([^}]+)\\}".toRegex()

        // 处理${project.xxx}等特殊变量
        result = result.replace("\${project.groupId}", properties["project.groupId"] ?: "")
        result = result.replace("\${project.artifactId}", properties["project.artifactId"] ?: "")
        result = result.replace("\${project.version}", properties["project.version"] ?: "")
        result = result.replace("\${pom.groupId}", properties["project.groupId"] ?: "")
        result = result.replace("\${pom.artifactId}", properties["project.artifactId"] ?: "")
        result = result.replace("\${pom.version}", properties["project.version"] ?: "")

        // 处理普通属性（循环处理嵌套属性）
        var prevResult: String
        var iterations = 0
        do {
            prevResult = result
            val match = pattern.find(result)
            if (match != null) {
                val propName = match.groupValues[1]
                val propValue = properties[propName] 
                    ?: System.getProperty(propName) 
                    ?: ""
                result = result.replace(match.value, propValue)
            }
            iterations++
        } while (result != prevResult && iterations < 10 && result.contains("\${"))

        return result
    }

    /**
     * 获取子元素，尝试多种策略
     */
    private fun getChildElement(parent: Element, tagName: String): Element? {
        val namespaceURI = parent.namespaceURI ?: "http://maven.apache.org/POM/4.0.0"
        
        // 策略1：使用命名空间
        var elements = parent.getElementsByTagNameNS(namespaceURI, tagName)
        if (elements.length > 0) {
            return elements.item(0) as? Element
        }
        
        // 策略2：不使用命名空间
        elements = parent.getElementsByTagName(tagName)
        if (elements.length > 0) {
            return elements.item(0) as? Element
        }
        
        // 策略3：手动遍历
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val localName = child.nodeName.substringAfter(":")
                if (localName == tagName || child.nodeName == tagName) {
                    return child as Element
                }
            }
            child = child.nextSibling
        }
        
        return null
    }

    /**
     * 获取元素文本内容
     */
    private fun getTextContent(parent: Element, tagName: String): String? {
        val child = getChildElement(parent, tagName) ?: return null
        return child.textContent?.trim()
    }
}
