package com.luafabric.studio.falling.ui.editor.ai.tools

import java.io.File
import java.util.concurrent.TimeUnit

class ShellTool : ChatTool {
    override val name = "execute_shell"
    override val description = "Execute a shell command on the device. Returns stdout and stderr. Relative paths are resolved against the project root directory."
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
        val timeoutSeconds = ((args["timeout_seconds"] as? Number)?.toInt() ?: 30).coerceIn(1, 300)

        return try {
            // cwd 必须指向项目根：Runtime.exec 默认工作目录为 /（只读根文件系统），
            // 相对路径（如 test.txt）会落到根分区 → "Read-only file system"
            val cwd = File(context.projectPath).takeIf { it.isDirectory }
            val process = if (cwd != null) {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command), null, cwd)
            } else {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            }
            // 并发抽干 stdout/stderr，避免管道写满导致进程永不退出（旧实现读流阻塞无超时）
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val outPump = Thread { process.inputStream.bufferedReader().forEachLine { stdout.appendLine(it) } }
            val errPump = Thread { process.errorStream.bufferedReader().forEachLine { stderr.appendLine(it) } }
            outPump.isDaemon = true
            errPump.isDaemon = true
            outPump.start()
            errPump.start()

            val exited = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                outPump.join(500)
                errPump.join(500)
                return ToolResult(false, "", "Shell timeout after ${timeoutSeconds}s (command killed)")
            }
            outPump.join(1000)
            errPump.join(1000)

            val result = buildString {
                appendLine("Exit code: ${process.exitValue()}")
                if (stdout.isNotBlank()) appendLine("STDOUT:").appendLine(stdout.toString().trimEnd())
                if (stderr.isNotBlank()) appendLine("STDERR:").appendLine(stderr.toString().trimEnd())
            }
            ToolResult(true, result.trimEnd())
        } catch (e: Exception) {
            ToolResult(false, "", "Shell execution failed: ${e.message}")
        }
    }
}