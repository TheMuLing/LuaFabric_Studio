package com.luafabric.studio.falling.ui.login

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 云居账号登录/签到仓库。所有请求一律 POST（后端约定）。 */
object LoginRepository {
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /** POST user_dl.php 登录。网络异常向上抛，由调用方决定策略。 */
    suspend fun login(qq: String, pass: String): LoginResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("appid", YunJuApi.APP_ID)
            .add("key", YunJuApi.APP_KEY)
            .add("user", qq)
            .add("pass", pass)
            .build()
        val request = Request.Builder()
            .url(YunJuApi.LOGIN_URL)
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: return@withContext LoginResult(false, null, "")
            val parsed = runCatching { gson.fromJson(text, YunJuResponse::class.java) }.getOrNull()
                ?: return@withContext LoginResult(false, null, "")
            when (parsed.code) {
                "1" -> LoginResult(true, parsed, parsed.msg)
                else -> LoginResult(false, null, parsed.msg.ifBlank { "登录失败(${parsed.code})" })
            }
        }
    }

    /** POST user_qiandao.php 签到。成功返回 (true, msg)，失败 (false, msg)。网络异常向上抛。 */
    suspend fun signIn(qq: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("appid", YunJuApi.APP_ID)
            .add("key", YunJuApi.APP_KEY)
            .add("user", qq)
            .build()
        val request = Request.Builder()
            .url(YunJuApi.SIGN_URL)
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: return@withContext (false to "")
            val parsed = runCatching { gson.fromJson(text, YunJuResponse::class.java) }.getOrNull()
                ?: return@withContext (false to "")
            if (parsed.code == "1") {
                true to parsed.msg
            } else {
                false to parsed.msg.ifBlank { "签到失败(${parsed.code})" }
            }
        }
    }

    /** POST user_azc.php 注册。email 由调用方拼接（QQ号@qq.com）。 */
    suspend fun register(
        qq: String,
        pass: String,
        nickname: String,
        email: String,
        code: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("appid", YunJuApi.APP_ID)
            .add("key", YunJuApi.APP_KEY)
            .add("user", qq)
            .add("pass", pass)
            .add("QQ", qq)
            .add("name", nickname)
            .add("email", email)
            .add("code", code)
            .build()
        val request = Request.Builder()
            .url(YunJuApi.REGISTER_URL)
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: return@withContext (false to "")
            val parsed = runCatching { gson.fromJson(text, YunJuResponse::class.java) }.getOrNull()
                ?: return@withContext (false to "")
            if (parsed.code == "1") {
                true to parsed.msg
            } else {
                false to parsed.msg.ifBlank { "注册失败(${parsed.code})" }
            }
        }
    }

    /** POST user_yzm.php 发送注册验证码到邮箱。 */
    suspend fun sendCode(email: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("appid", YunJuApi.APP_ID)
            .add("key", YunJuApi.APP_KEY)
            .add("email", email)
            .build()
        val request = Request.Builder()
            .url(YunJuApi.SEND_CODE_URL)
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: return@withContext (false to "")
            val parsed = runCatching { gson.fromJson(text, YunJuResponse::class.java) }.getOrNull()
                ?: return@withContext (false to "")
            if (parsed.code == "1") {
                true to parsed.msg
            } else {
                false to parsed.msg.ifBlank { "发送失败(${parsed.code})" }
            }
        }
    }
}
