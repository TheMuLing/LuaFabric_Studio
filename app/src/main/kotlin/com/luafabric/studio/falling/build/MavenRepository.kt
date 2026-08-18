package com.luafabric.studio.falling.build.maven

/**
 * Maven仓库配置
 */
data class MavenRepository(
    val name: String,
    val url: String,
    val priority: Int = 0, // 优先级，数字越小优先级越高
    val allowInsecureProtocol: Boolean = false // 是否允许HTTP连接
) {
    companion object {
        /**
         * 默认仓库列表（按优先级排序）
         * 根据您提供的 settings.gradle 配置整理
         */
        fun getDefaultRepositories(): List<MavenRepository> = listOf(
            MavenRepository("aliyun-public", "https://maven.aliyun.com/repository/public", 0),
            MavenRepository("aliyun-google", "https://maven.aliyun.com/repository/google", 1),
            MavenRepository("aliyun-gradle-plugin", "https://maven.aliyun.com/repository/gradle-plugin", 2),
            MavenRepository("bintray-ppartisan", "https://dl.bintray.com/ppartisan/maven/", 3),
            MavenRepository("clojars", "https://clojars.org/repo", 4),
            MavenRepository("jitpack", "https://jitpack.io", 5),
            MavenRepository("4thline", "http://4thline.org/m2", 6, allowInsecureProtocol = true),
            MavenRepository("gradle-plugin-portal", "https://plugins.gradle.org/m2", 7),
            MavenRepository("google", "https://maven.google.com", 8),
            MavenRepository("maven-central", "https://repo1.maven.org/maven2", 9)
        )

        /**
         * 从settings.json解析仓库配置
         * 支持两种格式：
         * - 字符串："https://example.com/repo"
         * - 对象：{"name": "myrepo", "url": "https://example.com/repo", "allowInsecure": true}
         */
        fun parseRepositories(repos: List<*>?): List<MavenRepository> {
            val result = getDefaultRepositories().toMutableList()

            repos?.forEach { repo ->
                when (repo) {
                    is String -> {
                        // 简单URL字符串，自动生成名称并追加到列表末尾
                        result.add(MavenRepository("custom_${result.size}", repo, result.size))
                    }
                    is Map<*, *> -> {
                        val name = repo["name"] as? String ?: "custom_${result.size}"
                        val url = repo["url"] as? String
                        if (url != null) {
                            val allowInsecure = repo["allowInsecure"] as? Boolean ?: false
                            result.add(MavenRepository(name, url, result.size, allowInsecure))
                        }
                    }
                }
            }

            // 按优先级排序返回
            return result.sortedBy { it.priority }
        }
    }
}