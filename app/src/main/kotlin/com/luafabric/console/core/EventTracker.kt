package com.luafabric.console.core

import com.luafabric.console.intercept.ArgDumper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 事件页模型：仅记录 Lua 侧显式调用且实际触发的 runFunc 条目
 * （core 侧已用 ConsoleCallMarker 门控），含相对路径 + 毫秒时间 + 参数摘要。
 */
object EventTracker {

    class EventEntry(
        val timeMs: Long,
        val funcName: String,
        val argsSummary: String,
        val fileLabel: String,
        val isMainThread: Boolean
    ) {
        fun timeLabel(): String = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timeMs))
    }

    private const val MAX_EVENTS = 500

    private val lock = Any()
    private val events = ArrayList<EventEntry>()

    fun record(
        file: String,
        funcName: String,
        args: Array<out Any?>?,
        timeMs: Long,
        isMainThread: Boolean
    ) {
        val argCount = args?.size ?: 0
        val summary = if (argCount == 0) {
            "(无参数)"
        } else {
            args!!.joinToString(", ") { ArgDumper.dump(it) }.let {
                if (it.length > 120) it.substring(0, 120) + "…" else it
            }
        }
        val entry = EventEntry(
            timeMs = timeMs,
            funcName = funcName,
            argsSummary = "$argCount 参 · $summary",
            fileLabel = file.ifBlank { "(无)" },
            isMainThread = isMainThread
        )
        synchronized(lock) {
            events.add(entry)
            if (events.size > MAX_EVENTS) events.removeAt(0)
        }
    }

    /** 新→旧。 */
    fun snapshot(): List<EventEntry> = synchronized(lock) {
        events.toList().asReversed()
    }

    fun clear() = synchronized(lock) { events.clear() }
}
