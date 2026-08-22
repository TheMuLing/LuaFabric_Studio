package com.luafabric.studio.falling.ui.editor.ai

import com.google.gson.annotations.SerializedName

private fun <T> T?.orDefault(default: T): T = this ?: default

// ========== API Protocol Models ==========

data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = true,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens")
    val maxTokens: Int = 4096,
    val tools: List<OpenAiTool>? = null,
    @SerializedName("tool_choice")
    val toolChoice: String = "auto"
)

data class OpenAiMessage(
    val role: String,
    val content: String?,
    val name: String? = null,
    @SerializedName("tool_calls")
    val toolCalls: List<OpenAiToolCall>? = null,
    @SerializedName("tool_call_id")
    val toolCallId: String? = null
)

data class OpenAiTool(
    val type: String = "function",
    val function: OpenAiFunction
)

data class OpenAiFunction(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
)

data class OpenAiStreamChunk(
    val choices: List<OpenAiChoice>? = null
)

data class OpenAiChoice(
    val delta: OpenAiDelta,
    @SerializedName("finish_reason")
    val finishReason: String? = null,
    val index: Int = 0
)

data class OpenAiDelta(
    val content: String? = null,
    @SerializedName("tool_calls")
    val toolCalls: List<OpenAiToolCallDelta>? = null
)

data class OpenAiToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: OpenAiFunctionDelta? = null
)

data class OpenAiFunctionDelta(
    val name: String? = null,
    val arguments: String? = null
)

data class OpenAiToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAiFunctionCall
)

data class OpenAiFunctionCall(
    val name: String,
    val arguments: String
)

data class OpenAiModelsResponse(
    val data: List<OpenAiModelItem> = emptyList()
)

data class OpenAiModelItem(
    val id: String
)

// ========== Anthropic Protocol Models ==========

data class AnthropicMessageRequest(
    val model: String,
    @SerializedName("max_tokens")
    val maxTokens: Int = 4096,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    val stream: Boolean = true,
    val temperature: Double = 0.7,
    val tools: List<AnthropicTool>? = null
)

data class AnthropicMessage(
    val role: String,
    val content: Any? = null
)

data class AnthropicTool(
    val name: String,
    val description: String,
    @SerializedName("input_schema")
    val inputSchema: Map<String, Any>
)

data class AnthropicStreamEvent(
    val type: String,
    val delta: AnthropicDelta? = null,
    @SerializedName("content_block")
    val contentBlock: AnthropicContentBlock? = null,
    @SerializedName("content_block_start")
    val contentBlockStart: AnthropicContentBlockStart? = null,
    @SerializedName("content_block_delta")
    val contentBlockDelta: AnthropicContentBlockDelta? = null,
    @SerializedName("content_block_stop")
    val contentBlockStop: AnthropicContentBlockStop? = null,
    val message: AnthropicMessageInfo? = null,
    @SerializedName("input_json_delta")
    val inputJsonDelta: AnthropicInputJsonDelta? = null
)

data class AnthropicContentBlockStart(
    val index: Int = 0,
    val content_block: AnthropicContentBlock? = null
)

data class AnthropicContentBlockDelta(
    val index: Int = 0,
    val delta: AnthropicDelta? = null
)

data class AnthropicContentBlockStop(
    val index: Int = 0
)

data class AnthropicDelta(
    val text: String? = null,
    @SerializedName("stop_reason")
    val stopReason: String? = null,
    @SerializedName("stop_sequence")
    val stopSequence: String? = null,
    val type: String? = null,
    val partial_json: String? = null
)

data class AnthropicContentBlock(
    val type: String? = null,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: Map<String, Any>? = null
)

data class AnthropicMessageInfo(
    val id: String? = null,
    val model: String? = null,
    val role: String? = null,
    @SerializedName("stop_reason")
    val stopReason: String? = null,
    @SerializedName("stop_sequence")
    val stopSequence: String? = null,
    val usage: AnthropicUsage? = null
)

data class AnthropicUsage(
    @SerializedName("input_tokens")
    val inputTokens: Int = 0,
    @SerializedName("output_tokens")
    val outputTokens: Int = 0
)

data class AnthropicInputJsonDelta(
    val partial_json: String? = null
)

data class AnthropicModelsResponse(
    val data: List<AnthropicModelInfo> = emptyList()
)

