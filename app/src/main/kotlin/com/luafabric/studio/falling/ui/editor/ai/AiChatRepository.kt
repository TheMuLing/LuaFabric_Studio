package com.luafabric.studio.falling.ui.editor.ai

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

object AiChatRepository {
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    private val logTag = "AiChatRepo"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchModels(config: AiConfig): List<String> = withContext(Dispatchers.IO) {
        try {
            val protocol = config.resolvedProtocol
            val url = when (protocol) {
                ApiProtocol.OPENAI -> "${config.resolvedBaseUrl}/v1/models"
                ApiProtocol.ANTHROPIC -> "${config.resolvedBaseUrl}/v1/models"
            }
            android.util.Log.d(logTag, "fetchModels url=$url protocol=$protocol")

            val request = Request.Builder().url(url).apply {
                when (protocol) {
                    ApiProtocol.OPENAI -> addHeader("Authorization", "Bearer ${config.resolvedApiKey}")
                    ApiProtocol.ANTHROPIC -> addHeader("x-api-key", config.resolvedApiKey)
                }
            }.build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            android.util.Log.d(logTag, "fetchModels code=${response.code} body=${body.take(200)}")

            if (!response.isSuccessful) {
                android.util.Log.w(logTag, "fetchModels failed: HTTP ${response.code} $body")
                return@withContext emptyList()
            }

            return@withContext parseModelsResponse(body, protocol)
        } catch (e: Exception) {
            android.util.Log.e(logTag, "fetchModels error", e)
            emptyList()
        }
    }

