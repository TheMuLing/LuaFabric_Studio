package com.luafabric.studio.falling.ui.login

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
}
