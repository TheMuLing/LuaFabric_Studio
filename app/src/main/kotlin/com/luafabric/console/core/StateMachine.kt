package com.luafabric.console.core

import java.util.concurrent.CopyOnWriteArrayList

enum class ConsoleState { IDLE, BALL, PANEL, CLOSED }

/**
 * 状态机：IDLE → BALL → PANEL → BALL / CLOSED。
 * 仅 CLOSED 状态消费音量下键以恢复浮球（其余状态不消费）。
 */
object StateMachine {

    interface Listener {
        fun onStateChanged(old: ConsoleState, new: ConsoleState)
    }

    @Volatile
    var state: ConsoleState = ConsoleState.IDLE
        private set

    private val listeners = CopyOnWriteArrayList<Listener>()

    @Synchronized
    fun transition(new: ConsoleState) {
        if (state == new) return
        val old = state
        state = new
        for (l in listeners) {
            try {
                l.onStateChanged(old, new)
            } catch (_: Throwable) {
            }
        }
    }

    fun addListener(l: Listener) = listeners.addIfAbsent(l)
    fun removeListener(l: Listener) = listeners.remove(l)
}
