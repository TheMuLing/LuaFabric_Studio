package com.luafabric.studio.falling.ui.editor.ai.tools

import java.io.File

class ShellTool : ChatTool {
    override val name = "execute_shell"
    override val description = "Execute a shell command on the device. Returns stdout and stderr."
    override val parameters: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "command" to mapOf(
                "type" to "string",
                "description" to "Shell command to execute"
            ),
            "timeout_seconds" to mapOf(
                "type" to "number",
                "description" to "Timeout in seconds (default 30)",
                "default" to 30
            )
        ),
        "required" to listOf("command")
    )

    override suspend fun execute(args: Map<String, Any>, context: ToolContext): ToolResult {
        val command = args["command"] as? String ?: return ToolResult(false, "", "Missing 'command' argument")
        val timeoutSeconds = (args["timeout_seconds"] as? Number)?.toInt() ?: 30

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            val result = buildString {
                appendLine("Exit code: $exitCode")
                if (stdout.isNotBlank()) appendLine("STDOUT:").appendLine(stdout.trimEnd())
                if (stderr.isNotBlank()) appendLine("STDERR:").appendLine(stderr.trimEnd())
            }
            ToolResult(true, result.trimEnd())
        } catch (e: Exception) {
            ToolResult(false, "", "Shell execution failed: ${e.message}")
        }
    }
}