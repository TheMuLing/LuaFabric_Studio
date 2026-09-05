package com.luafabric.console.output

/** 单文件输出缓冲，容量封顶防止内存无限增长。所有操作要求外部串行化（由 BufferPool 锁保证）。 */
class OutputBuffer(val fileKey: String, private val maxEntries: Int = MAX_ENTRIES) {

    private val entries = ArrayList<OutputEntry>()

    fun append(entry: OutputEntry) {
        entries.add(entry)
        while (entries.size > maxEntries) entries.removeAt(0)
    }

    fun all(): List<OutputEntry> = ArrayList(entries)

    fun clear() = entries.clear()

    fun size(): Int = entries.size

    companion object {
        const val MAX_ENTRIES = 2000
    }
}
