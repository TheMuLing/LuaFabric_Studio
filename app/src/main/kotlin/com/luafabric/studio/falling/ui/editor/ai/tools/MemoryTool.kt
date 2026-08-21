package com.luafabric.studio.falling.ui.editor.ai.tools

import java.util.UUID

class MemoryTool(private val onAddMemory: (content: String) -> Unit) : ChatTool {
    override val name = "add_memory"
    override val description = "记住一条信息，用于后续对话参考。当用户要求你记住某件事、保存偏好、或记录重要信息时使用。"
    override val parameters: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "content" to mapOf(
                "type" to "string",
                "description" to "要记住的信息内容"
            )
        ),
        "required" to listOf("content")
    )

    override suspend fun execute(args: Map<String, Any>, context: ToolContext): ToolResult {
        val content = args["content"] as? String ?: return ToolResult(false, "", "Missing 'content' argument")
        if (content.isBlank()) return ToolResult(false, "", "Content cannot be empty")
        onAddMemory(content.trim())
        return ToolResult(true, "已记住：$content")
    }
}

class GetMemoriesTool(private val onGetMemories: () -> List<String>) : ChatTool {
    override val name = "get_memories"
    override val description = "获取所有已记住的信息。当需要回顾之前记住的内容时使用。"
    override val parameters: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to emptyMap<String, Any>(),
        "required" to emptyList<String>()
    )

    override suspend fun execute(args: Map<String, Any>, context: ToolContext): ToolResult {
        val memories = onGetMemories()
        if (memories.isEmpty()) return ToolResult(true, "没有已记住的信息。")
        return ToolResult(true, "已记住的信息：\n" + memories.mapIndexed { i, m -> "${i + 1}. $m" }.joinToString("\n"))
    }
}