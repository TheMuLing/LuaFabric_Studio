package com.luafabric.console.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.luafabric.console.ui.tabs.DebugTabView
import com.luafabric.console.ui.tabs.EnvTabView
import com.luafabric.console.ui.tabs.StructTabView
import com.luafabric.console.ui.tabs.LogcatTabView
import com.luafabric.console.ui.tabs.OutputTabView
import com.luafabric.console.ui.tabs.SettingsTabView
import com.luafabric.studio.falling.R

/**
 * 控制台面板：Modal BottomSheet + TabLayout（输出/文件/事件/环境/Logcat/调试）+ 完全关闭。
 * 页签视图程序化构建并按需缓存，避免依赖 FragmentManager（宿主 Activity 基类不定）。
 */
class ConsoleSheet(
    activity: Activity,
    private val onFullyClosed: () -> Unit,
    private val onDismissed: () -> Unit
) : BottomSheetDialog(activity) {

    companion object {
        /** 上次选中的页签位：面板关闭/重开后保留，退出本次调试（onSessionEnd）时重置为 0。 */
        @Volatile
        var persistedTab = 0
    }

    private val cachedTabs = HashMap<Int, View>()
    private var lastShownPos = -1
    private val container by lazy { FrameLayout(context).apply { id = android.view.View.generateViewId() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = context
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            // 顶部/左右零 padding：header 与 tabs 的 surfaceContainer 背景贴边全覆盖
            setPadding(0, 0, 0, ctx.dp(20))
            setBackgroundColor(ConsoleTheme.surface)
        }

        // 头部：标题 + 最小化 + 完全关闭（头部区用 surfaceContainer 与内容区分色）
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
        // 最小化：收起面板回浮球
        header.addView(iconButton(R.drawable.ic_console_minimize) { dismiss() })
        // 完全关闭（叉号）：直接关闭，无二次确认
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
            // 主题色：指示器/选中 = 主色，未选中 = 次级文本色
            setSelectedTabIndicatorColor(ConsoleTheme.primary)
            setTabTextColors(ConsoleTheme.onSurfaceVariant, ConsoleTheme.primary)
            setBackgroundColor(ConsoleTheme.surfaceContainer)
            // 标签始终固定等分父浮窗宽度并单行，杜绝「Logcat」超窄时换行/截断
            tabMode = TabLayout.MODE_FIXED
            tabGravity = TabLayout.GRAVITY_FILL
            // 子项点击波纹跟随主题：主色 13% 透明度
            setTabRippleColor(
                android.content.res.ColorStateList.valueOf(
                    ConsoleTheme.primary and 0x00FFFFFF or 0x21000000
                )
            )
        }
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            ctx.dp(460)
        )

        root.addView(header)
        root.addView(tabs)
        root.addView(container)
        setContentView(root)

        // Material ≥1.14 对宽屏/横屏 BottomSheet 施加 android:maxWidth(默认 640dp)，
        // 使浮窗呈居中窄条、左右留边无法覆盖（平板横屏诊断取样 design_bottom_sheet=1116π/1600π）。
        // 放开为容器宽，控制台即全宽铺满；maxWidth 单位为 px。
        getBehavior()?.setMaxWidth(ctx.resources.displayMetrics.widthPixels)

        // 禁用 sheet 拖拽手势：页签内滚动（如环境页 ScrollView）与 BottomSheet 下拉关闭冲突，
        // 误触下划会错误收起浮窗；关闭仅通过头部最小化/完全关闭按钮。
        getBehavior().setDraggable(false)

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                persistedTab = tab.position
                showTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        // 恢复上次页签：面板关闭/重开后保留切换状态（退出调试时由 onSessionEnd 重置为 0）
        // select() 同步 TabLayout 选中指示器并触发 onTabSelected → showTab；兜底防止 select 未触发
        val restore = persistedTab.coerceIn(0, tabs.tabCount - 1)
        tabs.getTabAt(restore)?.select()
        if (lastShownPos != restore) showTab(restore)

        // 旋转关闭：BottomSheetDialog 无配置回调，用 DisplayListener 监听；本面板仅竖屏创建，旋到横屏即收。
        val displayManager = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val orientationCloser = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                val rotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
                    ?: return
                val nowLandscape =
                    rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
                if (nowLandscape) dismiss()
            }
        }
        displayManager.registerDisplayListener(orientationCloser, Handler(Looper.getMainLooper()))
        setOnDismissListener {
            displayManager.unregisterDisplayListener(orientationCloser)
            onDismissed()
        }
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

    /** 头部图标按钮：圆角矩形容器色底 + Icons 风格 vector 图标。 */
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
