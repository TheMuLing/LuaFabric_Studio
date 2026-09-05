package com.luafabric.console.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.luafabric.console.ui.tabs.EnvTabView
import com.luafabric.console.ui.tabs.FileTabView
import com.luafabric.console.ui.tabs.LogcatTabView
import com.luafabric.console.ui.tabs.OutputTabView
import com.luafabric.console.ui.tabs.PlaceholderView

/**
 * 控制台面板：Modal BottomSheet + TabLayout（输出/文件/事件/环境/Logcat/调试）+ 完全关闭。
 * 页签视图程序化构建并按需缓存，避免依赖 FragmentManager（宿主 Activity 基类不定）。
 */
class ConsoleSheet(
    activity: Activity,
    private val onFullyClosed: () -> Unit,
    private val onDismissed: () -> Unit
) : BottomSheetDialog(activity) {

    private val cachedTabs = HashMap<Int, View>()
    private var lastShownPos = -1
    private val container by lazy { FrameLayout(context).apply { id = android.view.View.generateViewId() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = context
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(4), ctx.dp(12), ctx.dp(4), ctx.dp(20))
        }

        // 头部：标题 + 完全关闭
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(8), 0, ctx.dp(8), 0)
        }
        header.addView(
            TextView(ctx).apply {
                text = "调试控制台"
                textSize = 16f
                setPadding(0, 0, ctx.dp(8), 0)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            TextView(ctx).apply {
                text = "完全关闭"
                textSize = 13f
                setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8))
                setOnClickListener { onFullyClosed() }
            }
        )

        val tabs = TabLayout(ctx).apply {
            addTab(newTab().setText("输出"))
            addTab(newTab().setText("文件"))
            addTab(newTab().setText("事件"))
            addTab(newTab().setText("环境"))
            addTab(newTab().setText("Logcat"))
            addTab(newTab().setText("调试"))
        }
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            ctx.dp(460)
        )

        root.addView(header)
        root.addView(tabs)
        root.addView(container)
        setContentView(root)

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        showTab(0)

        setOnDismissListener { onDismissed() }
    }

    @SuppressLint("Recycle")
    private fun showTab(pos: Int) {
        (cachedTabs[lastShownPos] as? LogcatTabView)?.stopPolling()
        lastShownPos = pos
        val view = cachedTabs.getOrPut(pos) { buildTab(pos) }
        container.removeAllViews()
        container.addView(
            view,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        (view as? FileTabView)?.refresh()
        (view as? EnvTabView)?.refresh()
        (view as? LogcatTabView)?.apply {
            refresh()
            startPolling()
        }
    }

    private fun buildTab(pos: Int): View = when (pos) {
        0 -> OutputTabView(context)
        1 -> FileTabView(context)
        3 -> EnvTabView(context)
        4 -> LogcatTabView(context)
        else -> PlaceholderView(context, tabName(pos))
    }

    private fun tabName(pos: Int): String = when (pos) {
        1 -> "文件"
        2 -> "事件"
        3 -> "环境"
        4 -> "Logcat"
        else -> "调试"
    }
}
