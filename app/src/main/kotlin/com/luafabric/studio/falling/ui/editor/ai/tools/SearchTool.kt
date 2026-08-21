package com.luafabric.studio.falling.ui.editor.ai.tools

import java.io.File

class SearchTool : ChatTool {
    override val name = "search_code"
    override val description = "Search for keywords, function names, or patterns in the project codebase. Returns matching file paths and line numbers."
    override val parameters: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "Search keyword or pattern"
            ),
            "file_pattern" to mapOf(
                "type" to "string",
                "description" to "Optional file extension filter (e.g. .lua, .kt, .json)",
                "default" to ""
            ),
            "max_results" to mapOf(
                "type" to "number",
                "description" to "Maximum number of results (default 20)",
                "default" to 20
            )
        ),
        "required" to listOf("query")
    )

    override suspend fun execute(args: Map<String, Any>, context: ToolContext): ToolResult {
        val query = args["query"] as? String ?: return ToolResult(false, "", "Missing 'query' argument")
        val filePattern = (args["file_pattern"] as? String)?.takeIf { it.isNotBlank() }
        val maxResults = (args["max_results"] as? Number)?.toInt() ?: 20

        return try {
            val projectDir = File(context.projectPath)
            if (!projectDir.exists() || !projectDir.isDirectory) {
                return ToolResult(false, "", "Project directory not found: ${context.projectPath}")
            }

            val results = mutableListOf<String>()
            searchInDirectory(projectDir, query, filePattern, results, maxResults)

            if (results.isEmpty()) {
                ToolResult(true, "No results found for '$query'")
            } else {
                ToolResult(true, results.joinToString("\n"))
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Search failed: ${e.message}")
        }
    }

    private fun searchInDirectory(
        dir: File,
        query: String,
        filePattern: String?,
        results: MutableList<String>,
        maxResults: Int
    ) {
        if (results.size >= maxResults) return

        val files = dir.listFiles() ?: return
        for (file in files) {
            if (results.size >= maxResults) return
            if (file.isDirectory) {
                if (!file.name.startsWith(".") && file.name != "build") {
                    searchInDirectory(file, query, filePattern, results, maxResults)
                }
            } else if (file.isFile) {
                if (filePattern != null && !file.name.endsWith(filePattern, ignoreCase = true)) continue
                if (file.length() > 500 * 1024) continue // skip large files

                try {
                    val lines = file.readLines(Charsets.UTF_8)
                    lines.forEachIndexed { index, line ->
                        if (results.size >= maxResults) return@forEachIndexed
                        if (line.contains(query, ignoreCase = true)) {
                            val relativePath = file.absolutePath.removePrefix(dir.absolutePath).trimStart('/')
                            results.add("$relativePath:${index + 1}: ${line.trim().take(120)}")
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }
}