package com.luafabric.console.output

/**
 * 导出格式：每条空行分隔；多行内容原样显示；恒含完整一级+二级数据；
 * 时间只显示一次且为最完整版（MM-dd HH:mm:ss.SSS）。
 */
object OutputExporter {

    fun export(entries: List<OutputEntry>): String {
        val sb = StringBuilder()
        for (e in entries) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append('[').append(e.label).append("] ").append(e.file).append('\n')
            sb.append(e.primary).append('\n')
            sb.append(e.fullTime).append(" 线程:").append(e.threadLabel)
            if (e.luaTypes.isNotEmpty()) {
                sb.append(" 类型:").append(e.luaTypes.joinToString(", "))
                sb.append(" 解析:").append(e.typeDetails.joinToString(", "))
            }
        }
        return sb.toString()
    }
}
