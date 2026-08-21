package com.luafabric.studio.falling.ui.editor.ai.tools

class AskUserTool : ChatTool {
    override val name = "ask_user"
    override val description = "Ask the user a question and get their response. Use this when you need user input, confirmation, or a choice between options."
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
                "description" to "Available options for the user to choose from. If empty, user can type freely."
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
        val options = (args["options"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        val result = context.onAskUser(title, options)

        return if (result != null) {
            ToolResult(true, "User response: $result")
        } else {
            ToolResult(true, "User dismissed the prompt")
        }
    }
}