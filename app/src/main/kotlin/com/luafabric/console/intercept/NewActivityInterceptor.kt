package com.luafabric.console.intercept

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luafabric.console.core.SessionManager
import com.luafabric.console.ui.ConsoleTheme
import com.luafabric.console.ui.dp
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.core.console.MethodCallResult
import java.io.File
import java.lang.reflect.Method

/**
 * F3：activity.newActivity 拦截确认（B 版）——零阻塞「先拒后补」。
 *
 * 每次 newActivity 拦截 → 立即 veto(nil) 回 Lua（脚本不挂起继续跑），请求以折叠卡片入单会话弹窗列表；
 * 弹窗已显示时新请求 → 签名去重（已存在则无操作）后主线程动态插入卡片。
 * 卡片：左侧相对路径（./…），右侧 chevron（折叠 left / 展开 down）；点卡片非图标区 = 选中（高亮），
 * 仅点右侧图标折叠/展开；展开区为参数键值对（左参数名 + 右 Lua 层类型，值单行内联、过长截断）。
 * 允许：必须已选中一条；settle 后主线程重放该条目真实跳转（反射解析 newActivity 重载 + 原始 args 精确匹配）。
 * 取消/弹窗关闭/重放失败：全部弃跳，调用方恒收 nil。
 *
 * 线程模型：拦截方全部非阻塞（无 looper 泵、无锁等待）；弹窗/插卡/重放全部主线程。
 */
