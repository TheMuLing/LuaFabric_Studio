package com.luafabric.studio.falling.ui.editor.ai

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object AiChatHistoryStore {
    private const val HISTORY_DIR = "ai_chat_history"
    private val gson = Gson()

    private fun getHistoryDir(context: Context): File {
        val dir = File(context.filesDir, HISTORY_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun saveConversation(context: Context, data: ConversationData) = withContext(Dispatchers.IO) {
        try {
            val file = File(getHistoryDir(context), "${data.id}.json")
            file.writeText(gson.toJson(data))
        } catch (_: Exception) { }
    }

    suspend fun loadConversation(context: Context, id: String): ConversationData? = withContext(Dispatchers.IO) {
        try {
            val file = File(getHistoryDir(context), "$id.json")
            if (!file.exists()) return@withContext null
            gson.fromJson(file.readText(), ConversationData::class.java)
        } catch (_: Exception) { null }
    }

    suspend fun listConversations(context: Context): List<Conversation> = withContext(Dispatchers.IO) {
        try {
            val dir = getHistoryDir(context)
            dir.listFiles()?.filter { it.extension == "json" }?.mapNotNull { file ->
                try {
                    val data = gson.fromJson(file.readText(), ConversationData::class.java)
                    Conversation(
                        id = data.id,
                        title = data.title,
                        createdAt = data.createdAt,
                        updatedAt = data.updatedAt,
                        messageCount = data.messages.size
                    )
                } catch (_: Exception) { null }
            }?.sortedByDescending { it.updatedAt } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun deleteConversation(context: Context, id: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(getHistoryDir(context), "$id.json")
            file.delete()
        } catch (_: Exception) { }
    }

    suspend fun deleteAllConversations(context: Context) = withContext(Dispatchers.IO) {
        try {
            getHistoryDir(context).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) { }
    }

    fun createNewId(): String = UUID.randomUUID().toString().take(8)

    fun generateTitle(messages: List<ChatMessage>): String {
        val firstUserMsg = messages.firstOrNull { it.role == ChatRole.USER }?.content ?: return "New Chat"
        val preview = firstUserMsg.take(60)
        return if (firstUserMsg.length > 60) "$preview..." else preview
    }
}