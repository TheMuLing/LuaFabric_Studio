package com.luafabric.console.persist

import com.luafabric.console.output.OutputExporter
import com.luafabric.console.output.OutputManager
import com.luafabric.studio.falling.core.console.SessionInfo
import java.io.File

/**
 * 会话归档：会话结束时将该项目的输出缓冲按导出格式写入
 * projects/<项目哈希>/sessions/<毫秒时间戳>/outputs.txt。
 * 缓冲按项目目录前缀过滤，杜绝其他项目输出混入；缓冲本身保留（跨会话仍可查看）。
 */
object SessionArchiver {

    fun archive(info: SessionInfo): File? {
        val projectDirPath = info.luaDir
        val buffers = OutputManager.buffers()
            .filter { it.size() > 0 }
            .filter { projectDirPath.isNullOrBlank() || it.fileKey.startsWith(projectDirPath) }
        if (buffers.isEmpty()) return null
        val dir = File(ConsolePaths.projectSessions(projectDirPath), "${System.currentTimeMillis()}")
        if (!dir.mkdirs()) return null
        val sb = StringBuilder()
        for (b in buffers) {
            if (sb.isNotEmpty()) {
                sb.append("\n\n========== ").append(b.fileKey).append(" ==========\n\n")
            }
            sb.append(OutputExporter.export(b.all()))
        }
        val f = File(dir, "outputs.txt")
        return try {
            f.writeText(sb.toString())
            f
        } catch (_: Exception) {
            null
        }
    }
}
