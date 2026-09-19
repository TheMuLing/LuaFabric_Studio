package com.luafabric.console.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import com.luafabric.console.ui.tabs.DebugTabView
import com.luafabric.console.ui.tabs.EnvTabView
import com.luafabric.console.ui.tabs.StructTabView
import com.luafabric.console.ui.tabs.LogcatTabView
import com.luafabric.console.ui.tabs.OutputTabView
import com.luafabric.console.ui.tabs.SettingsTabView
import com.luafabric.studio.falling.R

/**
 * 平板横屏专用的右侧调试面板（左下→ 右滑出全高侧栏）。
 * 复用 ConsoleSheet 的页签构建逻辑（输出/结构/环境/Logcat/调试/设置），宿主改为
 * 普通 Dialog + 右对齐固定宽度 + 滑入动画，替代底部 BottomSheet。
 * persistedTab 与 ConsoleSheet 共享，退出调试时的重置逻辑对两者同时生效。
 */
class SidePanelDialog(
    activity: Activity,
    private val onFullyClosed: () -> Unit,
    private val onDismissed: () -> Unit,
    private val targetWidthPx: Int
) : Dialog(activity) {

    private val cachedTabs = HashMap<Int, View>()
    private var lastShownPos = -1
    private val container by lazy { FrameLayout(context).apply { id = View.generateViewId() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = context
        val r = ConsoleTheme.cornerRadiusPx
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            // 侧栏贴右缘：圆角仅在邻近屏幕内容一侧（左上/左下），右上/右下贴边为直角
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ConsoleTheme.surface)
                // cornerRadii: topLeft,tlX topLeft,tlY / topRight / bottomRight / bottomLeft 两两(x,y)
                cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            }
            // 关键：child（header/tabs/内容）为矩形全宽背景，会盖掉圆角。按背景轮廓裁剪子view，
            // 左上/左下才对内伸圆角（右上/右下贴边直角不受影响）。
            clipToOutline = true
        }

        // 头部：标题 + 最小化 + 完全关闭
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(8), ctx.dp(8))
            setBackgroundColor(ConsoleTheme.surfaceContainer)
        }
        header.addView(
            TextView(ctx).apply {
                text = "调试控制台"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ConsoleTheme.onSurface)
                setPadding(0, 0, ctx.dp(8), 0)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            iconButton(R.drawable.ic_console_minimize) { dismiss() },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = ctx.dp(8) }
        )
        header.addView(
            iconButton(R.drawable.ic_console_close) { onFullyClosed() },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = ctx.dp(8) }
        )

        val tabs = TabLayout(ctx).apply {
            addTab(newTab().setText("输出"))
            addTab(newTab().setText("结构"))
            addTab(newTab().setText("环境"))
            addTab(newTab().setText("Logcat"))
            addTab(newTab().setText("调试"))
            addTab(newTab().setText("设置"))
            setSelectedTabIndicatorColor(ConsoleTheme.primary)
            setTabTextColors(ConsoleTheme.onSurfaceVariant, ConsoleTheme.primary)
            setBackgroundColor(ConsoleTheme.surfaceContainer)
            // 固定等分面板宽度并单行
            tabMode = TabLayout.MODE_FIXED
            tabGravity = TabLayout.GRAVITY_FILL
            setTabRippleColor(
                android.content.res.ColorStateList.valueOf(
                    ConsoleTheme.primary and 0x00FFFFFF or 0x21000000
                )
            )
        }
        // 容器撑满剩余竖向空间（全高侧栏）
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )

        root.addView(header)
        root.addView(tabs)
        root.addView(container)
        setContentView(root)

        // 右侧定位、固定宽度、全高、划入动画
        window?.setLayout(targetWidthPx, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setGravity(Gravity.RIGHT or Gravity.TOP)
        window?.setWindowAnimations(R.style.SidePanelDialogAnim)
        window?.setDimAmount(0.3f)
        // 贴边根因修：普通 Dialog 主题默认背景带圆角+描边+系统 inset 会在右/上/下留缝，
        // 改为透明背景、不避让系统窗口（全高贴缘），surface+圆角完全交给 root 自绘。
        window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        window?.decorView?.setPadding(0, 0, 0, 0)
        @SuppressLint("ObsoleteSdkInt")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window?.setDecorFitsSystemWindows(false)
        }

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                ConsoleSheet.persistedTab = tab.position
                showTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        val restore = ConsoleSheet.persistedTab.coerceIn(0, tabs.tabCount - 1)
        tabs.getTabAt(restore)?.select()
        if (lastShownPos != restore) showTab(restore)

        // 旋转关闭：Dialog 无配置回调，用 DisplayListener 监听；本面板仅横屏创建，旋到竖屏即收。
        val displayManager = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val orientationCloser = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                val rotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
                    ?: return
                val nowLandscape =
                    rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
                if (!nowLandscape) dismiss()
            }
        }
        displayManager.registerDisplayListener(orientationCloser, Handler(Looper.getMainLooper()))
        setOnDismissListener {
            displayManager.unregisterDisplayListener(orientationCloser)
            onDismissed()
        }
    }

    @SuppressLint("Recycle") // 缓存视图重复 addView，Recycler 唤醒由各 TabView 内部自管
    private fun showTab(pos: Int) {
        (cachedTabs[lastShownPos] as? LogcatTabView)?.stopPolling()
        lastShownPos = pos
        val view = cachedTabs.getOrPut(pos) { buildTab(pos) }
        container.removeAllViews()
        container.addView(
            view,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        (view as? StructTabView)?.refresh()
        (view as? EnvTabView)?.refresh()
        (view as? DebugTabView)?.refresh()
        (view as? SettingsTabView)?.refresh()
        (view as? LogcatTabView)?.apply {
            refresh()
            startPolling()
        }
    }

    private fun buildTab(pos: Int): View = when (pos) {
        0 -> OutputTabView(context)
        1 -> StructTabView(context)
        2 -> EnvTabView(context)
        3 -> LogcatTabView(context)
        4 -> DebugTabView(context)
        else -> SettingsTabView(context)
    }

    private fun iconButton(iconRes: Int, onClick: () -> Unit): android.widget.ImageView =
        android.widget.ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(ConsoleTheme.onSurface)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ConsoleTheme.accentContainer)
                cornerRadius = context.dp(12).toFloat()
            }
            val p = context.dp(8).toInt()
            setPadding(p, p, p, p)
            setOnClickListener { onClick() }
        }
}