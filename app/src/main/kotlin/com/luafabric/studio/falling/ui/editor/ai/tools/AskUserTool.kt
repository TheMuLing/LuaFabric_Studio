package com.luafabric.studio.falling.ui.editor.ai.tools

/**
 * AI 问询工具基类：经 onAskUser 回调让用户在 UI 中作答。
 * 每次提问 luafabric 都会为 options 自动追加「其他」项，用户可选它自行输入。
 */
abstract class AskUserToolBase(
    override val name: String,
    override val description: String,
    private val isMulti: Boolean
) : ChatTool {

    override val parameters: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "title" to mapOf(
                "type" to "string",
                "description" to "Question or prompt title"
            ),
            "options" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Available options for the user to choose from"
            ),
            "description" to mapOf(
                "type" to "string",
                "description" to "Additional context or description for the question"
            )
        ),
        "required" to listOf("title")
    )

    override suspend fun execute(args: Map<String, Any>, context: ToolContext): ToolResult {
        val title = args["title"] as? String ?: return ToolResult(false, "", "Missing 'title' argument")
        val description = args["description"] as? String ?: ""
        val options = (args["options"] as? List<*>)?.filterIsInstance<String>()
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        // 自动追加「其他」选项，让用户自行输入回答
        val withOther = options + "其他"

        context.onAskUser(
            title,
            description,
            withOther,
            isMulti
        )?.let { result ->
            val chosen = result.filter { it.isNotBlank() }
            if (chosen.isEmpty()) {
                return ToolResult(true, "User dismissed the prompt")
            }
            val content = if (isMulti) chosen.joinToString(";") else chosen.joinToString("")
            return ToolResult(true, "User response: $content")
        }
        return ToolResult(true, "User dismissed the prompt")
    }
}

/** 单选提问：让用户在选项中选择一项（可输入「其他」自定义）。 */
class AskUserChoiceTool : AskUserToolBase(
    name = "ask_user_choice",
    description = "Ask the user to choose ONE option from the given list. A free-text '其他' (Other) option is always appended automatically for custom input.",
    isMulti = false
)

/** 多选提问：让用户在选项中选择若干项（可输入「其他」自定义）。 */
class AskUserMultiChoiceTool : AskUserToolBase(
    name = "ask_user_multi_choice",
    description = "Ask the user to choose ONE OR MORE options from the given list. A free-text '其他' (Other) option is always appended automatically for custom input.",
    isMulti = true
)