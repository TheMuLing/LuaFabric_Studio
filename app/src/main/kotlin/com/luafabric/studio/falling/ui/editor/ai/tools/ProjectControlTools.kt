package com.luafabric.studio.falling.ui.editor.ai.tools

import java.io.File

/**
 * 项目控制能力（由宿主界面 CodeEditScreen 提供实现）：供 AI 工具调用，
 * 通过 UI 层触发「调试运行 / 构建项目 / 导入分析 / 获取语法错误」。
 */
data class ProjectOps(
    /** 调试运行项目（拉起入口文件并随 debugmode 激活调试控制台）。返回结果描述。 */
    val onDebugRunProject: suspend () -> String,
    /** 构建项目 APK。返回构建结果（成功=APK 路径；失败=以 "error:" 开头的描述）。 */
    val onBuildProject: suspend () -> String,
    /** 分析当前编辑文件的 import 建议。返回建议文本。 */
    val onImportAnalysis: suspend () -> String,
    /** 编译当前编辑文件并返回语法错误（无错误返回空/成功描述）。 */
    val onGetSyntaxErrors: suspend () -> String,
    /**
     * 当前编辑器快照（轻量单文件）：当前打开文件名 + 语法检查结论。
     * 注入每次请求的 system prompt，让 AI 无需用户提示即知正在编辑哪个文件及其错误。
     * 只检查活动文件（毫秒级），不做全量扫描（避免大项目 OOM）。
     */
    val onGetEditorSnapshot: suspend () -> String,
    /** 磁盘文件被 AI 工具修改后通知宿主刷新编辑器缓存（避免编辑器显示旧内容）。 */
    val onFileChanged: suspend (String) -> Unit = {},
    /**
     * AI 任意工具（file_io / execute_shell 等）执行一轮后，宿主统一把「已打开文件」与磁盘对齐：
     * 磁盘内容变了就刷新 buffer 和编辑器显示（依赖 onFileChanged 的即时钩子作为补充，双保险）。
     */
    val onSyncEditorsFromDisk: suspend () -> Unit = {},
    /** 校验单个 lua 文件语法，返回错误文本；无错误返回 null（用于 AI 写入后回验防呆 / get_syntax_errors 按路径查询）。 */
    val onValidateLuaFile: (String) -> String? = { null }
)

/** 调试运行项目。 */
class DebugRunProjectTool : ChatTool {
    override val name = "debug_run_project"
    override val description =
        "Launch/run the current project (debug mode, debug console activated when the project has debugmode enabled). Returns whether the launch succeeded."
    override val parameters: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to emptyMap<String, Any>(),
        "required" to emptyList<String>()
    )

    override suspend fun execute(args: Map<String, Any>, context: ToolContext): ToolResult {
        val msg = context.projectOps.onDebugRunProject()
        return ToolResult(msg.isBlank() || msg.startsWith("error:"), msg.substringAfter("error: ", msg))
    }
}

/** 构建项目。 */
class BuildProjectTool : ChatTool {
    override val name = "build_project"
    override val description =
        "Build the current project into an APK. Returns the APK path on success, or the error message on failure."
    override val parameters: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to emptyMap<String, Any>(),
        "required" to emptyList<String>()
    )

    override suspend fun execute(args: Map<String, Any>, context: ToolContext): ToolResult {
        val msg = context.projectOps.onBuildProject()
        val failed = msg.isBlank() || msg.startsWith("error:")
        return ToolResult(!failed, if (failed) msg.substringAfter("error: ", "构建失败") else "构建成功: $msg")
    }
}

/** import 导入分析。 */
class ImportAnalysisTool : ChatTool {
    override val name = "import_analysis"
    override val description =
        "Analyze the currently edited file and return import suggestions (classes referenced by uppercase identifiers with available candidates). Useful before adding require/import lines."
    override val parameters: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to emptyMap<String, Any>(),
        "required" to emptyList<String>()
    )

    override suspend fun execute(args: Map<String, Any>, context: ToolContext): ToolResult {
        val msg = context.projectOps.onImportAnalysis()
        return ToolResult(true, msg)
    }
}

/** 获取指定文件的语法错误：path 为空时检查当前活动文件；path 为相对/绝对路径时按路径查询。 */
class GetSyntaxErrorsTool : ChatTool {
    override val name = "get_syntax_errors"
    override val description =
        "Compile a lua file and return its syntax errors. Optional 'path' argument: relative path from project root (or absolute) to check any file; omitted = currently open file. Empty result means no syntax errors."
    override val parameters: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf(
                "type" to "string",
                "description" to "Relative path from project root or absolute path to the lua file (optional, defaults to the currently open file)"
            )
        ),
        "required" to emptyList<String>()
    )

    override suspend fun execute(args: Map<String, Any>, context: ToolContext): ToolResult {
        val path = args["path"] as? String
        if (path.isNullOrBlank()) {
            val msg = context.projectOps.onGetSyntaxErrors()
            return ToolResult(true, msg)
        }
        val file = File(path).let { if (it.isAbsolute) it else File(context.projectPath, path) }
        if (!file.isFile) return ToolResult(false, "", "文件不存在: $path")
        val err = context.projectOps.onValidateLuaFile(file.absolutePath)
        return ToolResult(err == null, err ?: "无语法错误: $path")
    }
}