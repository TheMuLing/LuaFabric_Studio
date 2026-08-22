package com.luafabric.studio.falling.ui.editor.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.aiDataStore by preferencesDataStore(name = "ai_settings")

object AiSettingsManager {
    private const val KEY_AI_CONFIG = "ai_config"
    private const val KEY_SHOW_WELCOME = "show_welcome"
    private const val KEY_DISMISS_WELCOME = "dismiss_welcome"

    private var _config: AiConfig = AiConfig()

    private val gson = Gson()

    fun loadConfig(context: Context): AiConfig {
        try {
            val prefs = runBlocking { context.aiDataStore.data.first() }
            val json = prefs[stringPreferencesKey(KEY_AI_CONFIG)]
            if (json != null) {
                _config = gson.fromJson(json, AiConfig::class.java).normalized()
            }
        } catch (_: Exception) { }
        return _config
    }

    fun getConfig(): AiConfig = _config

    fun saveConfig(context: Context, config: AiConfig) {
        _config = config
        runBlocking {
            context.aiDataStore.edit { prefs ->
                prefs[stringPreferencesKey(KEY_AI_CONFIG)] = gson.toJson(config)
            }
        }
    }

    fun isWelcomeDismissed(context: Context): Boolean {
        return try {
            val prefs = runBlocking { context.aiDataStore.data.first() }
            prefs[stringPreferencesKey(KEY_DISMISS_WELCOME)]?.toBoolean() ?: false
        } catch (_: Exception) { false }
    }

    fun setWelcomeDismissed(context: Context, dismissed: Boolean) {
        runBlocking {
            context.aiDataStore.edit { prefs ->
                prefs[stringPreferencesKey(KEY_DISMISS_WELCOME)] = dismissed.toString()
            }
        }
    }
}