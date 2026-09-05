package com.luafabric.console.output

/** 会话级缓冲池：每个 lua 文件独立输出缓冲，newActivity 切换后旧文件缓冲完整保留。 */
class BufferPool {

    private val buffers = LinkedHashMap<String, OutputBuffer>()

    @Synchronized
    fun getOrCreate(fileKey: String): OutputBuffer =
        buffers.getOrPut(fileKey) { OutputBuffer(fileKey) }

    @Synchronized
    fun buffers(): List<OutputBuffer> = ArrayList(buffers.values)

    @Synchronized
    fun clearAll() = buffers.clear()
}
