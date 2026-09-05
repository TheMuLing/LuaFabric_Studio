package com.luafabric.console.logcat

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * logcat 文件懒加载读取：按字节偏移分块读取（RandomAccessFile seek），
 * 逐字节收集后整体 UTF-8 解码，避免 readLine 的 Latin-1 破坏中文。
 */
class LogcatFileStore(private val file: File) {

    private var readOffset = 0L

    /** 从上次位置继续读取 maxLines 行；无新内容返回空列表。 */
    @Synchronized
    fun readChunk(maxLines: Int): List<String> {
        if (!file.exists() || file.length() <= readOffset) return emptyList()
        val lines = ArrayList<String>()
        try {
            val raf = RandomAccessFile(file, "r")
            try {
                raf.seek(readOffset)
                val buf = ByteArrayOutputStream(256)
                var b = raf.read()
                while (lines.size < maxLines && b != -1) {
                    if (b == '\n'.toInt()) {
                        lines.add(String(buf.toByteArray(), Charsets.UTF_8).trimEnd('\r'))
                        buf.reset()
                    } else {
                        buf.write(b)
                    }
                    b = raf.read()
                }
                readOffset = raf.filePointer
            } finally {
                raf.close()
            }
        } catch (e: Exception) {
            readOffset = file.length()
        }
        return lines
    }

    fun lineCount(): Long = file.length()

    fun file(): File = file
}
