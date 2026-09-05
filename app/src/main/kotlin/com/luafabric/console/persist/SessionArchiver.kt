package com.luafabric.console.persist

import com.luafabric.console.output.OutputExporter
import com.luafabric.console.output.OutputManager
import java.io.File

/**
 * 会话归档：会话结束时将全部输出缓冲按导出格式写入
 * sessions/<项目名>_<毫秒时间戳>/outputs.txt。缓冲本身保留（跨会话仍可查看）。
 */
object SessionArchiver {

    fun archive(projectName: String): File? {
        val buffers = OutputManager.buffers().filter { it.size() > 0 }
        if (buffers.isEmpty()) return null
        val dir = File(ConsolePaths.sessions(), "${projectName}_${System.currentTimeMillis()}")
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
