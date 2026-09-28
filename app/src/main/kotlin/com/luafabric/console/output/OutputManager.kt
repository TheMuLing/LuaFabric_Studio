package com.luafabric.console.output

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 输出管理器：任意线程入队 → 缓冲池按文件存储 → 主线程批量差量通知刷新。
 * UI 只订阅 Listener，不做跨线程直接写。
 */
object OutputManager {

    interface Listener {
        fun onOutputsChanged()
    }

    private val idSeq = AtomicLong(1)
    private val pool = BufferPool()
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val main = Handler(Looper.getMainLooper())
    private val dirty = AtomicBoolean(false)

    @Volatile
    var currentFile: String = ""

    fun nextId(): Long = idSeq.incrementAndGet()

    /** 任意线程可调。追加到目标文件缓冲并触发主线程批量通知。 */
    fun append(entry: OutputEntry) {
        val key = if (entry.file.isBlank()) currentFile else entry.file
        pool.getOrCreate(key).append(entry)
        notifyChange()
    }

    fun bufferFor(file: String): OutputBuffer = pool.getOrCreate(file)

    fun buffers(): List<OutputBuffer> = pool.buffers()

    fun clearAll() {
        pool.clearAll()
        notifyChange()
    }

    /** 仅清空当前文件缓冲（会话内其他文件缓冲保留）。 */
    fun clearCurrentFile() {
        if (currentFile.isNotBlank()) {
            pool.getOrCreate(currentFile).clear()
            notifyChange()
        }
    }

    /**
     * 会话游标建立后，把早于 currentFile 设定的「兜底缓冲」（key=""，主线程/启动阶段 print）并入新文件键，
     * 保证主线程输出不被此后的文件过滤吞掉。同一会话只执行一次（移动后清空源）。
     */
    fun rebaseCatchAll(to: String) {
        if (to.isBlank()) return
        synchronized(pool) {
            val src = pool.getOrCreate("")
            val dst = pool.getOrCreate(to)
            if (src !== dst && src.size() > 0) {
                src.all().forEach(dst::append)
                src.clear()
            }
        }
        notifyChange()
    }

    fun addListener(l: Listener) = listeners.addIfAbsent(l)

    fun removeListener(l: Listener) = listeners.remove(l)

    private fun notifyChange() {
        if (dirty.compareAndSet(false, true)) {
            main.post {
                dirty.set(false)
                for (l in listeners) {
                    try {
                        l.onOutputsChanged()
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }
}
