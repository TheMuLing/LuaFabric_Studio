package com.luafabric.studio.falling.ui.login

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

private val Context.loginDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_login")

/** 登录态本地持久化（独立 DataStore，与 app_settings 分离） */
object LoginStore {
    private val gson = Gson()

    private object Keys {
        val QQ = stringPreferencesKey("qq")
        val PASS = stringPreferencesKey("pass")
        val KEEP_LOGGED_IN = booleanPreferencesKey("keep_logged_in")
        val REMEMBER_ACCOUNT = booleanPreferencesKey("remember_account")
        val USER_JSON = stringPreferencesKey("user_json")
        // 180s 冷却：各自独立，存到期时间戳(毫秒)，进程重启后依旧生效
        val SEND_CODE_UNTIL = longPreferencesKey("send_code_until")
        val FIND_PASS_UNTIL = longPreferencesKey("find_pass_until")
    }

    data class SavedLogin(
        val qq: String,
        val pass: String,
        val keepLoggedIn: Boolean,
        val rememberAccount: Boolean,
        val user: YunJuResponse?
    )

    suspend fun read(context: Context): SavedLogin {
        val prefs = context.loginDataStore.data.first()
        val userJson = prefs[Keys.USER_JSON]
        return SavedLogin(
            qq = prefs[Keys.QQ] ?: "",
            pass = prefs[Keys.PASS] ?: "",
            keepLoggedIn = prefs[Keys.KEEP_LOGGED_IN] ?: true,
            rememberAccount = prefs[Keys.REMEMBER_ACCOUNT] ?: false,
            user = userJson?.let {
                runCatching { gson.fromJson(it, YunJuResponse::class.java) }.getOrNull()
            }
        )
    }

    suspend fun saveLogin(
        context: Context,
        qq: String,
        pass: String,
        keepLoggedIn: Boolean,
        rememberAccount: Boolean,
        user: YunJuResponse
    ) {
        context.loginDataStore.edit { prefs ->
            // 自动重登录与记住账号都依赖 QQ，故两者任一开启都保留
            if (rememberAccount || keepLoggedIn) prefs[Keys.QQ] = qq else prefs.remove(Keys.QQ)
            if (keepLoggedIn) {
                prefs[Keys.PASS] = pass
                prefs[Keys.USER_JSON] = gson.toJson(user)
            } else {
                prefs.remove(Keys.PASS)
                prefs.remove(Keys.USER_JSON)
            }
            prefs[Keys.KEEP_LOGGED_IN] = keepLoggedIn
            prefs[Keys.REMEMBER_ACCOUNT] = rememberAccount
        }
    }

    suspend fun updateUser(context: Context, user: YunJuResponse) {
        context.loginDataStore.edit { prefs ->
            prefs[Keys.USER_JSON] = gson.toJson(user)
        }
    }

    suspend fun clear(context: Context) {
        context.loginDataStore.edit { it.clear() }
    }

    /** 读取冷却到期时间戳(毫秒)。无记录或已过期返回 0。 */
    suspend fun readCooldownUntil(context: Context, tag: String): Long {
        val prefs = context.loginDataStore.data.first()
        val until = when (tag) {
            CooldownTag.SEND_CODE -> prefs[Keys.SEND_CODE_UNTIL] ?: 0L
            CooldownTag.FIND_PASS -> prefs[Keys.FIND_PASS_UNTIL] ?: 0L
            else -> 0L
        }
        return if (until > System.currentTimeMillis()) until else 0L
    }

    /** 写入冷却到期时间戳(毫秒)。 */
    suspend fun writeCooldownUntil(context: Context, tag: String, untilMillis: Long) {
        context.loginDataStore.edit { prefs ->
            when (tag) {
                CooldownTag.SEND_CODE -> prefs[Keys.SEND_CODE_UNTIL] = untilMillis
                CooldownTag.FIND_PASS -> prefs[Keys.FIND_PASS_UNTIL] = untilMillis
            }
        }
    }
}

/** 冷却标签：注册验证码发送 / 找回密码发送，各自独立计时 */
object CooldownTag {
    const val SEND_CODE = "send_code"
    const val FIND_PASS = "find_pass"
}
