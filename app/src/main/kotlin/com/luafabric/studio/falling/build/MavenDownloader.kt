package com.luafabric.studio.falling.build.maven

import android.content.Context
import muling.views.tool.utils.LogCatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class MavenDownloader(private val context: Context) {

    private val mavenCacheDir: File by lazy {
        File(context.externalCacheDir, "maven_repo").apply {
            if (!exists()) mkdirs()
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    interface DownloadCallback {
        fun onProgress(currentFile: String, currentIndex: Int, totalFiles: Int)
        fun onDownloadedBytes(downloaded: Long, total: Long)
        fun onComplete(success: Boolean, message: String)
    }

    suspend fun downloadDependency(
        dependency: MavenDependency,
        repositories: List<MavenRepository>,
        callback: DownloadCallback? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        
        val cachedFiles = findCachedFiles(dependency)
        if (cachedFiles.isNotEmpty()) {
            val hasJar = cachedFiles.any { it.name.endsWith(".jar") || it.name.endsWith(".aar") }
            if (hasJar || cachedFiles.size == 1) {
                LogCatcher.i("MavenDownloader", "使用缓存: ${dependency.getCoordinates()}")
                return@withContext DownloadResult.Success(cachedFiles)
            }
        }

        for (repo in repositories) {
            LogCatcher.i("MavenDownloader", "尝试仓库 ${repo.name}: ${dependency.getCoordinates()}")
            
            val result = withTimeoutOrNull(20000) {
                tryDownloadFromRepository(dependency, repo, callback)
            } ?: DownloadResult.Failure("仓库 ${repo.name} 超时(20s)")
            
            if (result is DownloadResult.Success) {
                return@withContext result
            } else {
                val reason = (result as? DownloadResult.Failure)?.reason ?: "失败"
                LogCatcher.w("MavenDownloader", "仓库 ${repo.name} 失败: $reason")
            }
        }

        val pomOnly = findPomFileOnly(dependency)
        if (pomOnly != null) {
            LogCatcher.w("MavenDownloader", "所有仓库失败，但本地有POM: ${dependency.getCoordinates()}")
            return@withContext DownloadResult.Success(listOf(pomOnly))
        }

        DownloadResult.Failure("所有仓库都无法下载: ${dependency.getCoordinates()}")
    }

    private suspend fun tryDownloadFromRepository(
        dependency: MavenDependency,
        repository: MavenRepository,
        callback: DownloadCallback?
    ): DownloadResult = withContext(Dispatchers.IO) {
        
        try {
            val resolvedVersion = if (dependency.isDynamicVersion()) {
                resolveDynamicVersion(dependency, repository) ?: dependency.version
            } else {
                dependency.version
            }

            val resolvedDep = dependency.copy(version = resolvedVersion)
            val cacheSubDir = File(mavenCacheDir, resolvedDep.getCacheDirName()).apply { mkdirs() }

            val pomFileName = "${resolvedDep.getFileNameWithClassifier()}.pom"
            val pomFile = File(cacheSubDir, pomFileName)
            
            if (!pomFile.exists() || pomFile.length() <= 0L) {
                val pomUrl = buildArtifactUrl(repository, resolvedDep, "pom")
                val pomSuccess = downloadFileWithOkHttp(pomUrl, pomFile, callback)
                if (!pomSuccess) {
                    return@withContext DownloadResult.Failure("POM下载失败")
                }
            }

            val pomParser = PomParser()
            val pomInfo = pomParser.parse(pomFile)
            val packaging = pomInfo?.packaging ?: resolvedDep.packaging

            if (packaging == "pom" || packaging == "bom") {
                return@withContext DownloadResult.Success(listOf(pomFile))
            }

            val downloadedFiles = mutableListOf<File>()
            downloadedFiles.add(pomFile)

            val isKnownJar = packaging == "jar" || 
                           resolvedDep.groupId.contains("errorprone") || 
                           resolvedDep.groupId.contains("jetbrains") || 
                           resolvedDep.groupId.contains("google.code")
            
            val artifactFile = if (!isKnownJar) {
                downloadArtifactOnce(resolvedDep, repository, "aar", callback)
                    ?: downloadArtifactOnce(resolvedDep, repository, "jar", callback)
            } else {
                downloadArtifactOnce(resolvedDep, repository, "jar", callback)
            }

            if (artifactFile != null) {
                downloadedFiles.add(artifactFile)
            } else {
                LogCatcher.w("MavenDownloader", "构件下载失败，但保留POM: ${resolvedDep.getCoordinates()}")
            }

            DownloadResult.Success(downloadedFiles)

        } catch (e: Exception) {
            DownloadResult.Failure(e.message ?: "未知错误")
        }
    }

    private suspend fun downloadArtifactOnce(
        dependency: MavenDependency,
        repository: MavenRepository,
        type: String,
        callback: DownloadCallback?
    ): File? = withContext(Dispatchers.IO) {
        val url = buildArtifactUrl(repository, dependency, type)
        val fileName = if (dependency.classifier != null) {
            "${dependency.getFileNameWithClassifier()}.$type"
        } else {
            "${dependency.getBaseFileName()}.$type"
        }
        val cacheSubDir = File(mavenCacheDir, dependency.getCacheDirName()).apply { mkdirs() }
        val file = File(cacheSubDir, fileName)

        if (file.exists() && file.length() > 0L) {
            return@withContext file
        }

        val success = downloadFileWithOkHttp(url, file, callback)
        if (success) file else null
    }

    private suspend fun downloadFileWithOkHttp(
        urlString: String,
        targetFile: File,
        callback: DownloadCallback?
    ): Boolean = withContext(Dispatchers.IO) {
        
        if (targetFile.exists() && targetFile.length() > 0L) {
            return@withContext true
        }

        val request = Request.Builder()
            .url(urlString)
            .header("User-Agent", "LuaFabric-Studio/1.0")
            .build()

        val result = withTimeoutOrNull(15000) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                val call = okHttpClient.newCall(request)
                
                continuation.invokeOnCancellation {
                    call.cancel()
                }

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!continuation.isActive) {
                            response.close()
                            return
                        }

                        if (!response.isSuccessful) {
                            response.close()
                            continuation.resume(false)
                            return
                        }

                        val body = response.body
                        if (body == null) {
                            continuation.resume(false)
                            return
                        }

                        try {
                            targetFile.parentFile?.mkdirs()
                            val totalBytes = body.contentLength()
                            var downloadedBytes = 0L

                            body.byteStream().use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    val buffer = ByteArray(8192)
                                    var bytesRead: Int
                                    
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        if (!continuation.isActive) throw IOException("取消")
                                        output.write(buffer, 0, bytesRead)
                                        downloadedBytes += bytesRead
                                        callback?.onDownloadedBytes(downloadedBytes, totalBytes)
                                    }
                                }
                            }
                            
                            continuation.resume(true)
                        } catch (e: Exception) {
                            targetFile.delete()
                            if (continuation.isActive) continuation.resume(false)
                        } finally {
                            response.close()
                        }
                    }
                })
            }
        }
        
        if (result == null) {
            LogCatcher.w("MavenDownloader", "下载超时(15s): $urlString")
            targetFile.delete()
        }
        
        result ?: false
    }

    private suspend fun resolveDynamicVersion(
        dependency: MavenDependency,
        repository: MavenRepository
    ): String? = withContext(Dispatchers.IO) {
        try {
            val metadataUrl = "${repository.url}/${dependency.groupId.replace('.', '/')}/${dependency.artifactId}/maven-metadata.xml"
            val metadataFile = File.createTempFile("maven-metadata", ".xml")
            
            val success = withTimeoutOrNull(10000) {
                downloadFileWithOkHttp(metadataUrl, metadataFile, null)
            } ?: false
            
            if (!success) {
                metadataFile.delete()
                return@withContext null
            }

            val metadata = metadataFile.readText()
            metadataFile.delete()
            parseVersionFromMetadata(metadata, dependency.version)

        } catch (e: Exception) {
            null
        }
    }

    private fun parseVersionFromMetadata(metadata: String, requestedVersion: String): String? {
        val versioningPattern = "<versioning>.*?</versioning>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val versioning = versioningPattern.find(metadata)?.value ?: return null

        val versionPattern = "<version>([^<]+)</version>".toRegex()
        val versions = versionPattern.findAll(versioning).map { it.groupValues[1] }.toList()

        if (versions.isEmpty()) return null

        return when {
            requestedVersion.endsWith("+") -> {
                val prefix = requestedVersion.removeSuffix("+")
                versions.filter { it.startsWith(prefix) }.maxOrNull() ?: versions.last()
            }
            requestedVersion.contains("[") || requestedVersion.contains("(") -> versions.last()
            else -> versions.find { it == requestedVersion } ?: versions.last()
        }
    }

    private fun buildArtifactUrl(
        repository: MavenRepository,
        dependency: MavenDependency,
        type: String
    ): String {
        val basePath = "${repository.url}/${dependency.groupId.replace('.', '/')}/${dependency.artifactId}/${dependency.version}"
        val fileName = if (dependency.classifier != null) {
            "${dependency.artifactId}-${dependency.version}-${dependency.classifier}.$type"
        } else {
            "${dependency.artifactId}-${dependency.version}.$type"
        }
        return "$basePath/$fileName"
    }

    /**
     * 查找缓存文件（改为public以便外部访问）
     */
    fun findCachedFiles(dependency: MavenDependency): List<File> {
        val cacheDir = File(mavenCacheDir, dependency.getCacheDirName())
        if (!cacheDir.exists()) return emptyList()

        val baseName = dependency.getFileNameWithClassifier()
        val files = mutableListOf<File>()

        val pomFile = File(cacheDir, "$baseName.pom")
        if (!pomFile.exists() || pomFile.length() <= 0L) return emptyList()
        files.add(pomFile)

        val aarFile = File(cacheDir, "$baseName.aar")
        val jarFile = File(cacheDir, "$baseName.jar")
        
        when {
            aarFile.exists() && aarFile.length() > 0L -> files.add(aarFile)
            jarFile.exists() && jarFile.length() > 0L -> files.add(jarFile)
        }

        return files
    }
    
    /**
     * 并发批量下载多个依赖
     */
    suspend fun downloadDependencies(
        dependencies: List<MavenDependency>,
        repositories: List<MavenRepository>,
        callback: DownloadCallback? = null
    ): Map<MavenDependency, DownloadResult> = withContext(Dispatchers.IO) {
        
        val deferreds = dependencies.mapIndexed { index, dep ->
            async {
                callback?.onProgress(dep.getCoordinates(), index + 1, dependencies.size)
                dep to downloadDependency(dep, repositories, callback)
            }
        }
        
        val results = deferreds.awaitAll().toMap()
        
        callback?.onComplete(
            results.values.all { it is DownloadResult.Success }, 
            "下载完成: ${results.size}个依赖"
        )
        
        results
    }

    private fun findPomFileOnly(dependency: MavenDependency): File? {
        val cacheDir = File(mavenCacheDir, dependency.getCacheDirName())
        val pomFile = File(cacheDir, "${dependency.getFileNameWithClassifier()}.pom")
        return if (pomFile.exists() && pomFile.length() > 0L) pomFile else null
    }

    fun getCacheDir(): File = mavenCacheDir

    fun clearCache(): Boolean {
        return mavenCacheDir.deleteRecursively()
    }

    sealed class DownloadResult {
        data class Success(val files: List<File>) : DownloadResult()
        data class Failure(val reason: String) : DownloadResult()
    }
}