data class AnthropicModelInfo(
    val type: String? = null,
    val id: String
)

// ========== Tool Calling Models ==========

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
)

data class ToolCallInfo(
    val id: String,
    val name: String,
    val arguments: String
)

// ========== Chat UI Models ==========

data class CodeReference(
    val filePath: String,
    val fileName: String,
    val startLine: Int,
    val endLine: Int,
    val content: String
) {
    val preview: String
        get() {
            val text = content.replace('\n', ' ').replace('\r', ' ').trim().take(60)
            return if (startLine == endLine) {
                "第${startLine}行：$text"
            } else {
                "第${startLine}~${endLine}行：$text"
            }
        }
}

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val collapsedSections: List<String> = emptyList(),
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val codeReference: CodeReference? = null
)

enum class ChatRole { USER, ASSISTANT, SYSTEM, TOOL }

enum class ApiProtocol { OPENAI, ANTHROPIC }

data class AiConfig(
    // Old flat fields (kept for backward compatibility)
    val protocol: ApiProtocol = ApiProtocol.OPENAI,
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val useDefaultKey: Boolean = true,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val customModels: List<CustomModel> = emptyList(),
    // New provider list
    val providers: List<ApiProvider> = emptyList(),
    val selectedProviderIndex: Int = -1,
    // Skills
    val skills: List<SkillConfig> = emptyList(),
    // Memories
    val memories: List<MemoryItem> = emptyList()
) {
    val activeProvider: ApiProvider?
        get() = if (selectedProviderIndex in providers.indices) providers[selectedProviderIndex]
        else if (providers.isEmpty()) null
        else providers.firstOrNull()

    val resolvedApiKey: String
        get() {
            activeProvider?.let { return if (it.useDefaultKey) DEFAULT_KEY else it.apiKey }
            return if (useDefaultKey) DEFAULT_KEY else apiKey
        }

    val resolvedBaseUrl: String
        get() {
            activeProvider?.let {
                return when {
                    it.baseUrl.isNotBlank() -> it.baseUrl.trimEnd('/')
                    it.protocol == ApiProtocol.OPENAI -> "https://api.openai.com"
                    else -> "https://api.anthropic.com"
                }
            }
            return when {
                baseUrl.isNotBlank() -> baseUrl.trimEnd('/')
                protocol == ApiProtocol.OPENAI -> "https://api.openai.com"
                else -> "https://api.anthropic.com"
            }
        }

    val resolvedProtocol: ApiProtocol
        get() = activeProvider?.protocol ?: protocol

    val displayModel: String
        get() {
            activeProvider?.let { p ->
                val custom = p.customModels.find { it.modelId == p.model }
                if (custom != null && custom.displayName.isNotBlank()) return custom.displayName
                if (p.model.isNotBlank()) return p.model
            }
            val custom = customModels.find { it.modelId == model }
            if (custom != null && custom.displayName.isNotBlank()) return custom.displayName
            if (model.isNotBlank()) return model
            return if (resolvedProtocol == ApiProtocol.OPENAI) DEFAULT_OPENAI_MODEL else DEFAULT_ANTHROPIC_MODEL
        }

    // Gson 反序列化不会应用 Kotlin 默认值，旧数据/缺失字段可能为 null，统一归一化避免 NPE
    fun normalized(): AiConfig = copy(
        protocol = protocol.orDefault(ApiProtocol.OPENAI),
        apiKey = apiKey.orDefault(""),
        baseUrl = baseUrl.orDefault(""),
        model = model.orDefault(""),
        customModels = customModels.orDefault(emptyList()),
        providers = providers.orDefault(emptyList()),
        skills = skills.orDefault(emptyList()),
        memories = memories.orDefault(emptyList())
    )

    companion object {
        val DEFAULT_KEY = ""
        val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
        val DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-20250514"
    }
}

data class ApiProvider(
    val name: String = "",
    val protocol: ApiProtocol = ApiProtocol.OPENAI,
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val useDefaultKey: Boolean = true,
    val customModels: List<CustomModel> = emptyList()
)

data class CustomModel(
    val modelId: String,
    val displayName: String = ""
)

data class SkillConfig(
    val path: String = "",
    val enabled: Boolean = true,
    val title: String = "",
    val readme: String = ""
)

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
)

data class ConversationData(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
    val summary: String = ""
)

data class MemoryItem(
    val id: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)