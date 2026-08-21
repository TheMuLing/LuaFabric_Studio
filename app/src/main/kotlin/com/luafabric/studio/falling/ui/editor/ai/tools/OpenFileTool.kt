package com.luafabric.studio.falling.ui.editor.ai.tools

class OpenFileTool : ChatTool {
    override val name = "open_file"
    override val description = "Open a file in the editor and optionally select a specific line range. Use this to show the user relevant code sections."
    override val parameters: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "file_path" to mapOf(
                "type" to "string",
                "description" to "Relative path from project root or absolute path to the file"
            ),
            "start_line" to mapOf(
                "type" to "number",
                "description" to "Optional start line to select (1-based)",
                "default" to 1
            ),
            "end_line" to mapOf(
                "type" to "number",
                "description" to "Optional end line to select (1-based, inclusive)",
                "default" to 1
            ),
            "message" to mapOf(
                "type" to "string",
                "description" to "Optional message to explain to the user why this file is being opened"
            )
        ),
        "required" to listOf("file_path")
    )

    override suspend fun execute(args: Map<String, Any>, context: ToolContext): ToolResult {
        val filePath = args["file_path"] as? String ?: return ToolResult(false, "", "Missing 'file_path' argument")
        val startLine = (args["start_line"] as? Number)?.toInt() ?: 1
        val endLine = (args["end_line"] as? Number)?.toInt() ?: 1
        val message = args["message"] as? String ?: ""

        val fullPath = if (filePath.startsWith("/")) filePath else "${context.projectPath}/$filePath"

        // Ask user for confirmation in main area
        val confirmMessage = buildString {
            append("Open file: $filePath")
            if (startLine > 0 || endLine > 0) {
                append(" (lines $startLine-$endLine)")
            }
            if (message.isNotBlank()) {
                append("\n\n$message")
            }
            append("\n\nProceed?")
        }

        val confirmed = context.onConfirmInMain("Open File", confirmMessage)

        return if (confirmed) {
            context.onOpenFile(fullPath, startLine, endLine)
            ToolResult(true, "File opened: $filePath")
        } else {
            ToolResult(true, "User declined to open the file")
        }
    }
}