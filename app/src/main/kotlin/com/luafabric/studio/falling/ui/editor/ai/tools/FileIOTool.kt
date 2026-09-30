package com.luafabric.studio.falling.ui.editor.ai.tools

import java.io.File

class FileIOTool : ChatTool {
    override val name = "file_io"
    override val description =
        "Read or write files in the project. Supports reading file content, reading specific line(s) (read_lines with 'line' for one line or 'start'+'end' for a line range, 1-based), listing directory, and writing content."
    override val parameters: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "action" to mapOf(
                "type" to "string",
                "enum" to listOf("read", "write", "list", "read_lines"),
                "description" to "Action to perform: read (file content), write (write to file), list (directory listing), read_lines (read specific line(s))"
            ),
            "path" to mapOf(
                "type" to "string",
                "description" to "Relative path from project root or absolute path"
            ),
            "content" to mapOf(
                "type" to "string",
                "description" to "Content to write (only for write action)"
            ),
            "line" to mapOf(
                "type" to "integer",
                "description" to "Single line number to read, 1-based (only for read_lines action; exclusive with start/end)"
            ),
            "start" to mapOf(
                "type" to "integer",
                "description" to "Start line of range to read, 1-based, inclusive (only for read_lines action, requires 'end')"
            ),
            "end" to mapOf(
                "type" to "integer",
                "description" to "End line of range to read, 1-based, inclusive (only for read_lines action, requires 'start')"
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
                    // 保留旧内容用于失败回滚（防 AI 写坏文件）
                    val oldContent = if (file.exists()) file.readText(Charsets.UTF_8) else null
                    file.writeText(content, Charsets.UTF_8)
                    // 写入后语法回验：仅 lua 文件，失败即回滚，避免模型转义损坏写坏源码
                    val validateErr = if (path.endsWith(".lua", ignoreCase = true)) {
                        context.projectOps.onValidateLuaFile(file.absolutePath)
                    } else null
                    if (validateErr != null) {
                        if (oldContent != null) {
                            file.writeText(oldContent, Charsets.UTF_8)
                        } else {
                            file.delete()
                        }
                        context.projectOps.onFileChanged(file.absolutePath)
                        return ToolResult(false, "", "写入后语法检查失败，已回滚（保留原内容）：$validateErr")
                    }
                    // 通知宿主刷新编辑器缓存（读盘最新内容），避免编辑区仍显示旧 buffer
                    context.projectOps.onFileChanged(file.absolutePath)
                    ToolResult(true, "Written ${content.length} bytes to $path")
                } catch (e: Exception) {
                    ToolResult(false, "", "Write failed: ${e.message}")
                }
            }
            "read_lines" -> {
                val line = (args["line"] as? Number)?.toInt()
                val start = (args["start"] as? Number)?.toInt()
                val end = (args["end"] as? Number)?.toInt()
                if (line == null && (start == null || end == null)) {
                    return ToolResult(false, "", "read_lines 需要 line（单行，1 基）或 start+end（闭区间 N~M，1 基）")
                }
                if (!file.exists() || !file.isFile) return ToolResult(false, "", "File not found: $path")
                if (file.length() > 1024 * 1024) return ToolResult(false, "", "File too large (>1MB)")
                try {
                    val lines = file.readLines(Charsets.UTF_8)
                    if (line != null) {
                        if (line < 1 || line > lines.size) {
                            return ToolResult(false, "", "行号越界: $line（文件共 ${lines.size} 行）")
                        }
                        ToolResult(true, "$line: ${lines[line - 1]}")
                    } else {
                        var s = start!!
                        var e = end!!
                        if (s > e) { val t = s; s = e; e = t }
                        val from = maxOf(s, 1)
                        val to = minOf(e, lines.size)
                        if (from > lines.size) {
                            return ToolResult(false, "", "行号越界: $s（文件共 ${lines.size} 行）")
                        }
                        val out = StringBuilder("第 $from~$to 行：\n")
                        for (i in from..to) {
                            out.appendLine("$i: ${lines[i - 1]}")
                        }
                        ToolResult(true, out.toString())
                    }
                } catch (e: Exception) {
                    ToolResult(false, "", "Read lines failed: ${e.message}")
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