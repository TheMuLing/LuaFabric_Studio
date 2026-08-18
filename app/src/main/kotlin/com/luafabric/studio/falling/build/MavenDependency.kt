package com.luafabric.studio.falling.build.maven

/**
 * Maven依赖数据类
 */
data class MavenDependency(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifier: String? = null,
    val packaging: String = "aar", // 默认尝试aar
    val scope: String = "compile",
    val optional: Boolean = false,
    val exclusions: List<Exclusion> = emptyList()
) {
    /**
     * 获取依赖的唯一标识
     */
    fun getCoordinates(): String = "$groupId:$artifactId:$version"

    /**
     * 获取带classifier的坐标
     */
    fun getCoordinatesWithClassifier(): String {
        return if (classifier != null) {
            "$groupId:$artifactId:$version:$classifier"
        } else {
            getCoordinates()
        }
    }

    /**
     * 获取本地缓存目录名
     */
    fun getCacheDirName(): String {
        return "${groupId.replace('.', '/')}/$artifactId/$version"
    }

    /**
     * 获取基础文件名（不含扩展名）
     */
    fun getBaseFileName(): String {
        return "$artifactId-$version"
    }

    /**
     * 获取带classifier的基础文件名
     */
    fun getFileNameWithClassifier(): String {
        return if (classifier != null) {
            "$artifactId-$version-$classifier"
        } else {
            getBaseFileName()
        }
    }

    /**
     * 检查是否是动态版本
     */
    fun isDynamicVersion(): Boolean {
        return version.contains("+") || 
               version.endsWith("-SNAPSHOT") ||
               version.contains("[") || 
               version.contains("(")
    }

    companion object {
        /**
         * 从字符串解析依赖，格式: groupId:artifactId:version[:classifier][@packaging]
         */
        fun parse(dependencyString: String): MavenDependency? {
            return try {
                var str = dependencyString.trim()
                var packaging = "aar"
                
                // 检查是否有@packaging后缀
                val atIndex = str.lastIndexOf('@')
                if (atIndex > 0) {
                    packaging = str.substring(atIndex + 1)
                    str = str.substring(0, atIndex)
                }

                val parts = str.split(':')
                when (parts.size) {
                    3 -> MavenDependency(
                        groupId = parts[0],
                        artifactId = parts[1],
                        version = parts[2],
                        packaging = packaging
                    )
                    4 -> MavenDependency(
                        groupId = parts[0],
                        artifactId = parts[1],
                        version = parts[2],
                        classifier = parts[3],
                        packaging = packaging
                    )
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 依赖排除规则
 */
data class Exclusion(
    val groupId: String,
    val artifactId: String? = null
) {
    fun matches(groupId: String, artifactId: String): Boolean {
        if (this.groupId != groupId) return false
        if (this.artifactId == null) return true // 排除整个group
        return this.artifactId == artifactId
    }
}
