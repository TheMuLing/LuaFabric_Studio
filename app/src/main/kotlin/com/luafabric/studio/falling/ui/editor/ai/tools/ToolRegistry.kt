package com.luafabric.studio.falling.ui.editor.ai.tools

import com.luafabric.studio.falling.ui.editor.ai.ToolCallInfo
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

data class ToolResult(
    val success: Boolean,
    val data: String,
    val error: String? = null
)

interface ChatTool {
    val name: String
    val description: String
    val parameters: Map<String, Any>
    suspend fun execute(args: Map<String, Any>, context: ToolContext): ToolResult
}

data class ToolContext(
    val projectPath: String,
    val onAskUser: suspend (title: String, options: List<String>) -> String?,
    val onOpenFile: suspend (filePath: String, startLine: Int, endLine: Int) -> Boolean,
    val onConfirmInMain: suspend (title: String, message: String) -> Boolean
)

class ToolRegistry {
    private val tools = mutableMapOf<String, ChatTool>()
    private val gson = Gson()

    fun register(tool: ChatTool) {
        tools[tool.name] = tool
    }

    fun getDefinitions(): List<com.luafabric.studio.falling.ui.editor.ai.ToolDefinition> {
        return tools.values.map { tool ->
            com.luafabric.studio.falling.ui.editor.ai.ToolDefinition(
                name = tool.name,
                description = tool.description,
                parameters = tool.parameters
            )
        }
    }

    suspend fun execute(toolCall: ToolCallInfo, context: ToolContext): ToolResult {
        val tool = tools[toolCall.name] ?: return ToolResult(false, "", "Tool not found: ${toolCall.name}")
        return try {
            val argsType = object : TypeToken<Map<String, Any>>() {}.type
            val args: Map<String, Any> = try {
                gson.fromJson(toolCall.arguments, argsType)
            } catch (_: Exception) {
                // Try parsing with JsonParser for malformed JSON
                try {
                    val obj = JsonParser.parseString(toolCall.arguments).asJsonObject
                    obj.entrySet().associate { it.key to parseJsonElement(it.value) }
                } catch (_: Exception) {
                    emptyMap()
                }
            }
            tool.execute(args, context)
        } catch (e: Exception) {
            ToolResult(false, "", "Tool execution error: ${e.message}")
        }
    }

    private fun parseJsonElement(element: com.google.gson.JsonElement): Any {
        return when {
            element.isJsonPrimitive -> {
                val prim = element.asJsonPrimitive
                when {
                    prim.isBoolean -> prim.asBoolean
                    prim.isNumber -> prim.asNumber
                    else -> prim.asString
                }
            }
            element.isJsonArray -> element.asJsonArray.map { parseJsonElement(it) }
            element.isJsonObject -> element.asJsonObject.entrySet().associate {
                it.key to parseJsonElement(it.value)
            }
            else -> ""
        }
    }

    fun getToolNames(): List<String> = tools.keys.toList()
}