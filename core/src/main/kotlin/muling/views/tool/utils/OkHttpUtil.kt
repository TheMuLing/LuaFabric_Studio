package muling.views.tool.utils

import android.content.Context
import androidx.annotation.Keep
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

@Keep
object OkHttpUtil {

    data class HttpResponse(
        val code: Int,
        val content: String,
        val cookie: String,
        val headers: Map<String, List<String>>
    )

    private var client: OkHttpClient? = null

    // 初始化OkHttpClient
    private fun getClient(): OkHttpClient {
        if (client == null) {
            client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
        return client!!
    }

    // ========== GET请求 ==========

    @JvmStatic
    fun get(
        context: Context,
        url: String,
        cookie: String? = null,
        charset: String? = null,
        headers: Map<String, String>? = null
    ): HttpResponse {
        return try {
            val requestBuilder = Request.Builder().url(url)

            // 添加自定义请求头
            headers?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            // 添加Cookie
            cookie?.let { requestBuilder.addHeader("Cookie", it) }

            // 设置Charset
            if (charset != null) {
                requestBuilder.addHeader("Accept-Charset", charset)
            }

            val request = requestBuilder.build()
            val response = getClient().newCall(request).execute()

            // 提取Cookie
            val responseCookies = response.headers("Set-Cookie")
            val responseCookie = responseCookies.joinToString("; ")

            // 获取响应内容
            val content = if (charset != null) {
                response.body?.bytes()?.toString(charset(charset)) ?: ""
            } else {
                response.body?.string() ?: ""
            }

            // 获取响应头
            val responseHeaders = response.headers.toMultimap()

            HttpResponse(response.code, content, responseCookie, responseHeaders)
        } catch (e: Exception) {
            HttpResponse(-1, "Error: ${e.message}", "", emptyMap())
        }
    }

    // ========== POST请求（表单提交） ==========

    @JvmStatic
    fun post(
        context: Context,
        url: String,
        formData: Map<String, String>? = null,  // 允许空表
        cookie: String? = null,
        charset: String? = null,
        headers: Map<String, String>? = null
    ): HttpResponse {
        return try {
            val formBodyBuilder = FormBody.Builder()

            // 添加表单数据（允许空表）
            formData?.forEach { (key, value) ->
                formBodyBuilder.add(key, value)
            }

            val formBody = formBodyBuilder.build()
            val requestBuilder = Request.Builder()
                .url(url)
                .post(formBody)

            // 添加自定义请求头
            headers?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            // 添加Cookie
            cookie?.let { requestBuilder.addHeader("Cookie", it) }

            // 设置Charset
            if (charset != null) {
                requestBuilder.addHeader("Accept-Charset", charset)
            }

            val request = requestBuilder.build()
            val response = getClient().newCall(request).execute()

            // 提取Cookie
            val responseCookies = response.headers("Set-Cookie")
            val responseCookie = responseCookies.joinToString("; ")

            // 获取响应内容
            val content = if (charset != null) {
                response.body?.bytes()?.toString(charset(charset)) ?: ""
            } else {
                response.body?.string() ?: ""
            }

            // 获取响应头
            val responseHeaders = response.headers.toMultimap()

            HttpResponse(response.code, content, responseCookie, responseHeaders)
        } catch (e: Exception) {
            HttpResponse(-1, "Error: ${e.message}", "", emptyMap())
        }
    }

    // ========== 文件上传 ==========

    @JvmStatic
    fun upload(
        context: Context,
        url: String,
        filePath: String,
        cookie: String? = null,
        headers: Map<String, String>? = null,
        fieldName: String = "file",
        extraParams: Map<String, String>? = null
    ): HttpResponse {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return HttpResponse(-1, "Error: File not found", "", emptyMap())
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    fieldName,
                    file.name,
                    file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                )

            // 添加额外参数
            extraParams?.forEach { (key, value) ->
                requestBody.addFormDataPart(key, value)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody.build())

            // 添加自定义请求头
            headers?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            // 添加Cookie
            cookie?.let { requestBuilder.addHeader("Cookie", it) }

            val request = requestBuilder.build()
            val response = getClient().newCall(request).execute()

            // 提取Cookie
            val responseCookies = response.headers("Set-Cookie")
            val responseCookie = responseCookies.joinToString("; ")

            // 获取响应内容
            val content = response.body?.string() ?: ""

            // 获取响应头
            val responseHeaders = response.headers.toMultimap()

            HttpResponse(response.code, content, responseCookie, responseHeaders)
        } catch (e: Exception) {
            HttpResponse(-1, "Error: ${e.message}", "", emptyMap())
        }
    }

    // ========== 文件下载 ==========

    @JvmStatic
    fun download(
        context: Context,
        url: String,
        savePath: String,
        cookie: String? = null,
        headers: Map<String, String>? = null
    ): HttpResponse {
        return try {
            val requestBuilder = Request.Builder().url(url)

            // 添加自定义请求头
            headers?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            // 添加Cookie
            cookie?.let { requestBuilder.addHeader("Cookie", it) }

            val request = requestBuilder.build()
            val response = getClient().newCall(request).execute()

            if (response.isSuccessful) {
                val file = File(savePath)
                file.parentFile?.mkdirs()

                response.body?.byteStream()?.use { inputStream ->
                    file.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // 提取Cookie
                val responseCookies = response.headers("Set-Cookie")
                val responseCookie = responseCookies.joinToString("; ")

                // 获取响应头
                val responseHeaders = response.headers.toMultimap()

                HttpResponse(response.code, "Download success", responseCookie, responseHeaders)
            } else {
                HttpResponse(response.code, "Download failed: ${response.code}", "", emptyMap())
            }
        } catch (e: Exception) {
            HttpResponse(-1, "Error: ${e.message}", "", emptyMap())
        }
    }

    // ========== 辅助方法 ==========

    private fun charset(charsetName: String): java.nio.charset.Charset {
        return try {
            java.nio.charset.Charset.forName(charsetName)
        } catch (e: Exception) {
            java.nio.charset.StandardCharsets.UTF_8
        }
    }
}