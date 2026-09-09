package com.luafabric.console.ui.tabs

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.luafabric.console.core.SessionManager
import com.luafabric.console.intercept.NewActivityInterceptor
import com.luafabric.console.ui.ConsoleTheme
import com.luafabric.console.ui.dp
import com.luafabric.studio.falling.R
import java.io.File

/**
 * 结构页：当前项目文件树（惰性展开）+ 右上角悬浮操作栏。
 * - 行布局：折叠指示器(menu-right/menu-down) + 文件(夹)图标(file/folder/folder-open) + 名称。
 * - 单选互斥：再点已选取消；文件夹仅折叠/展开不参与选中。
 * - 选中态行容器变色(accentContainer)，行有波纹；悬浮栏仅选 .lua 显示，golf 直达 newActivity（绕过拦截器）。
 * - 颜色三层区分：页签 surface → 父圆角 surfaceContainer → 图标容器 primary → 图标 onPrimary。
 */
class StructTabView(context: Context) : FrameLayout(context) {

    private val expanded = HashSet<String>()
    private var selectedPath: String? = null
    private var overlay: LinearLayout? = null

    private val tree = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context)

    init {
        setBackgroundColor(ConsoleTheme.surface)
        scroll.addView(tree)
        addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    /** 页签展示时刷新：重扫项目根，展开态/选中全复位（保持与「当前项目」一致）。 */
    fun refresh() {
        expanded.clear()
        selectedPath = null
        removeOverlay()
        val dir = SessionManager.current?.luaDir
        tree.removeAllViews()
        if (dir.isNullOrEmpty() || !File(dir).isDirectory) {
            tree.addView(emptyHint("(无项目运行)"))
            return
        }
        expanded.add(dir) // 根默认展开
        addNode(tree, File(dir), 0)
    }

    private fun emptyHint(text: String) = TextView(context).apply {
        this.text = text
        textSize = 13f
        setTextColor(ConsoleTheme.onSurfaceVariant)
        setPadding(context.dp(12), context.dp(20), context.dp(12), context.dp(20))
    }

    /** 递归构建节点行；目录命中展开集合才递归子项——惰性，避免大项目全量造 View。 */
    private fun addNode(parent: LinearLayout, file: File, depth: Int) {
        parent.addView(nodeRow(file, depth))
        if (!file.isDirectory || !expanded.contains(file.path)) return
        val children = file.listFiles() ?: return
        children.sortWith(compareBy({ it.isFile }, { it.name.lowercase() }))
        for (c in children) addNode(parent, c, depth + 1)
    }

    private fun nodeRow(file: File, depth: Int): LinearLayout {
        val isDir = file.isDirectory
        val open = isDir && expanded.contains(file.path)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(10 + depth * 16), context.dp(9), context.dp(10), context.dp(9))
            background = rowBg()
            isSelected = selectedPath == file.path
            tag = file.path
            setOnClickListener { onNodeClick(file) }
        }

        // 折叠指示器：仅目录显示；文件留位对齐
        val indicator = ImageView(context).apply {
            if (!isDir) visibility = INVISIBLE
            setImageResource(if (open) R.drawable.ic_menu_down else R.drawable.ic_menu_right)
            colorFilter = PorterDuffColorFilter(ConsoleTheme.onSurfaceVariant, PorterDuff.Mode.SRC_IN)
        }
        row.addView(indicator, LinearLayout.LayoutParams(context.dp(24), context.dp(24)))

        // 文件/文件夹图标：目录展开 folder-open，折叠 folder；文件 file
        row.addView(
            ImageView(context).apply {
                if (isDir) {
                    setImageResource(if (open) R.drawable.ic_folder_open else R.drawable.ic_folder)
                    colorFilter = PorterDuffColorFilter(ConsoleTheme.primary, PorterDuff.Mode.SRC_IN)
                } else {
                    setImageResource(R.drawable.ic_file)
                    colorFilter = PorterDuffColorFilter(ConsoleTheme.onSurfaceVariant, PorterDuff.Mode.SRC_IN)
                }
            },
            LinearLayout.LayoutParams(context.dp(24), context.dp(24)).apply { marginStart = context.dp(6) }
        )

        // 名称
        row.addView(
            TextView(context).apply {
                text = file.name
                textSize = 14f
                setTextColor(ConsoleTheme.onSurface)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(context.dp(8), 0, 0, 0)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        return row
    }

    /** 行背景：波纹(onSurface 15%) 叠在 StateList 上——选中 accentContainer，未选透明。 */
    private fun rowBg() = RippleDrawable(
        ColorStateList.valueOf(
            Color.argb(
                0x26,
                Color.red(ConsoleTheme.onSurface),
                Color.green(ConsoleTheme.onSurface),
                Color.blue(ConsoleTheme.onSurface)
            )
        ),
        StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_selected),
                GradientDrawable().apply { setColor(ConsoleTheme.accentContainer) }
            )
            addState(intArrayOf(), GradientDrawable().apply { setColor(Color.TRANSPARENT) })
        },
        null
    )

    private fun onNodeClick(file: File) {
        // 目录：折叠/展开（不参与选中），重建可见行
        if (file.isDirectory) {
            if (expanded.contains(file.path)) expanded.remove(file.path) else expanded.add(file.path)
            tree.removeAllViews()
            SessionManager.current?.luaDir?.let { addNode(tree, File(it), 0) }
            return
        }
        // 文件：单选互斥，再点已选取消；可见行平铺于 tree，逐行回写选中态避免整树重建
        selectedPath = if (selectedPath == file.path) null else file.path
        for (i in 0 until tree.childCount) {
            val child = tree.getChildAt(i)
            (child as? LinearLayout)?.isSelected = child.tag == selectedPath
        }
        updateOverlay()
    }

    // ---------- 右上角悬浮操作栏 ----------

    private fun updateOverlay() {
        val sel = selectedPath
        if (sel != null && sel.endsWith(".lua")) {
            if (overlay == null) attachOverlay()
        } else {
            removeOverlay()
        }
    }

    private fun attachOverlay() {
        overlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = context.dp(6).toInt()
            setPadding(p, p, p, p)
            // 父圆角矩形：surfaceContainer，与页签 surface 区分
            background = GradientDrawable().apply {
                setColor(ConsoleTheme.surfaceContainer)
                cornerRadius = context.dp(14).toFloat()
            }
            // 功能芯片容器：primary 底（与父圆角区分），图标 onPrimary 染色；高度随芯片数自适应
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val cp = context.dp(8).toInt()
                setPadding(cp, cp, cp, cp)
                background = GradientDrawable().apply {
                    setColor(ConsoleTheme.primary)
                    cornerRadius = context.dp(10).toFloat()
                }
                addView(
                    ImageView(context).apply {
                        setImageResource(R.drawable.ic_golf)
                        colorFilter = PorterDuffColorFilter(ConsoleTheme.onPrimary, PorterDuff.Mode.SRC_IN)
                    },
                    LinearLayout.LayoutParams(context.dp(22), context.dp(22))
                )
                setOnClickListener { launchSelected() }
            })
        }
        addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply { setMargins(0, context.dp(8), context.dp(8), 0) }
        )
    }

    /** golf 直达拉起：绕过拦截器，newActivity 拉起选中 .lua；失败 Toast 提示。 */
    private fun launchSelected() {
        val path = selectedPath ?: return
        if (!NewActivityInterceptor.launchDirect(SessionManager.activity, path)) {
            Toast.makeText(context, "拉起失败：$path", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeOverlay() {
        overlay?.let { removeView(it) }
        overlay = null
    }
}