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

    /**
     * 每个项目的对话记录独立存放：ai_chat_history/<项目目录名>/
     * projectPath 为项目目录绝对路径，取最后一段目录名作为隔离 key。
     */
    private fun getHistoryDir(context: Context, projectPath: String): File {
        val key = File(projectPath).name.ifBlank { "default" }
        val dir = File(File(context.filesDir, HISTORY_DIR), key)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun saveConversation(context: Context, projectPath: String, data: ConversationData) = withContext(Dispatchers.IO) {
        try {
            val file = File(getHistoryDir(context, projectPath), "${data.id}.json")
            file.writeText(gson.toJson(data))
        } catch (_: Exception) { }
    }

    suspend fun loadConversation(context: Context, projectPath: String, id: String): ConversationData? = withContext(Dispatchers.IO) {
        try {
            val file = File(getHistoryDir(context, projectPath), "$id.json")
            if (!file.exists()) return@withContext null
            val data = gson.fromJson(file.readText(), ConversationData::class.java)
            // 旧版本保存的 JSON 可能缺少 summary/messages 字段（Gson 会注入 null），
            // 这里统一归一化，避免下游非空参数 NPE 闪退
            data.copy(
                summary = data.summary ?: "",
                messages = data.messages ?: emptyList()
            )
        } catch (_: Exception) { null }
    }

    suspend fun listConversations(context: Context, projectPath: String): List<Conversation> = withContext(Dispatchers.IO) {
        try {
            val dir = getHistoryDir(context, projectPath)
            dir.listFiles()?.filter { it.extension == "json" }?.mapNotNull { file ->
                try {
                    val data = gson.fromJson(file.readText(), ConversationData::class.java)
                    Conversation(
                        id = data.id,
                        title = data.title,
                        createdAt = data.createdAt,
                        updatedAt = data.updatedAt,
                        messageCount = (data.messages ?: emptyList()).size
                    )
                } catch (_: Exception) { null }
            }?.sortedByDescending { it.updatedAt } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun deleteConversation(context: Context, projectPath: String, id: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(getHistoryDir(context, projectPath), "$id.json")
            file.delete()
        } catch (_: Exception) { }
    }

    suspend fun deleteAllConversations(context: Context, projectPath: String) = withContext(Dispatchers.IO) {
        try {
            getHistoryDir(context, projectPath).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) { }
    }

    /** 删除指定项目的整个对话记录目录（删除项目时连带调用）。 */
    suspend fun deleteAllForProject(context: Context, projectPath: String) = withContext(Dispatchers.IO) {
        try {
            val key = File(projectPath).name.ifBlank { "default" }
            val dir = File(File(context.filesDir, HISTORY_DIR), key)
            if (dir.exists()) dir.deleteRecursively()
        } catch (_: Exception) { }
    }

    fun createNewId(): String = UUID.randomUUID().toString().take(8)

    fun generateTitle(messages: List<ChatMessage>): String {
        val firstUserMsg = messages.firstOrNull { it.role == ChatRole.USER }?.content ?: return "New Chat"
        val preview = firstUserMsg.take(60)
        return if (firstUserMsg.length > 60) "$preview..." else preview
    }
}