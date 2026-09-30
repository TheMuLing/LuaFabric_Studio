package com.luafabric.studio.falling.ui.login

import android.content.Context
import com.google.gson.Gson
import com.luafabric.studio.falling.native.YunJuBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 云居账号登录/签到仓库。所有请求一律走 native 层（yunju.c，套 VPN/代理门控），
 * 响应体已由 native 裁剪为纯 JSON。网络失败/被门控 native 返回 null → 抛 IOException，
 * 与旧 OkHttp 行为一致（调用方 catch 网络异常）。
 */
object LoginRepository {
    private val gson = Gson()

    /** native 返回 null（门控/网络失败）时抛异常，保持调用方既有 try-catch 语义 */
    private fun err() = IOException("native request failed")

    /**
     * 裁剪 native 返回为纯 JSON body（取首个 '{' 至末个 '}'），
     * 去除尾部可能残留的 chunked 终结符（\r\n0\r\n\r\n）等下行字节，与论坛侧一致。
     */
    private fun extractJson(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var body = raw.substring(start)
        val end = body.lastIndexOf('}')
        if (end >= 0) body = body.substring(0, end + 1)
        return body
    }

    /** POST user_dl.php 登录。 */
    suspend fun login(context: Context, qq: String, pass: String): LoginResult =
        withContext(Dispatchers.IO) {
            val raw = YunJuBridge.nativeLogin(context, qq, pass) ?: throw err()
            val json = extractJson(raw) ?: throw err()
            val parsed = runCatching { gson.fromJson(json, YunJuResponse::class.java) }.getOrNull()
                ?: return@withContext LoginResult(false, null, "", null)
            when (parsed.code) {
                "1" -> LoginResult(true, parsed, parsed.msg, parsed.code)
                else -> LoginResult(false, null, parsed.msg.ifBlank { "登录失败(${parsed.code})" }, parsed.code)
            }
        }

    /** POST user_qiandao.php 签到。成功返回 (true, msg)，失败 (false, msg)。 */
    suspend fun signIn(context: Context, qq: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val raw = YunJuBridge.nativeSignIn(context, qq) ?: throw err()
            val json = extractJson(raw) ?: throw err()
            val parsed = runCatching { gson.fromJson(json, YunJuResponse::class.java) }.getOrNull()
                ?: return@withContext (false to "")
            if (parsed.code == "1") {
                true to parsed.msg
            } else {
                false to parsed.msg.ifBlank { "签到失败(${parsed.code})" }
            }
        }

    /** POST user_yhxx.php 拉取用户实时信息（金币/经验/等级等）。请求/解析失败返回 null（不抛异常）。 */
    suspend fun fetchUserInfo(context: Context, qq: String): YunJuResponse? =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = YunJuBridge.nativeFetchUserInfo(context, qq) ?: return@withContext null
                val json = extractJson(raw) ?: return@withContext null
                val parsed = runCatching { gson.fromJson(json, YunJuResponse::class.java) }.getOrNull()
                    ?: return@withContext null
                if (parsed.code == "1") parsed else null
            }.getOrNull()
        }

    /** POST user_azc.php 注册。email 由调用方拼接（QQ号@qq.com）。 */
    suspend fun register(
        context: Context,
        qq: String,
        pass: String,
        nickname: String,
        email: String,
        code: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val raw = YunJuBridge.nativeRegister(context, qq, pass, qq, nickname, email, code)
            ?: throw err()
        val json = extractJson(raw) ?: throw err()
        val parsed = runCatching { gson.fromJson(json, YunJuResponse::class.java) }.getOrNull()
            ?: return@withContext (false to "")
        if (parsed.code == "1") {
            true to parsed.msg
        } else {
            false to parsed.msg.ifBlank { "注册失败(${parsed.code})" }
        }
    }

    /** POST user_yzm.php 发送注册验证码到邮箱。 */
    suspend fun sendCode(context: Context, email: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val raw = YunJuBridge.nativeSendCode(context, email) ?: throw err()
            val json = extractJson(raw) ?: throw err()
            val parsed = runCatching { gson.fromJson(json, YunJuResponse::class.java) }.getOrNull()
                ?: return@withContext (false to "")
            if (parsed.code == "1") {
                true to parsed.msg
            } else {
                false to parsed.msg.ifBlank { "发送失败(${parsed.code})" }
            }
        }

    /** POST user_zhmm.php 找回密码（密码发往邮箱）。 */
    suspend fun findPassword(context: Context, email: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val raw = YunJuBridge.nativeFindPassword(context, email) ?: throw err()
            val json = extractJson(raw) ?: throw err()
            val parsed = runCatching { gson.fromJson(json, YunJuResponse::class.java) }.getOrNull()
                ?: return@withContext (false to "")
            if (parsed.code == "1") {
                true to parsed.msg
            } else {
                false to parsed.msg.ifBlank { "找回失败(${parsed.code})" }
            }
        }
}