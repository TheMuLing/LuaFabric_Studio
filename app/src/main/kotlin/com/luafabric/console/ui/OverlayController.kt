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
    private var fallbackAttached = false

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
        val s = ConsoleSheet(
            activity = activity,
            onFullyClosed = { fullyClosed() },
            onDismissed = { onSheetDismissedToBall() }
        )
        sheet = s
        StateMachine.transition(ConsoleState.PANEL)
        s.show()
    }

    /** 面板收起但未完全关闭 → 回到浮球。 */
    private fun onSheetDismissedToBall() {
        sheet = null
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
        sheet?.dismiss()
        sheet = null
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
    }

    fun isBallShowing(): Boolean = ball != null

    private fun hostActivity(): Activity? = SessionManager.activity
}
