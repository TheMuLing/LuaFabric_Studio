package com.luafabric.console.intercept

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.MessageQueue
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.luafabric.console.output.ClipboardHelper
import com.luafabric.console.ui.dp
import com.luafabric.studio.falling.core.console.MethodCallResult
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * F3：activity.newActivity 阻塞确认。
 * 互斥：同一时刻仅一个确认会话；并发同文件同参数 → 丢弃；不同参数 → 列表单选跳转；长参数 pop 展示。
 * 线程：Lua 线程 CountDownLatch 阻塞；主线程 re-entrant Looper 泵；阻塞期不触碰 Lua 栈。
 */
class NewActivityInterceptor(private val context: Context) {

    private val lock = AtomicBoolean(false)
    private val busy = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = CopyOnWriteArrayList<Entry>()

    @Volatile
    private var inFlightReq: Request? = null

    data class Request(val path: String, val dump: String)

    private class Waiter {
        val latch = CountDownLatch(1)
        @Volatile
        var allow = false
    }

    private class Entry(val req: Request, val waiter: Waiter)

    fun intercept(activity: Activity?, methodName: String?, args: Array<out Any?>?): MethodCallResult {
        if (methodName != "newActivity") return MethodCallResult.ALLOW
        val req = parse(args) ?: return MethodCallResult.ALLOW
        // 弹窗须用调用方 Activity（action context），app context 无窗口 token，show() 抛 BadTokenException
        val ctx = activity ?: context
        if (!lock.compareAndSet(false, true)) {
            // 已有确认会话进行中
            val entry = synchronized(busy) {
                val cur = inFlightReq
                if (cur != null && cur.path == req.path && cur.dump == req.dump) {
                    return MethodCallResult.veto() // 同文件同参数 → 丢弃
                }
                if (pending.any { it.req.path == req.path && it.req.dump == req.dump }) {
                    return MethodCallResult.veto() // 已入队同参 → 丢弃
                }
                Entry(req, Waiter()).also { pending.add(it) }
            }
            return blockOn(entry.waiter) // 不同参数 → 阻塞等列表决策
        }
        return try {
            confirmSession(ctx, req)
        } finally {
            lock.set(false)
        }
    }

    /** 首个请求：阻塞确认，随后循环处理待选列表，直到无新请求。 */
    private fun confirmSession(ctx: Context, first: Request): MethodCallResult {
        inFlightReq = first
        val firstW = blockDialog(ctx) { showConfirmDialog(it, first, ctx) }
        inFlightReq = null
        while (true) {
            val snapshot = synchronized(busy) {
                if (pending.isEmpty()) null else pending.toList().also { pending.clear() }
            } ?: break
            blockDialog(ctx) { showListDialog(it, snapshot, ctx) }
        }
        return if (firstW.allow) MethodCallResult.ALLOW else MethodCallResult.veto()
    }

    private fun parse(args: Array<out Any?>?): Request? {
        if (args == null) return null
        val path = args.firstOrNull { it is String } as? String ?: return null
        val params = args.filter { it !== path }
        val dump = params.joinToString(", ") { ArgDumper.dump(it) }
        return Request(path, dump)
    }

    /** 阻塞当前线程直到弹窗决策：主线程 re-entrant 泵，其余线程 latch。 */
    private fun blockDialog(ctx: Context, build: (Waiter) -> Unit): Waiter {
        val w = Waiter()
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                build(w)
                pumpMainLooper { w.latch.count > 0L }
            } else {
                val posted = CountDownLatch(1)
                mainHandler.post {
                    try {
                        build(w)
                    } catch (e: Exception) {
                        w.latch.countDown() // 弹窗失败 → 放行（取消），防死锁
                    } finally {
                        posted.countDown()
                    }
                }
                posted.await()
                w.latch.await()
            }
        } catch (e: Exception) {
            w.latch.countDown()
        }
        return w
    }

    private fun blockOn(w: Waiter): MethodCallResult {
        try {
            w.latch.await()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return if (w.allow) MethodCallResult.ALLOW else MethodCallResult.veto()
    }

    /** 主线程 re-entrant Looper 泵：逐条派发主队列消息直到条件满足。 */
    private fun pumpMainLooper(until: () -> Boolean) {
        val queue = Looper.getMainLooper().queue
        val next = MessageQueue::class.java.getDeclaredMethod("next")
        next.isAccessible = true
        while (!until()) {
            val msg = try {
                next.invoke(queue) as? Message ?: continue
            } catch (e: Exception) {
                return
            }
            if (msg.target != null) {
                try {
                    msg.target.dispatchMessage(msg)
                } catch (e: Exception) {
                    // 单条派发失败不阻断泵
                }
            }
            msg.recycle()
        }
    }

    private fun showConfirmDialog(w: Waiter, req: Request, ctx: Context) {
        val paramsText = summary(req.dump).ifBlank { "(无)" }
        val builder = AlertDialog.Builder(ctx)
            .setTitle("跳转确认")
            .setMessage("文件：${req.path}\n\n参数：$paramsText")
            .setCancelable(false)
            .setPositiveButton("允许") { _, _ ->
                w.allow = true
                w.latch.countDown()
            }
            .setNegativeButton("取消") { _, _ ->
                w.allow = false
                w.latch.countDown()
            }
        if (req.dump.length > SUMMARY_LEN) {
            builder.setNeutralButton("完整参数") { _, _ -> showParamsPop(req, ctx) }
        }
        builder.show()
    }

    private fun showListDialog(w: Waiter, snapshot: List<Entry>, ctx: Context) {
        val listView = ListView(ctx)
        val labels = snapshot.mapIndexed { i, e ->
            "${i + 1}. ${e.req.path}  [${summary(e.req.dump)}]"
        }
        listView.adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, labels)
        listView.setOnItemClickListener { _, _, pos, _ ->
            snapshot.forEachIndexed { i, e ->
                e.waiter.allow = (i == pos)
                e.waiter.latch.countDown()
            }
            w.latch.countDown()
        }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            showParamsPop(snapshot[pos].req, ctx)
            true
        }
        AlertDialog.Builder(ctx)
            .setTitle("多个跳转请求（选择执行）")
            .setView(listView)
            .setCancelable(false)
            .setNegativeButton("取消全部") { _, _ ->
                snapshot.forEach { e ->
                    e.waiter.allow = false
                    e.waiter.latch.countDown()
                }
                w.latch.countDown()
            }
            .show()
    }

    /** 长参数 pop 弹窗：可滚动可复制全文，非阻塞。 */
    private fun showParamsPop(req: Request, ctx: Context) {
        val scroll = ScrollView(ctx)
        val tv = TextView(ctx).apply {
            text = "文件：${req.path}\n\n${req.dump}"
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(ctx.dp(18), ctx.dp(12), ctx.dp(18), ctx.dp(12))
        }
        scroll.addView(tv)
        AlertDialog.Builder(ctx)
            .setTitle("完整参数")
            .setView(scroll)
            .setPositiveButton("复制") { _, _ ->
                ClipboardHelper.copy(ctx, "文件：${req.path}\n${req.dump}")
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun summary(dump: String): String =
        if (dump.length <= SUMMARY_LEN) dump else dump.substring(0, SUMMARY_LEN) + "…"

    companion object {
        private const val SUMMARY_LEN = 100
    }
}
