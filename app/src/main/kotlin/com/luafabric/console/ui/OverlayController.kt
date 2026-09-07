package com.luafabric.console.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.luafabric.console.core.ConsoleSettings
import com.luafabric.console.core.SessionManager
import com.luafabric.console.core.StateMachine
import com.luafabric.console.core.ConsoleState

/**
 * 悬浮控制：优先 TYPE_APPLICATION_OVERLAY，权限缺失兜底挂当前 Activity 内容视图。
 * 面板打开 = BALL→PANEL；完全关闭 = 移除浮球 → CLOSED（首次 Toast 提示音量键恢复）。
 */
class OverlayController(private val appContext: Context) {

    private val settings = ConsoleSettings(appContext)
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var ball: ConsoleBallView? = null
    private var sheet: ConsoleSheet? = null
    private var sheetHost: Activity? = null
    private var fallbackAttached = false
    private var fallbackHost: Activity? = null

    fun canOverlay(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(appContext)

    @SuppressLint("ClickableViewAccessibility")
    fun showBall() {
        if (ball != null) return
        val activity = hostActivity() ?: return
        val view = ConsoleBallView(appContext) { openSheet() }
        view.onMove = { dx, dy -> moveBy(dx, dy) }
        ball = view
        if (canOverlay()) {
            wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            params = WindowManager.LayoutParams(
                appContext.dp(56),
                appContext.dp(56),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = appContext.dp(12)
                y = appContext.dp(160)
            }
            try {
                wm?.addView(view, params)
                return
            } catch (_: Exception) {
                wm = null
                params = null
            }
        }
        // 权限兜底：挂到当前 Activity 内容视图
        val lp = FrameLayout.LayoutParams(appContext.dp(56), appContext.dp(56))
        lp.gravity = Gravity.TOP or Gravity.START
        lp.setMargins(appContext.dp(12), appContext.dp(160), 0, 0)
        (activity.window.decorView as ViewGroup).addView(view, lp)
        fallbackAttached = true
        fallbackHost = activity
    }

    @SuppressLint("NewApi")
    private fun moveBy(dx: Float, dy: Float) {
        if (wm != null && params != null) {
            val size = maxBallBounds()
            params!!.x = (params!!.x + dx).toInt().coerceIn(0, size[0])
            params!!.y = (params!!.y + dy).toInt().coerceIn(0, size[1])
            try {
                wm?.updateViewLayout(ball, params)
            } catch (_: Exception) {
            }
            return
        }
        if (fallbackAttached) {
            val p = ball?.layoutParams as? FrameLayout.LayoutParams ?: return
            p.marginStart = (p.marginStart + dx).toInt().coerceAtLeast(0)
            p.topMargin = (p.topMargin + dy).toInt().coerceAtLeast(0)
            ball?.layoutParams = p
        }
    }

    @SuppressLint("NewApi")
    private fun maxBallBounds(): IntArray {
        val activity = hostActivity()
        val s = wm?.currentWindowMetrics?.bounds
        val w = s?.width() ?: activity?.windowManager?.currentWindowMetrics?.bounds?.width() ?: 0
        val h = s?.height() ?: activity?.windowManager?.currentWindowMetrics?.bounds?.height() ?: 0
        return intArrayOf(
            maxOf(0, w - appContext.dp(56)),
            maxOf(0, h - appContext.dp(56))
        )
    }

    fun openSheet() {
        if (sheet != null) return
        val activity = hostActivity() ?: return
        // 宿主已死/正在结束：等待下次 join 重建，防 BadTokenException
        if (activity.isDestroyed || activity.isFinishing) return
        val s = ConsoleSheet(
            activity = activity,
            onFullyClosed = { fullyClosed() },
            onDismissed = { onSheetDismissedToBall() }
        )
        sheet = s
        sheetHost = activity
        StateMachine.transition(ConsoleState.PANEL)
        s.show()
    }

    /** 面板收起但未完全关闭 → 回到浮球。 */
    private fun onSheetDismissedToBall() {
        sheet = null
        sheetHost = null
        if (ball != null) StateMachine.transition(ConsoleState.BALL)
    }

    private fun fullyClosed() {
        closeAll()
        StateMachine.transition(ConsoleState.CLOSED)
        if (!settings.firstCloseDone) {
            Toast.makeText(appContext, "按下音量 - 键显示控制台浮球", Toast.LENGTH_SHORT).show()
            settings.firstCloseDone = true
        }
    }

    fun closeAll() {
        dismissSheetSafe()
        removeBallView()
    }

    /**
     * 后台：收起面板（回调回浮球态）+ 摘除浮球视图；会话/状态保留，回前台按状态恢复。
     */
    fun hideForBackground() {
        dismissSheetSafe()
        removeBallView()
    }

    /**
     * 宿主 Activity 销毁：面板窗口随宿主消失（onDismissed 不触发），须显式 dismiss 防窗口泄漏，
     * 并清理引用回浮球态，否则 openSheet 被 `sheet != null` 短路、后台 dismiss 死窗口抛 IllegalArg。
     * fallback 浮球挂宿主 decorView，宿主销毁后视图消失但引用残留 → 一并摘除，等上层重建。
     */
    fun onHostDestroyed(activity: Activity) {
        if (sheetHost === activity) dismissSheetSafe()
        if (fallbackHost === activity) removeBallView()
    }

    /** 异常安全：后台/宿主销毁时窗口可能已摘，dismiss 会抛 IllegalArgException。 */
    private fun dismissSheetSafe() {
        sheet?.let { s ->
            try {
                s.dismiss()
            } catch (_: Exception) {
            }
            sheet = null
            sheetHost = null
            StateMachine.transition(ConsoleState.BALL)
        }
    }

    private fun removeBallView() {
        ball?.let { b ->
            try {
                wm?.removeView(b)
            } catch (_: Exception) {
            }
            (b.parent as? ViewGroup)?.removeView(b)
        }
        ball = null
        wm = null
        params = null
        fallbackAttached = false
        fallbackHost = null
    }

    fun isBallShowing(): Boolean = ball != null

    fun isSheetShowing(): Boolean = sheet != null

    /** 崩溃提示：浮球变红。 */
    fun setBallRed(red: Boolean) {
        ball?.setRed(red)
    }

    private fun hostActivity(): Activity? = SessionManager.activity
}