class NewActivityInterceptor(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 当前拦截会话；null = 无会话。 */
    @Volatile
    private var session: Session? = null

    private class Session(val ctx: Context) {
        val entries = LinkedHashMap<String, Entry>()
        val cards = LinkedHashMap<String, JumpCardView>()
        val listHost = LinearLayout(ctx) // 卡片容器（仅主线程读写）
        @Volatile
        var selectedKey: String? = null
        @Volatile
        var allowKey: String? = null
        @Volatile
        var settled = false
        var dialog: Dialog? = null
        var positiveButton: View? = null
    }

    private class Entry(
        val key: String,
        val req: Resolved,
        /** 调用方实例（LuaActivity/LuaActivityX），重放真实跳转的目标。 */
        val caller: Activity?,
        /** 原始 java 参数：重放按此精确匹配 newActivity 重载。 */
        val args: Array<out Any?>
    )

    /** 解析后的请求：目标绝对路径 + 相对路径展示 + 参数列表 + 签名。 */
    private class Resolved(
        val absPath: String,
        val relPath: String,
        val params: List<Param>,
        val signature: String
    )

    /** 单个参数：名称（无则 参数 N）、Lua 层类型、单行内联值。 */
    private class Param(val name: String, val luaType: String, val inline: String) {
        override fun toString() = "$name:$inline"
    }

    fun intercept(activity: Activity?, methodName: String?, args: Array<out Any?>?): MethodCallResult {
        if (methodName != "newActivity") return MethodCallResult.ALLOW
        if (args == null) return MethodCallResult.ALLOW
        val req = resolve(activity, args) ?: return MethodCallResult.ALLOW
        synchronized(this) {
            val cur = session
            if (cur != null) {
                synchronized(cur) {
                    if (cur.entries.containsKey(req.signature)) return MethodCallResult.veto() // 已存在 → 无操作
                    cur.entries[req.signature] = Entry(req.signature, req, activity, args)
                }
                mainHandler.post { appendCardIfAbsent(cur, req.signature) }
            } else {
                val ns = Session(activity ?: context)
                ns.entries[req.signature] = Entry(req.signature, req, activity, args)
                session = ns
                mainHandler.post { openDialog(ns) }
            }
        }
        return MethodCallResult.veto() // B：恒否决回 Lua，脚本继续；放行在 settle 后重放
    }

    // ---------- 解析 ----------

    private fun resolve(activity: Activity?, args: Array<out Any?>): Resolved? {
        val raw = args.filterIsInstance<String>().firstOrNull() ?: return null
        val luaDir = SessionManager.luaDirOf(activity) ?: SessionManager.current?.luaDir
        if (luaDir.isNullOrEmpty()) return null // 无会话上下文，防误拦直接放行
        // 与 LuaActivity.newActivity 同款路径解析
        var p = if (raw.startsWith("/")) raw else "$luaDir/$raw"
        val f = File(p)
        if (f.isDirectory && File("$p/main.lua").exists()) p += "/main.lua"
        else if ((f.isDirectory || !f.exists()) && !p.endsWith(".lua")) p += ".lua"
        val abs = p
        val rel = if (abs.startsWith(luaDir)) "./" + abs.removePrefix(luaDir).trimStart('/') else abs
        val other = args.filter { it !== raw }
        val params = if (other.size == 1 && other[0] is Map<*, *>) {
            (other[0] as Map<*, *>).flatMap { (k, v) ->
                listOf(Param(k?.toString() ?: "?", luaTypeOf(v), luaInlineOf(v, 0)))
            }
        } else {
            other.mapIndexed { i, v -> Param("参数 ${i + 1}", luaTypeOf(v), luaInlineOf(v, 0)) }
        }
        val signature = "$abs|${ArgDumper.dump(other)}"
        return Resolved(abs, rel, params, signature)
    }

    // ---------- 弹窗（主线程） ----------

    private fun openDialog(s: Session) {
        if (s.settled) return
        val scroll = ScrollView(s.ctx)
        s.listHost.orientation = LinearLayout.VERTICAL
        s.listHost.setPadding(s.ctx.dp(14), s.ctx.dp(4), s.ctx.dp(14), s.ctx.dp(6))
        scroll.addView(s.listHost)
        appendAllCards(s)
        val dlg = MaterialAlertDialogBuilder(s.ctx)
            .setTitle("跳转拦截")
            .setView(scroll)
            .setPositiveButton("允许") { _, _ -> settle(s, s.selectedKey) }
            .setNegativeButton("取消") { _, _ -> settle(s, null) }
            .setCancelable(false)
            .create()
        s.dialog = dlg
        dlg.setOnDismissListener { if (!s.settled) settle(s, null) }
        try {
            dlg.show()
        } catch (e: Exception) {
            settle(s, null) // 弹窗失败（宿主已销毁等）→ 全部弃跳，防悬挂
            return
        }
        styleDialog(dlg)
        val positive = dlg.getButton(DialogInterface.BUTTON_POSITIVE)
        val negative = dlg.getButton(DialogInterface.BUTTON_NEGATIVE)
        negative.setTextColor(ConsoleTheme.primary)
        positive.isEnabled = false // 未选中前不可允许
        s.positiveButton = positive
    }

    /** 弹窗主题化：窗口背景 surface + 圆角（md3 28dp）。 */
    private fun styleDialog(dlg: Dialog) {
        try {
            dlg.window?.setBackgroundDrawable(
                GradientDrawable().apply {
                    setColor(ConsoleTheme.surface)
                    cornerRadius = dlg.context.dp(28).toFloat()
                }
            )
        } catch (e: Exception) {
        }
    }

    /** 结算：放行选中条（主线程重放真实跳转）/ 其余与取消全部弃跳。幂等。 */
    private fun settle(s: Session, allowKey: String?) {
        synchronized(s) {
            if (s.settled) return
            s.settled = true
            s.allowKey = allowKey
        }
        try {
            s.dialog?.dismiss()
        } catch (e: Exception) {
        }
        if (allowKey != null) {
            s.entries[allowKey]?.let { reExec(it) }
        }
        synchronized(this) {
            if (session === s) session = null
        }
    }

    // ---------- 重放：真实跳转 ----------

    /** 重放选中条目的真实 newActivity：反射重载解析 + 原始 args 精确匹配。失败安全吞、不崩。 */
    private fun reExec(e: Entry) {
        val caller = e.caller ?: return
        if (caller.isFinishing || caller.isDestroyed) return
        try {
            val method = resolveOverload(caller, "newActivity", e.args) ?: return // 匹配失败 → 弃跳
            method.invoke(caller, *e.args)
        } catch (t: Throwable) {
            // 重放失败（FileNotFound 等）→ 静默弃跳
        }
    }

    /** 按调用方类型找 newActivity 重载：参数个数 + 可赋性（含基本类型拆箱）。多义 → null（安全失败）。 */
    private fun resolveOverload(caller: Any, name: String, args: Array<out Any?>): Method? {
        var best: Method? = null
        for (m in caller.javaClass.methods) {
            if (m.name != name || m.parameterTypes.size != args.size) continue
            var ok = true
            for (i in m.parameterTypes.indices) {
                if (!assignable(m.parameterTypes[i], args[i])) {
                    ok = false
                    break
                }
            }
            if (ok) {
                if (best != null) return null
                best = m
            }
        }
        return best
    }

    private fun assignable(param: Class<*>, arg: Any?): Boolean {
        if (arg == null) return !param.isPrimitive
        if (!param.isPrimitive) return param.isInstance(arg)
        return when (param.name) {
            "int" -> arg is Int
            "long" -> arg is Long
            "boolean" -> arg is Boolean
            "double" -> arg is Double
            "float" -> arg is Float
            "short" -> arg is Short
            "byte" -> arg is Byte
            "char" -> arg is Char
            else -> false
        }
    }

    // ---------- 卡片 ----------

    /** 幂等插卡（已存在卡片跳过）。主线程调用。 */
    private fun appendAllCards(s: Session) {
        s.entries.values.forEach { e ->
            if (!s.cards.containsKey(e.key)) s.listHost.addView(cardFor(s, e))
        }
    }

    /** 会话寿命内动态追加单卡。主线程调用。 */
    private fun appendCardIfAbsent(s: Session, key: String) {
        if (s.settled) return
        val e = s.entries[key] ?: return
        if (!s.cards.containsKey(key)) s.listHost.addView(cardFor(s, e))
    }

    private fun cardFor(s: Session, e: Entry): JumpCardView {
        val card = JumpCardView(s.ctx, e.req.relPath, e.req.params)
        card.onSelect = {
            if (!s.settled) {
                s.selectedKey = e.key
                s.cards.forEach { (k, other) -> other.setSelected(k == e.key) }
                s.positiveButton?.isEnabled = true
                (s.positiveButton as? TextView)?.setTextColor(ConsoleTheme.primary)
            }
        }
        s.cards[e.key] = card
        return card
    }

    /** 折叠参数卡片：头行（左相对路径 + 右 chevron）+ 参数区。点非图标区选中高亮，点图标折叠/展开。 */
    private inner class JumpCardView(
        context: Context,
        val pathLabel: String,
        val params: List<Param>
    ) : LinearLayout(context) {

        var onSelect: (() -> Unit)? = null

        private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private val arrow = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.ic_chevron_left)
            colorFilter = PorterDuffColorFilter(
                ConsoleTheme.onSurfaceVariant, PorterDuff.Mode.SRC_IN
            )
            layoutParams = LayoutParams(context.dp(18), context.dp(18))
            isClickable = true
            setOnClickListener {
                onSelect?.invoke() // 点图标同样算选中
                toggle()           // 并折叠/展开
            }
        }
        private val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
            setOnClickListener { onSelect?.invoke() } // 其余区域 → 选中
        }
        private var expanded = false
        private var selected = false

        init {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(4) // 卡片间距 4dp
            }
            header.addView(
                TextView(context).apply {
                    text = pathLabel
                    textSize = 14f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(ConsoleTheme.primary)
                    setPadding(0, 0, context.dp(8), 0)
                },
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            )
            if (params.isNotEmpty()) header.addView(arrow) // 无参数 → 无折叠内容，不显箭头
            addView(header)
            refreshHeader()

            if (params.isNotEmpty()) {
                body.isClickable = true
                body.setOnClickListener { onSelect?.invoke() } // 点展开区同样算选中
                body.setPadding(context.dp(12), context.dp(2), context.dp(12), context.dp(8))
                // 展开区：首行「携带参数：」独占一行，小号
                body.addView(
                    TextView(context).apply {
                        text = "携带参数："
                        textSize = 11f
                        setTextColor(ConsoleTheme.onSurfaceVariant)
                        setPadding(0, 0, 0, context.dp(2))
                    }
                )
                // 参数条目：自动名（参数 N）不显示文本，条目间细分割线分隔（不贴两边）
                params.forEachIndexed { i, p ->
                    if (i > 0) body.addView(divider())
                    val auto = p.name.startsWith("参数 ")
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, context.dp(6), 0, context.dp(6))
                    }
                    row.addView(
                        TextView(context).apply {
                            text = if (auto) p.inline.ifEmpty { p.luaType } else p.name
                            textSize = 13f
                            setTextColor(
                                if (auto) ConsoleTheme.onSurfaceVariant else ConsoleTheme.onSurface
                            )
                            setSingleLine(true)
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            setPadding(0, 0, context.dp(8), 0)
                        },
                        LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                    )
                    row.addView(
                        TextView(context).apply {
                            text = p.luaType
                            textSize = 12f
                            setTextColor(ConsoleTheme.primary)
                            setPadding(context.dp(8), context.dp(2), context.dp(8), context.dp(2))
                        }
                    )
                    body.addView(row)
                    // 具名参数：内联值值与类型不同 → 另起一行
                    if (!auto && p.inline.isNotEmpty() && p.inline != p.luaType) {
                        body.addView(
                            TextView(context).apply {
                                text = p.inline
                                textSize = 12f
                                setTextColor(ConsoleTheme.onSurfaceVariant)
                                setSingleLine(true)
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            }
                        )
                    }
                }
                // 展开区背景左右收窄，圆角无缝隙；默认折叠
                val bodyLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                bodyLp.leftMargin = context.dp(8)
                bodyLp.rightMargin = context.dp(8)
                body.layoutParams = bodyLp
                body.visibility = View.GONE
                addView(body)
            }
        }

        /** 细分割线：1dp，左右不贴边。 */
        private fun divider(): View {
            val base = ConsoleTheme.onSurfaceVariant
            return View(context).apply {
                setBackgroundColor(
                    android.graphics.Color.argb(
                        0x30,
                        android.graphics.Color.red(base),
                        android.graphics.Color.green(base),
                        android.graphics.Color.blue(base)
                    )
                )
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, context.dp(1)).apply {
                    leftMargin = context.dp(4)
                    rightMargin = context.dp(4)
                }
            }
        }

        private fun refreshHeader() {
            header.background = GradientDrawable().apply {
                setColor(
                    if (selected) ConsoleTheme.accentContainer
                    else ConsoleTheme.surfaceContainer
                )
                cornerRadius = context.dp(12).toFloat()
                if (selected) {
                    setStroke(context.dp(1).toInt(), ConsoleTheme.primary)
                }
            }
            body.background = GradientDrawable().apply {
                setColor(
                    if (selected) ConsoleTheme.accentContainer
                    else (ConsoleTheme.surfaceContainer and 0x00FFFFFF) or 0x12000000
                )
                cornerRadii = floatArrayOf(
                    0f, 0f, 0f, 0f,
                    context.dp(12).toFloat(), context.dp(12).toFloat(),
                    context.dp(12).toFloat(), context.dp(12).toFloat()
                )
            }
        }

        override fun setSelected(on: Boolean) {
            selected = on
            refreshHeader()
        }

        private fun setExpanded(on: Boolean) {
            expanded = on
            body.visibility = if (on) View.VISIBLE else View.GONE
            arrow.setImageResource(if (on) R.drawable.ic_chevron_down else R.drawable.ic_chevron_left)
        }

        private fun toggle() = setExpanded(!expanded)
    }

    // ---------- 类型/内联 ----------

    private fun luaTypeOf(v: Any?): String = when (v) {
        null -> "nil"
        is String -> "string"
        is Boolean -> "boolean"
        is Number -> "number"
        is Map<*, *>, is Collection<*>, is Array<*> -> "table"
        else -> "userdata"
    }

    private fun luaInlineOf(v: Any?, depth: Int): String = try {
        if (depth > 3) "…"
        else when {
            v == null -> "nil"
            v is String -> "\"" + v + "\""
            v is Boolean || v is Number -> v.toString()
            v is Map<*, *> -> mapInline(v, depth)
            v is Collection<*> -> listInline(v.toList(), depth)
            v is Array<*> -> listInline(v.toList(), depth)
            else -> v.toString()
        }
    } catch (e: Exception) {
        "<unprintable>"
    }

    /** 类 Lua 打印的 table 内联：序号数组省略键 {1, 2, …}，命名键 k=v。单行截断。 */
    private fun mapInline(m: Map<*, *>, depth: Int): String {
        val entries = m.entries.toList()
        val isList = entries.all { (k, _) -> k is Number }
        val sb = StringBuilder("{")
        val limit = 24
        entries.take(limit).forEachIndexed { i, (k, value) ->
            if (i > 0) sb.append(", ")
            if (isList) sb.append(luaInlineOf(value, depth + 1))
            else sb.append(k).append('=').append(luaInlineOf(value, depth + 1))
        }
        if (entries.size > limit) sb.append(", …")
        sb.append('}')
        return truncate(sb.toString())
    }

    private fun listInline(items: List<Any?>, depth: Int): String {
        val sb = StringBuilder("{")
        val limit = 24
        items.take(limit).forEachIndexed { i, v ->
            if (i > 0) sb.append(", ")
            sb.append(luaInlineOf(v, depth + 1))
        }
        if (items.size > limit) sb.append(", …")
        sb.append('}')
        return truncate(sb.toString())
    }

    private fun truncate(s: String): String =
        if (s.length <= 120) s else s.substring(0, 120) + "…"
}