    suspend fun fetchModels(baseUrl: String, apiKey: String, protocol: ApiProtocol): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = when (protocol) {
                ApiProtocol.OPENAI -> "${baseUrl.trimEnd('/')}/v1/models"
                ApiProtocol.ANTHROPIC -> "${baseUrl.trimEnd('/')}/v1/models"
            }
            android.util.Log.d(logTag, "fetchModels2 url=$url protocol=$protocol")
            val request = Request.Builder().url(url).apply {
                when (protocol) {
                    ApiProtocol.OPENAI -> addHeader("Authorization", "Bearer $apiKey")
                    ApiProtocol.ANTHROPIC -> addHeader("x-api-key", apiKey)
                }
            }.build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            android.util.Log.d(logTag, "fetchModels2 code=${response.code} body=${body.take(200)}")
            if (!response.isSuccessful) {
                android.util.Log.w(logTag, "fetchModels2 failed: HTTP ${response.code} $body")
                return@withContext emptyList()
            }
            return@withContext parseModelsResponse(body, protocol)
        } catch (e: Exception) {
            android.util.Log.e(logTag, "fetchModels2 error", e)
            emptyList()
        }
    }

    private fun parseModelsResponse(body: String, protocol: ApiProtocol): List<String> {
        // Try OpenAI format first (most common, covers many Anthropic-compatible endpoints)
        try {
            val modelsResp = gson.fromJson(body, OpenAiModelsResponse::class.java)
            if (modelsResp.data.isNotEmpty()) {
                android.util.Log.d(logTag, "parseModelsResponse: parsed as OpenAI format, ${modelsResp.data.size} models")
                return modelsResp.data.map { it.id }
            }
        } catch (_: Exception) { }

        // Fall back to Anthropic format
        try {
            val modelsResp = gson.fromJson(body, AnthropicModelsResponse::class.java)
            if (modelsResp.data.isNotEmpty()) {
                android.util.Log.d(logTag, "parseModelsResponse: parsed as Anthropic format, ${modelsResp.data.size} models")
                return modelsResp.data.map { it.id }
            }
        } catch (_: Exception) { }

        android.util.Log.w(logTag, "parseModelsResponse: failed to parse response body")
        return emptyList()
    }

    fun streamChat(
        config: AiConfig,
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        onChunk: (String) -> Unit,
        onToolCall: (ToolCallInfo) -> Unit,
        onComplete: (String?) -> Unit
    ) {
        val protocol = config.resolvedProtocol
        val requestBody = buildRequestBody(config, messages, tools, protocol)
        val httpRequest = buildHttpRequest(config, requestBody, protocol)
        android.util.Log.d(logTag, "streamChat url=${httpRequest.url} protocol=$protocol model=${config.activeProvider?.model ?: config.model} apiKey=${if (config.resolvedApiKey.isNotBlank()) "***" else "EMPTY"}")

        client.newCall(httpRequest).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string() ?: "Unknown error"
                        android.util.Log.w(logTag, "streamChat HTTP ${resp.code}: $errorBody")
                        onComplete("HTTP ${resp.code}: $errorBody")
                        return
                    }
                    android.util.Log.d(logTag, "streamChat connected HTTP ${resp.code}")
                    try {
                        when (protocol) {
                            ApiProtocol.OPENAI -> parseOpenAiStream(resp, onChunk, onToolCall, onComplete)
                            ApiProtocol.ANTHROPIC -> parseAnthropicStream(resp, onChunk, onToolCall, onComplete)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(logTag, "streamChat parse error", e)
                        onComplete("Stream error: ${e.message}")
                    }
                }
            }

            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                android.util.Log.e(logTag, "streamChat network error", e)
                onComplete("Network error: ${e.message}")
            }
        })
    }

    suspend fun summarizeMessages(
        config: AiConfig,
        messages: List<ChatMessage>,
        existingSummary: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val dump = messages.joinToString("\n\n") { msg ->
                when (msg.role) {
                    ChatRole.USER -> "用户：${msg.content}"
                    ChatRole.ASSISTANT -> "助手：${msg.content}"
                    ChatRole.TOOL -> "工具结果：${msg.content}"
                    ChatRole.SYSTEM -> "系统：${msg.content}"
                }
            }
            val summaryPrompt = buildList {
                add(ChatMessage(
                    id = "summary_sys",
                    role = ChatRole.SYSTEM,
                    content = "你是对话摘要助手。请用简洁的中文总结以下对话内容，保留关键信息：用户的需求和问题、已做出的决定、重要结论、引用的文件路径和行号、待办事项。不要遗漏重要细节，也不要添加对话中不存在的信息。"
                ))
                if (existingSummary.isNotBlank()) {
                    add(ChatMessage(
                        id = "summary_prev",
                        role = ChatRole.SYSTEM,
                        content = "以下是之前已压缩过的对话摘要，请将新内容合并进这份摘要，保持整体简洁：\n$existingSummary"
                    ))
                }
                add(ChatMessage(
                    id = "summary_user",
                    role = ChatRole.USER,
                    content = "以下是需要总结的对话内容：\n$dump"
                ))
            }
            val body = buildRequestBody(config, summaryPrompt, emptyList())
            val httpRequest = buildHttpRequest(config, body)
            val response = client.newCall(httpRequest).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    android.util.Log.w(logTag, "summarizeMessages HTTP ${resp.code}")
                    return@withContext ""
                }
                val respBody = resp.body?.string() ?: return@withContext ""
                val text = parseCompletionText(respBody, config.resolvedProtocol)
                android.util.Log.d(logTag, "summarizeMessages result len=${text.length}")
                text
            }
        } catch (e: Exception) {
            android.util.Log.e(logTag, "summarizeMessages error", e)
            ""
        }
    }

    private fun parseCompletionText(body: String, protocol: ApiProtocol): String {
        return try {
            when (protocol) {
                ApiProtocol.OPENAI -> {
                    val root = JsonParser.parseString(body).asJsonObject
                    val choices = root.getAsJsonArray("choices")
                    if (choices == null || choices.size() == 0) return ""
                    choices[0].asJsonObject.getAsJsonObject("message")?.get("content")?.asString ?: ""
                }
                ApiProtocol.ANTHROPIC -> {
                    val root = JsonParser.parseString(body).asJsonObject
                    val content = root.getAsJsonArray("content") ?: return ""
                    content.mapNotNull { it.asJsonObject.get("text")?.asString }.joinToString("")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(logTag, "parseCompletionText error", e)
            ""
        }
    }

    private fun buildRequestBody(
        config: AiConfig,
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        protocol: ApiProtocol = config.resolvedProtocol
    ): String {
        return when (protocol) {
            ApiProtocol.OPENAI -> buildOpenAiRequest(config, messages, tools)
            ApiProtocol.ANTHROPIC -> buildAnthropicRequest(config, messages, tools)
        }
    }

    private fun buildOpenAiRequest(
        config: AiConfig,
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): String {
        val apiMessages = messages.map { msg ->
            when (msg.role) {
                ChatRole.USER -> OpenAiMessage("user", msg.content)
                ChatRole.ASSISTANT -> {
                    if (msg.toolCalls.isNotEmpty()) {
                        OpenAiMessage(
                            role = "assistant",
                            content = msg.content.ifEmpty { null },
                            toolCalls = msg.toolCalls.map { tc ->
                                OpenAiToolCall(
                                    id = tc.id,
                                    type = "function",
                                    function = OpenAiFunctionCall(tc.name, tc.arguments)
                                )
                            }
                        )
                    } else {
                        OpenAiMessage("assistant", msg.content)
                    }
                }
                ChatRole.TOOL -> OpenAiMessage(
                    role = "tool",
                    content = msg.content,
                    toolCallId = msg.toolCalls.firstOrNull()?.id
                )
                ChatRole.SYSTEM -> OpenAiMessage("system", msg.content)
            }
        }

        val openAiTools = if (tools.isNotEmpty()) {
            tools.map { tool ->
                OpenAiTool(
                    function = OpenAiFunction(
                        name = tool.name,
                        description = tool.description,
                        parameters = tool.parameters
                    )
                )
            }
        } else null

        val request = OpenAiChatRequest(
            model = (config.activeProvider?.model ?: config.model).ifEmpty { AiConfig.DEFAULT_OPENAI_MODEL },
            messages = apiMessages,
            tools = openAiTools,
            toolChoice = if (tools.isNotEmpty()) "auto" else "none",
            maxTokens = config.maxTokens,
            temperature = config.temperature
        )
        android.util.Log.d(logTag, "buildOpenAiRequest model=${request.model} provider=${config.activeProvider?.name}")
        return gson.toJson(request)
    }

    private fun buildAnthropicRequest(
        config: AiConfig,
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): String {
        val systemMessages = messages.filter { it.role == ChatRole.SYSTEM }
        val chatMessages = messages.filter { it.role != ChatRole.SYSTEM }

        val apiMessages = mutableListOf<AnthropicMessage>()
        var i = 0
        while (i < chatMessages.size) {
            val msg = chatMessages[i]
            when (msg.role) {
                ChatRole.ASSISTANT -> {
                    val content = mutableListOf<Map<String, Any>>()
                    if (msg.content.isNotBlank()) {
                        content.add(mapOf("type" to "text", "text" to msg.content))
                    }
                    msg.toolCalls.forEach { tc ->
                        content.add(
                            mapOf(
                                "type" to "tool_use",
                                "id" to tc.id,
                                "name" to tc.name,
                                "input" to parseToolArguments(tc.arguments)
                            )
                        )
                    }
                    apiMessages.add(AnthropicMessage(role = "assistant", content = content))
                    i++
                }
                ChatRole.TOOL -> {
                    // Group consecutive tool results into a single user message
                    val toolResults = mutableListOf<Map<String, Any>>()
                    while (i < chatMessages.size && chatMessages[i].role == ChatRole.TOOL) {
                        val toolMsg = chatMessages[i]
                        val tc = toolMsg.toolCalls.firstOrNull()
                        toolResults.add(
                            mapOf(
                                "type" to "tool_result",
                                "tool_use_id" to (tc?.id ?: ""),
                                "content" to toolMsg.content
                            )
                        )
                        i++
                    }
                    apiMessages.add(AnthropicMessage(role = "user", content = toolResults))
                }
                else -> {
                    apiMessages.add(AnthropicMessage(role = "user", content = msg.content))
                    i++
                }
            }
        }

        val anthropicTools = if (tools.isNotEmpty()) {
            tools.map { tool ->
                AnthropicTool(
                    name = tool.name,
                    description = tool.description,
                    inputSchema = tool.parameters
                )
            }
        } else null

        val request = AnthropicMessageRequest(
            model = (config.activeProvider?.model ?: config.model).ifEmpty { AiConfig.DEFAULT_ANTHROPIC_MODEL },
            messages = apiMessages,
            system = systemMessages.joinToString("\n") { it.content }.ifEmpty { null },
            tools = anthropicTools,
            maxTokens = config.maxTokens,
            temperature = config.temperature
        )
        return gson.toJson(request)
    }

    private fun parseToolArguments(arguments: String): Map<String, Any> {
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            gson.fromJson(arguments, type)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun buildHttpRequest(config: AiConfig, body: String, protocol: ApiProtocol = config.resolvedProtocol): Request {
        return when (protocol) {
            ApiProtocol.OPENAI -> Request.Builder()
                .url("${config.resolvedBaseUrl}/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${config.resolvedApiKey}")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(jsonMediaType))
                .build()

            ApiProtocol.ANTHROPIC -> Request.Builder()
                .url("${config.resolvedBaseUrl}/v1/messages")
                .addHeader("x-api-key", config.resolvedApiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(jsonMediaType))
                .build()
        }
    }

    private fun parseOpenAiStream(
        response: okhttp3.Response,
        onChunk: (String) -> Unit,
        onToolCall: (ToolCallInfo) -> Unit,
        onComplete: (String?) -> Unit
    ) {
        val source = response.body?.source() ?: run {
            android.util.Log.e(logTag, "parseOpenAiStream: no response body")
            onComplete("No response body")
            return
        }

        val reader = source.inputStream().bufferedReader()
        val toolCallAccumulators = mutableMapOf<Int, ToolCallAccumulator>()
        var lineCount = 0
        var textChunkCount = 0

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val data = line ?: continue
            lineCount++
            if (!data.startsWith("data: ")) {
                if (lineCount <= 5) {
                    android.util.Log.d(logTag, "parseOpenAiStream: non-data line[$lineCount]: ${data.take(100)}")
                }
                continue
            }
            val payload = data.removePrefix("data: ").trim()
            if (payload == "[DONE]") break

            try {
                val chunk = gson.fromJson(payload, OpenAiStreamChunk::class.java)
                val choice = chunk.choices?.firstOrNull() ?: continue

                // Text content
                val content = choice.delta.content
                if (!content.isNullOrEmpty()) {
                    textChunkCount++
                    onChunk(content)
                }

                // Tool calls
                choice.delta.toolCalls?.forEach { tcDelta ->
                    val acc = toolCallAccumulators.getOrPut(tcDelta.index) {
                        ToolCallAccumulator()
                    }
                    tcDelta.id?.let { acc.id = it }
                    tcDelta.function?.name?.let { acc.name = it }
                    tcDelta.function?.arguments?.let { acc.arguments += it }
                }

                // Finish reason means tool calls are complete
                if (choice.finishReason == "tool_calls") {
                    toolCallAccumulators.values.forEach { acc ->
                        if (acc.id.isNotEmpty() && acc.name.isNotEmpty()) {
                            onToolCall(ToolCallInfo(acc.id, acc.name, acc.arguments))
                        }
                    }
                    toolCallAccumulators.clear()
                }
            } catch (e: Exception) {
                android.util.Log.w(logTag, "parseOpenAiStream: parse error line[$lineCount]: ${payload.take(100)}", e)
            }
        }

        // Emit any remaining tool calls
        toolCallAccumulators.values.forEach { acc ->
            if (acc.id.isNotEmpty() && acc.name.isNotEmpty()) {
                onToolCall(ToolCallInfo(acc.id, acc.name, acc.arguments))
            }
        }
        toolCallAccumulators.clear()

        if (textChunkCount == 0) {
            android.util.Log.w(logTag, "parseOpenAiStream: stream ended with ZERO text chunks! total lines=$lineCount")
        }
        onComplete(null)
    }

    private fun parseAnthropicStream(
        response: okhttp3.Response,
        onChunk: (String) -> Unit,
        onToolCall: (ToolCallInfo) -> Unit,
        onComplete: (String?) -> Unit
    ) {
        val reader = response.body?.source()?.inputStream()?.bufferedReader() ?: run {
            android.util.Log.e(logTag, "parseAnthropicStream: no response body")
            onComplete("No response body")
            return
        }

        var toolCallAccumulator: ToolCallAccumulator? = null
        var lineCount = 0
        var textChunkCount = 0

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val data = line ?: continue
            lineCount++
            if (!data.startsWith("data: ")) {
                // Log non-data lines (SSE event lines, etc.) for first 10 lines
                if (lineCount <= 10) {
                    android.util.Log.d(logTag, "parseAnthropicStream: non-data line[$lineCount]: ${data.take(100)}")
                }
                continue
            }
            val payload = data.removePrefix("data: ").trim()
            if (payload == "[DONE]") break

            try {
                val event = gson.fromJson(payload, AnthropicStreamEvent::class.java)
                if (lineCount <= 5) {
                    android.util.Log.d(logTag, "parseAnthropicStream: event type=${event.type} payload=${payload.take(120)}")
                }
                when (event.type) {
                    "content_block_start" -> {
                        val block = event.contentBlock
                        if (block?.type == "tool_use") {
                            android.util.Log.d(logTag, "parseAnthropicStream: tool_use start id=${block.id} name=${block.name}")
                            toolCallAccumulator = ToolCallAccumulator().apply {
                                id = block.id ?: ""
                                name = block.name ?: ""
                            }
                        } else if (block?.type == "text") {
                            android.util.Log.d(logTag, "parseAnthropicStream: text block start")
                        }
                    }
                    "content_block_delta" -> {
                        val delta = event.delta
                        when (delta?.type) {
                            "text_delta" -> {
                                val text = delta.text ?: ""
                                if (text.isNotEmpty()) {
                                    textChunkCount++
                                    onChunk(text)
                                }
                            }
                            "input_json_delta" -> {
                                toolCallAccumulator?.let { acc ->
                                    delta.partial_json?.let { acc.arguments += it }
                                }
                            }
                            else -> {
                                android.util.Log.d(logTag, "parseAnthropicStream: unknown delta type=${delta?.type} text=${delta?.text?.take(50)}")
                            }
                        }
                    }
                    "content_block_stop" -> {
                        toolCallAccumulator?.let { acc ->
                            if (acc.id.isNotEmpty() && acc.name.isNotEmpty()) {
                                android.util.Log.d(logTag, "parseAnthropicStream: tool_use complete name=${acc.name}")
                                onToolCall(ToolCallInfo(acc.id, acc.name, acc.arguments))
                            }
                        }
                        toolCallAccumulator = null
                    }
                    "message_delta" -> {
                        android.util.Log.d(logTag, "parseAnthropicStream: message_delta stop_reason=${event.delta?.stopReason}")
                    }
                    "message_stop" -> {
                        android.util.Log.d(logTag, "parseAnthropicStream: message_stop (total lines=$lineCount, textChunks=$textChunkCount)")
                    }
                    "error" -> {
                        android.util.Log.w(logTag, "parseAnthropicStream: API error payload=${payload.take(200)}")
                    }
                    else -> {
                        android.util.Log.d(logTag, "parseAnthropicStream: unhandled event type=${event.type}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(logTag, "parseAnthropicStream: parse error line[$lineCount]: ${payload.take(100)}", e)
            }
        }

        if (textChunkCount == 0) {
            android.util.Log.w(logTag, "parseAnthropicStream: stream ended with ZERO text chunks! total lines=$lineCount")
        }
        onComplete(null)
    }

    private data class ToolCallAccumulator(
        var id: String = "",
        var name: String = "",
        var arguments: String = ""
    )
}