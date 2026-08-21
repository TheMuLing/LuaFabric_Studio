package com.luafabric.studio.falling.ui.editor.ai.tools

import java.io.File

class FileIOTool : ChatTool {
    override val name = "file_io"
    override val description = "Read or write files in the project. Supports reading file content, listing directory, and writing content."
    override val parameters: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "action" to mapOf(
                "type" to "string",
                "enum" to listOf("read", "write", "list"),
                "description" to "Action to perform: read (file content), write (write to file), list (directory listing)"
            ),
            "path" to mapOf(
                "type" to "string",
                "description" to "Relative path from project root or absolute path"
            ),
            "content" to mapOf(
                "type" to "string",
                "description" to "Content to write (only for write action)"
            )
        ),
        "required" to listOf("action", "path")
    )

    override suspend fun execute(args: Map<String, Any>, context: ToolContext): ToolResult {
        val action = args["action"] as? String ?: return ToolResult(false, "", "Missing 'action' argument")
        val path = args["path"] as? String ?: return ToolResult(false, "", "Missing 'path' argument")

        val file = File(path).let {
            if (it.isAbsolute) it else File(context.projectPath, path)
        }

        // Security: prevent reading outside project
        if (!file.absolutePath.startsWith(context.projectPath)) {
            return ToolResult(false, "", "Access denied: path is outside project directory")
        }

        return when (action) {
            "read" -> {
                if (!file.exists() || !file.isFile) return ToolResult(false, "", "File not found: $path")
                if (file.length() > 1024 * 1024) return ToolResult(false, "", "File too large (>1MB)")
                try {
                    ToolResult(true, file.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    ToolResult(false, "", "Read failed: ${e.message}")
                }
            }
            "write" -> {
                val content = args["content"] as? String ?: return ToolResult(false, "", "Missing 'content' for write action")
                try {
                    file.parentFile?.mkdirs()
                    file.writeText(content, Charsets.UTF_8)
                    ToolResult(true, "Written ${content.length} bytes to $path")
                } catch (e: Exception) {
                    ToolResult(false, "", "Write failed: ${e.message}")
                }
            }
            "list" -> {
                if (!file.exists() || !file.isDirectory) return ToolResult(false, "", "Directory not found: $path")
                try {
                    val listing = file.listFiles()?.sortedBy { it.name }?.joinToString("\n") { f ->
                        val type = if (f.isDirectory) "[DIR]" else "[FILE]"
                        "$type ${f.name} (${f.length()} bytes)"
                    } ?: "(empty)"
                    ToolResult(true, listing)
                } catch (e: Exception) {
                    ToolResult(false, "", "List failed: ${e.message}")
                }
            }
            else -> ToolResult(false, "", "Unknown action: $action")
        }
    }
}