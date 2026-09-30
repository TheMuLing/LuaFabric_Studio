package com.luafabric.studio.falling.ui.editor.ai

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
            val url = buildModelsUrl(normalizeBaseUrl(config.resolvedBaseUrl), protocol)
            android.util.Log.d(logTag, "fetchModels input=${config.resolvedBaseUrl} url=$url protocol=$protocol apiKey=${if (config.resolvedApiKey.isNotBlank()) "***" else "EMPTY"}")

            val request = Request.Builder().url(url).apply {
                when (protocol) {
                    ApiProtocol.OPENAI -> addHeader("Authorization", "Bearer ${config.resolvedApiKey}")
                    ApiProtocol.ANTHROPIC -> addHeader("x-api-key", config.resolvedApiKey)
                }
            }.build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            android.util.Log.d(logTag, "fetchModels code=${response.code} body=${body.take(300)}")

            if (!response.isSuccessful) {
                android.util.Log.w(logTag, "fetchModels failed: HTTP ${response.code} body=$body")
                return@withContext emptyList()
            }

            val models = parseModelsResponse(body, protocol)
            android.util.Log.d(logTag, "fetchModels parsed ${models.size} models")
            return@withContext models
        } catch (e: Exception) {
            android.util.Log.e(logTag, "fetchModels error input=${config.resolvedBaseUrl}", e)
            emptyList()
        }
    }

    suspend fun fetchModels(baseUrl: String, apiKey: String, protocol: ApiProtocol): List<String> = withContext(Dispatchers.IO) {
        try {
            val base = normalizeBaseUrl(baseUrl)
            val url = buildModelsUrl(base, protocol)
            android.util.Log.d(logTag, "fetchModels2 input=$baseUrl normalized=$base url=$url protocol=$protocol apiKey=${if (apiKey.isNotBlank()) "***" else "EMPTY"}")
            val request = Request.Builder().url(url).apply {
                when (protocol) {
                    ApiProtocol.OPENAI -> addHeader("Authorization", "Bearer $apiKey")
                    ApiProtocol.ANTHROPIC -> addHeader("x-api-key", apiKey)
                }
            }.build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            android.util.Log.d(logTag, "fetchModels2 code=${response.code} body=${body.take(300)}")
            if (!response.isSuccessful) {
                android.util.Log.w(logTag, "fetchModels2 failed: HTTP ${response.code} body=$body")
                return@withContext emptyList()
            }
            val models = parseModelsResponse(body, protocol)
            android.util.Log.d(logTag, "fetchModels2 parsed ${models.size} models")
            return@withContext models
        } catch (e: Exception) {
            android.util.Log.e(logTag, "fetchModels2 error input=$baseUrl", e)
            emptyList()
        }
    }

    // 规范化 API 基础地址：去空白/尾斜杠，剥离 chat/models/messages 端点后缀（含 /v1 前缀），裸域名补 https://
    // 注意：仅剥离端点后缀，路径中用户主动填写的 /v1（如 https://api.siliconflow.cn/v1）予以保留
    fun normalizeBaseUrl(raw: String): String {
        var base = raw.trim().trimEnd('/')
        val suffixes = listOf(
            "/v1/chat/completions",
            "/chat/completions",
            "/v1/models",
            "/models",
            "/v1/messages"
        )
        for (suffix in suffixes) {
            if (base.endsWith(suffix)) {
                base = base.removeSuffix(suffix).trimEnd('/')
                break
            }
        }
        if (!base.startsWith("http://") && !base.startsWith("https://") && base.isNotEmpty()) {
            base = "https://$base"
        }
        return base
    }

    // 构建模型列表地址：与 chat 端点同规则（官方文档），保留 base 中已填的 /v1；
    // Anthropic 官方 models 端点在 /v1 下，base 已含 /v1 时去重
    private fun buildModelsUrl(base: String, protocol: ApiProtocol): String = when (protocol) {
        ApiProtocol.OPENAI -> "$base/models"
        ApiProtocol.ANTHROPIC -> if (base.endsWith("/v1")) "$base/models" else "$base/v1/models"
    }

    private fun parseModelsResponse(body: String, protocol: ApiProtocol): List<String> {
        // Try OpenAI format first (most common, covers many Anthropic-compatible endpoints)
        try {
            val modelsResp = gson.fromJson(body, OpenAiModelsResponse::class.java)
            if (modelsResp.data.isNotEmpty()) {
                android.util.Log.d(logTag, "parseModelsResponse: parsed as OpenAI format, ${modelsResp.data.size} models")
                return modelsResp.data.map { it.id }
            }
            android.util.Log.w(logTag, "parseModelsResponse: OpenAI format parsed but data empty")
        } catch (e: Exception) {
            android.util.Log.w(logTag, "parseModelsResponse: OpenAI parse failed: ${e.message}")
        }

        // Fall back to Anthropic format
        try {
            val modelsResp = gson.fromJson(body, AnthropicModelsResponse::class.java)
            if (modelsResp.data.isNotEmpty()) {
                android.util.Log.d(logTag, "parseModelsResponse: parsed as Anthropic format, ${modelsResp.data.size} models")
                return modelsResp.data.map { it.id }
            }
            android.util.Log.w(logTag, "parseModelsResponse: Anthropic format parsed but data empty")
        } catch (e: Exception) {
            android.util.Log.w(logTag, "parseModelsResponse: Anthropic parse failed: ${e.message}")
        }

        android.util.Log.w(logTag, "parseModelsResponse: failed to parse response body, body=${body.take(500)}")
        return emptyList()
    }

    fun streamChat(
        config: AiConfig,
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        onChunk: (String) -> Unit,
        onReasoning: (String) -> Unit,
        onToolCall: (ToolCallInfo) -> Unit,
        onComplete: (String?) -> Unit,
        stream: Boolean = true
    ) {
        val protocol = config.resolvedProtocol
        val requestBody = buildRequestBody(config, messages, tools, protocol, stream)
        val httpRequest = buildHttpRequest(config, requestBody, protocol)
        android.util.Log.d(logTag, "streamChat url=${httpRequest.url} protocol=$protocol model=${config.activeProvider?.model ?: config.model} apiKey=${if (config.resolvedApiKey.isNotBlank()) "***" else "EMPTY"}")

        client.newCall(httpRequest).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val startMs = System.currentTimeMillis()
                response.use { resp ->
                    val elapsedMs = System.currentTimeMillis() - startMs
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string() ?: "Unknown error"
                        val headers = resp.headers.toMultimap()
                            .map { (k, v) -> "$k=${v.joinToString(",")}" }
                            .joinToString("; ")
                        // 422/400 类协议拒绝：连请求摘要一起落日志，便于直接定位哪条消息/哪个工具被拒
                        android.util.Log.w(logTag, "streamChat HTTP ${resp.code} in ${elapsedMs}ms body=$errorBody headers=$headers")
                        android.util.Log.w(logTag, "streamChat failed request digest: ${requestDigest(messages, tools)}")
                        onComplete("HTTP ${resp.code}: $errorBody")
                        return
                    }
                    android.util.Log.d(logTag, "streamChat connected HTTP ${resp.code} in ${elapsedMs}ms")
                    try {
                        if (stream) {
                            when (protocol) {
                                ApiProtocol.OPENAI -> parseOpenAiStream(resp, onChunk, onReasoning, onToolCall, onComplete)
                                ApiProtocol.ANTHROPIC -> parseAnthropicStream(resp, onChunk, onToolCall, onComplete)
                            }
                        } else {
                            parseNonStreamingResponse(resp, protocol, onChunk, onReasoning, onToolCall, onComplete)
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
                    val errorBody = resp.body?.string() ?: "Unknown error"
                    val headers = resp.headers.toMultimap()
                        .map { (k, v) -> "$k=${v.joinToString(",")}" }
                        .joinToString("; ")
                    android.util.Log.w(logTag, "summarizeMessages HTTP ${resp.code} body=$errorBody headers=$headers")
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

    /**
     * 非流式响应解析（降级重试用）：部分端点流式模式不下发 tool_calls（如 SiliconFlow 对部分模型），
     * 流式空返回时由调用方用同参非流式重试一次；这里统一提取 content/reasoning/tool_calls。
     */
    private fun parseNonStreamingResponse(
        response: okhttp3.Response,
        protocol: ApiProtocol,
        onChunk: (String) -> Unit,
        onReasoning: (String) -> Unit,
        onToolCall: (ToolCallInfo) -> Unit,
        onComplete: (String?) -> Unit
    ) {
        try {
            if (protocol != ApiProtocol.OPENAI) {
                onComplete("non-stream retry only supports OPENAI protocol")
                return
            }
            val body = response.body?.string() ?: run {
                onComplete("Empty body")
                return
            }
            val root = com.google.gson.JsonParser.parseString(body).asJsonObject
            val message = root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")
                ?: run {
                    onComplete("No choices in response: ${body.take(200)}")
                    return
                }
            message.get("content")?.takeIf { !it.isJsonNull }?.asString
                ?.let { if (it.isNotBlank()) onChunk(it) }
            message.get("reasoning_content")?.takeIf { !it.isJsonNull }?.asString
                ?.let { if (it.isNotBlank()) onReasoning(it) }
            message.getAsJsonArray("tool_calls")?.forEach { tcElem ->
                val tc = tcElem.asJsonObject
                val fn = tc.getAsJsonObject("function")
                onToolCall(ToolCallInfo(
                    id = tc.get("id")?.asString.orEmpty(),
                    name = fn.get("name")?.asString.orEmpty(),
                    arguments = fn.get("arguments")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                ))
            }
            onComplete(null)
        } catch (e: Exception) {
            android.util.Log.e(logTag, "parseNonStreamingResponse error", e)
            onComplete("Parse error: ${e.message}")
        }
    }

    private fun buildRequestBody(
        config: AiConfig,
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        protocol: ApiProtocol = config.resolvedProtocol,
        stream: Boolean = true
    ): String {
        val safeMessages = sanitizeToolPairing(messages)
        return when (protocol) {
            ApiProtocol.OPENAI -> buildOpenAiRequest(config, safeMessages, tools, stream)
            ApiProtocol.ANTHROPIC -> buildAnthropicRequest(config, safeMessages, tools)
        }
    }

    /**
     * 协议自愈：清洗 tool_calls 与 tool 结果的配对关系。
     * 网关（422/400）会拒绝「assistant 声明了 tool_calls 但没有对应 tool 结果」或被取消中断留下的
     * 孤儿 tool 消息；这里按 id 双向剔除不配对项，并对纯工具调用且内容为空的 assistant 消息整条丢弃。
     */
    private fun sanitizeToolPairing(messages: List<ChatMessage>): List<ChatMessage> {
        val resultIds = messages.filter { it.role == ChatRole.TOOL }
            .mapNotNull { it.toolCalls.firstOrNull()?.id }
            .filter { it.isNotEmpty() }
            .toSet()
        val keepCallIds = messages.filter { it.role == ChatRole.ASSISTANT }
            .flatMap { it.toolCalls }
            .map { it.id }
            .filter { it in resultIds }
            .toSet()

        val out = ArrayList<ChatMessage>(messages.size)
        var droppedCalls = 0
        var droppedTools = 0
        for (msg in messages) {
            when (msg.role) {
                ChatRole.ASSISTANT -> {
                    if (msg.toolCalls.isEmpty()) {
                        out.add(msg)
                        continue
                    }
                    val calls = msg.toolCalls.filter { it.id in keepCallIds }
                    droppedCalls += msg.toolCalls.size - calls.size
                    if (calls.isEmpty() && msg.content.isBlank()) {
                        // 无内容的纯工具调用消息且全部无结果 → 整条丢弃（空 assistant 消息部分网关直接拒收）
                        continue
                    }
                    out.add(if (calls.size == msg.toolCalls.size) msg else msg.copy(toolCalls = calls))
                }
                ChatRole.TOOL -> {
                    val id = msg.toolCalls.firstOrNull()?.id
                    if (id.isNullOrEmpty() || id !in keepCallIds) droppedTools++ else out.add(msg)
                }
                else -> out.add(msg)
            }
        }
        if (droppedCalls > 0 || droppedTools > 0) {
            android.util.Log.w(
                logTag,
                "sanitizeToolPairing: dropped ${droppedCalls} orphan tool_calls / ${droppedTools} orphan tool results " +
                    "(in=${messages.size} out=${out.size})"
            )
        }
        return out
    }

    /** 请求摘要（非 2xx 时落日志）：消息角色序列 + tool_call/tool 结果配对情况 + 工具数量。 */
    private fun requestDigest(messages: List<ChatMessage>, tools: List<ToolDefinition>): String {
        val safe = sanitizeToolPairing(messages)
        val resultIds = safe.filter { it.role == ChatRole.TOOL }
            .mapNotNull { it.toolCalls.firstOrNull()?.id }
            .toSet()
        val orphanCalls = safe.filter { it.role == ChatRole.ASSISTANT }
            .flatMap { it.toolCalls }
            .count { it.id !in resultIds }
        val roles = safe.joinToString(",") { m ->
            when (m.role) {
                ChatRole.ASSISTANT ->
                    if (m.toolCalls.isNotEmpty()) "assistant(tc=${m.toolCalls.size},len=${m.content.length})"
                    else "assistant(len=${m.content.length})"
                ChatRole.TOOL -> "tool(id=${m.toolCalls.firstOrNull()?.id?.take(8) ?: "MISSING"})"
                ChatRole.USER -> "user(len=${m.content.length})"
                ChatRole.SYSTEM -> "system(len=${m.content.length})"
            }
        }
        return "sent=${safe.size}/raw=${messages.size} tools=${tools.size} " +
            "last=${safe.lastOrNull()?.role} orphanToolCalls=$orphanCalls roles=[$roles]"
    }

    private fun buildOpenAiRequest(
        config: AiConfig,
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        stream: Boolean = true
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

        val thinking = resolveThinkingParams(config)
        val request = OpenAiChatRequest(
            model = (config.activeProvider?.model ?: config.model).ifEmpty { AiConfig.DEFAULT_OPENAI_MODEL },
            messages = apiMessages,
            tools = openAiTools,
            toolChoice = if (tools.isNotEmpty()) "auto" else "none",
            maxTokens = config.maxTokens,
            temperature = config.temperature,
            stream = stream,
            enableThinking = thinking.first,
            reasoningEffort = thinking.second,
            thinking = thinking.third
        )
        android.util.Log.d(logTag, "buildOpenAiRequest model=${request.model} provider=${config.activeProvider?.name} host=${runCatching { config.resolvedBaseUrl.toHttpUrlOrNull()?.host }.getOrNull()} enableThinking=${request.enableThinking}")
        return gson.toJson(request)
    }

    /**
     * 思考参数宿主适配（参考 rikkahub 按 host/模型注入）：
     * SiliconFlow 对 thinking 模型不显式传 enable_thinking 时，流式响应只回 reasoning 而不下发
     * tool_calls（工具不可用）。此处对白名单模型显式关闭思考以保证流式工具可用。
     */
    private fun resolveThinkingParams(
        config: AiConfig
    ): Triple<Boolean?, String?, Map<String, Any>?> {
        val host = runCatching { config.resolvedBaseUrl.toHttpUrlOrNull()?.host }.getOrNull()
        val modelId = (config.activeProvider?.model ?: config.model).ifEmpty { AiConfig.DEFAULT_OPENAI_MODEL }
        return when (host) {
            "api.siliconflow.cn" ->
                Triple(if (modelId in SILICONFLOW_THINKING_MODELS) false else null, null, null)
            else -> Triple(null, null, null)
        }
    }

    private val SILICONFLOW_THINKING_MODELS = setOf(
        "Pro/moonshotai/Kimi-K2.5", "Pro/zai-org/GLM-5", "Pro/zai-org/GLM-5.1", "Pro/zai-org/GLM-4.7",
        "deepseek-ai/DeepSeek-V3.2", "Pro/deepseek-ai/DeepSeek-V3.2",
        "Qwen/Qwen3.5-397B-A17B", "Qwen/Qwen3.5-122B-A10B", "Qwen/Qwen3.5-35B-A3B",
        "Qwen/Qwen3.5-27B", "Qwen/Qwen3.5-9B", "Qwen/Qwen3.5-4B",
        "zai-org/GLM-4.6", "Qwen/Qwen3-8B", "Qwen/Qwen3-14B", "Qwen/Qwen3-32B", "Qwen/Qwen3-30B-A3B",
        "tencent/Hunyuan-A13B-Instruct", "zai-org/GLM-4.5V",
        "deepseek-ai/DeepSeek-V3.1-Terminus", "Pro/deepseek-ai/DeepSeek-V3.1-Terminus",
        "deepseek-ai/DeepSeek-V4-Flash", "Pro/deepseek-ai/DeepSeek-V4-Flash",
        "deepseek-ai/DeepSeek-V4-Pro", "Pro/deepseek-ai/DeepSeek-V4-Pro"
    )

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
        // 端点按官方文档构造：bare 剥离端点后缀并保留用户填写的 /v1；
        // OPENAI 兼容 = base + /chat/completions（SiliconFlow/Kimi base 已含 /v1，DeepSeek 无 /v1）
        val base = normalizeBaseUrl(config.resolvedBaseUrl)
        return when (protocol) {
            ApiProtocol.OPENAI -> Request.Builder()
                .url("$base/chat/completions")
                .addHeader("Authorization", "Bearer ${config.resolvedApiKey}")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(jsonMediaType))
                .build()

            // Anthropic 官方端点 /v1/messages，base 已含 /v1 时去重
            ApiProtocol.ANTHROPIC -> Request.Builder()
                .url(if (base.endsWith("/v1")) "$base/messages" else "$base/v1/messages")
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
        onReasoning: (String) -> Unit,
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
        var reasoningChunkCount = 0
        var reasoningLogged = false
        // 诊断：记录最近 3 个 content chunk 片段（排查输出中断/裸字符问题）
        val lastTextChunks = ArrayDeque<String>()

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

                // Thinking 模型（DeepSeek reasoner 等）先流式回思考链，再回正文
                val reasoning = choice.delta.reasoning_content
                if (!reasoning.isNullOrEmpty()) {
                    reasoningChunkCount++
                    if (!reasoningLogged) {
                        reasoningLogged = true
                        android.util.Log.d(logTag, "parseOpenAiStream: reasoning_content first chunk: ${reasoning.take(200)}")
                    }
                    onReasoning(reasoning)
                }

                // Text content
                val content = choice.delta.content
                if (!content.isNullOrEmpty()) {
                    textChunkCount++
                    lastTextChunks.addLast(content.take(40))
                    while (lastTextChunks.size > 3) lastTextChunks.removeFirst()
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
            android.util.Log.w(
                logTag,
                "parseOpenAiStream: stream ended with ZERO text chunks! total lines=$lineCount reasoningChunks=$reasoningChunkCount"
            )
        } else {
            android.util.Log.d(
                logTag,
                "parseOpenAiStream: done textChunks=$textChunkCount reasoningChunks=$reasoningChunkCount lines=$lineCount " +
                    "tail=[${lastTextChunks.joinToString(" | ")}]"
            )
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