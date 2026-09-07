package com.luafabric.console.ui.adapters

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.luafabric.console.core.ConsoleSettings
import com.luafabric.console.output.OutputEntry
import com.luafabric.console.ui.ConsoleTheme
import com.luafabric.console.ui.dp

/**
 * 输出条目适配器：
 * 一级常显 = 标签 + 内容 + 简略时间；二级默认折叠 = 完整毫秒时间 + 线程 + lua 类型解析。
 * 点击条目切换二级展开；长按进入多选。
 */
class OutputAdapter(
    private val settings: ConsoleSettings,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<OutputAdapter.VH>() {

    private val items = ArrayList<OutputEntry>()
    private val selected = LinkedHashSet<Long>()
    private val expanded = HashSet<Long>()

    var selectionMode: Boolean = false
        private set

    val selectionCount: Int get() = selected.size

    val selectedEntries: List<OutputEntry>
        get() = items.filter { it.id in selected }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<OutputEntry>) {
        items.clear()
        items.addAll(list)
        selected.retainAll(items.map { it.id })
        notifyDataSetChanged()
    }

    fun setSelectionMode(on: Boolean) {
        if (selectionMode == on) return
        selectionMode = on
        if (!on) selected.clear()
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun setShowMeta(show: Boolean) {
        expanded.clear()
        if (show) expanded.addAll(items.map { it.id })
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8))
        }
        val check = CheckBox(ctx).apply {
            visibility = View.GONE
            isClickable = false
        }
        val meta = TextView(ctx).apply {
            textSize = 11f
            setTextColor(ConsoleTheme.onSurfaceVariant)
            visibility = View.GONE
        }
        val content = TextView(ctx).apply {
            textSize = 14f
            setTextColor(ConsoleTheme.onSurface)
        }
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(check, LinearLayout.LayoutParams(ctx.dp(28), ctx.dp(28)))
        head.addView(content, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(head)
        root.addView(meta)
        val lp = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
        root.layoutParams = lp
        return VH(root, check, content, meta)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.content.text = buildString {
            append('[').append(e.label).append("] ")
            append(e.primary)
            append("\n").append(e.shortTime).append(" · ").append(e.file)
        }
        holder.meta.text = buildString {
            append(e.fullTime).append(" · 线程:").append(e.threadLabel)
            if (e.luaTypes.isNotEmpty()) {
                append(" · 类型:").append(e.luaTypes.joinToString(", "))
                append(" | ").append(e.typeDetails.joinToString(", "))
            }
        }
        val isSel = e.id in selected
        holder.check.isChecked = isSel
        holder.check.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.itemView.setBackgroundColor(
            if (isSel) ConsoleTheme.accentContainer else Color.TRANSPARENT
        )
        holder.meta.visibility = if (e.id in expanded || settings.showMeta) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelect(e.id)
            } else {
                if (e.id in expanded) expanded.remove(e.id) else expanded.add(e.id)
                notifyItemChanged(position)
            }
        }
        holder.itemView.setOnLongClickListener {
            if (!selectionMode) setSelectionMode(true)
            toggleSelect(e.id)
            true
        }
    }

    private fun toggleSelect(id: Long) {
        if (!selectionMode) setSelectionMode(true)
        if (selected.contains(id)) selected.remove(id) else selected.add(id)
        notifyDataSetChanged()
        onSelectionChanged()
    }

    class VH(
        itemView: View,
        val check: CheckBox,
        val content: TextView,
        val meta: TextView
    ) : RecyclerView.ViewHolder(itemView)
}
