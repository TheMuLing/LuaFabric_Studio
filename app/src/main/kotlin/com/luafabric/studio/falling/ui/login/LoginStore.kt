package com.luafabric.studio.falling.ui.login

import android.content.Context
import com.google.gson.Gson
import com.luafabric.studio.falling.core.KeystoreCrypto
import com.luafabric.studio.falling.core.StudioMmkv

/**
 * 登录态本地持久化（独立 Studio MMKV id=studio_login，显式私有根 + Keystore 派生密钥）。
 * 敏感值（pass / user_json）在 MMKV 加密之上再套一层 Keystore AES-GCM 封装。
 * 存量 DataStore app_login 按「直接切换，存量作废」由 StudioMmkv 首次初始化删除。
 */
object LoginStore {
    private val gson = Gson()

    private object Keys {
        const val QQ = "qq"
        const val PASS = "pass"
        const val KEEP_LOGGED_IN = "keep_logged_in"
        const val REMEMBER_ACCOUNT = "remember_account"
        const val USER_JSON = "user_json"
        // 180s 冷却：各自独立，存到期时间戳(毫秒)，进程重启后依旧生效
        const val SEND_CODE_UNTIL = "send_code_until"
        const val FIND_PASS_UNTIL = "find_pass_until"
    }

    data class SavedLogin(
        val qq: String,
        val pass: String,
        val keepLoggedIn: Boolean,
        val rememberAccount: Boolean,
        val user: YunJuResponse?
    )

    fun read(context: Context): SavedLogin {
        StudioMmkv.ensureInit(context)
        val id = StudioMmkv.ID_LOGIN
        val userJson = StudioMmkv.getString(context, id, Keys.USER_JSON)
        return SavedLogin(
            qq = StudioMmkv.getString(context, id, Keys.QQ) ?: "",
            pass = StudioMmkv.getString(context, id, Keys.PASS)?.let { sealed ->
                // 存时 sealString 加密，此处必须解封；Keystore 密钥丢失（卸载重装）时返回空，跳过重校验
                KeystoreCrypto.openString(sealed) ?: ""
            } ?: "",
            keepLoggedIn = StudioMmkv.getBoolean(context, id, Keys.KEEP_LOGGED_IN, true),
            rememberAccount = StudioMmkv.getBoolean(context, id, Keys.REMEMBER_ACCOUNT, false),
            user = userJson?.let { sealed ->
                val plain = KeystoreCrypto.openString(sealed) ?: return@let null
                runCatching { gson.fromJson(plain, YunJuResponse::class.java) }.getOrNull()
            }
        )
    }

    fun saveLogin(
        context: Context,
        qq: String,
        pass: String,
        keepLoggedIn: Boolean,
        rememberAccount: Boolean,
        user: YunJuResponse
    ) {
        StudioMmkv.ensureInit(context)
        val id = StudioMmkv.ID_LOGIN
        // 自动重登录与记住账号都依赖 QQ，故两者任一开启都保留
        if (rememberAccount || keepLoggedIn) {
            StudioMmkv.putString(context, id, Keys.QQ, qq)
        } else {
            StudioMmkv.remove(context, id, Keys.QQ)
        }
        if (keepLoggedIn) {
            StudioMmkv.putString(context, id, Keys.PASS, KeystoreCrypto.sealString(pass))
            StudioMmkv.putString(context, id, Keys.USER_JSON, KeystoreCrypto.sealString(gson.toJson(user)))
        } else {
            StudioMmkv.remove(context, id, Keys.PASS)
            StudioMmkv.remove(context, id, Keys.USER_JSON)
        }
        StudioMmkv.putBoolean(context, id, Keys.KEEP_LOGGED_IN, keepLoggedIn)
        StudioMmkv.putBoolean(context, id, Keys.REMEMBER_ACCOUNT, rememberAccount)
    }

    fun updateUser(context: Context, user: YunJuResponse) {
        StudioMmkv.ensureInit(context)
        StudioMmkv.putString(
            context,
            StudioMmkv.ID_LOGIN,
            Keys.USER_JSON,
            KeystoreCrypto.sealString(gson.toJson(user))
        )
    }

    fun clear(context: Context) {
        StudioMmkv.ensureInit(context)
        StudioMmkv.clear(context, StudioMmkv.ID_LOGIN)
    }

    /** 读取冷却到期时间戳(毫秒)。无记录或已过期返回 0。 */
    fun readCooldownUntil(context: Context, tag: String): Long {
        StudioMmkv.ensureInit(context)
        val id = StudioMmkv.ID_LOGIN
        val until = when (tag) {
            CooldownTag.SEND_CODE -> StudioMmkv.getLong(context, id, Keys.SEND_CODE_UNTIL, 0L)
            CooldownTag.FIND_PASS -> StudioMmkv.getLong(context, id, Keys.FIND_PASS_UNTIL, 0L)
            else -> 0L
        }
        return if (until > System.currentTimeMillis()) until else 0L
    }

    /** 写入冷却到期时间戳(毫秒)。 */
    fun writeCooldownUntil(context: Context, tag: String, untilMillis: Long) {
        StudioMmkv.ensureInit(context)
        val id = StudioMmkv.ID_LOGIN
        when (tag) {
            CooldownTag.SEND_CODE -> StudioMmkv.putLong(context, id, Keys.SEND_CODE_UNTIL, untilMillis)
            CooldownTag.FIND_PASS -> StudioMmkv.putLong(context, id, Keys.FIND_PASS_UNTIL, untilMillis)
        }
    }
}

/** 冷却标签：注册验证码发送 / 找回密码发送，各自独立计时 */
object CooldownTag {
    const val SEND_CODE = "send_code"
    const val FIND_PASS = "find_pass"
}