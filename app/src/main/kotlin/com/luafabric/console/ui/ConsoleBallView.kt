package com.luafabric.console.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** 悬浮球：圆角矩形「控制台」，支持拖动与点击。颜色遵循 luafabric 主题（ConsoleTheme.primary）。 */
@SuppressLint("ViewConstructor")
class ConsoleBallView(context: Context, private val onTap: () -> Unit) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ConsoleTheme.primary }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ConsoleTheme.onPrimary
        textSize = context.resources.displayMetrics.scaledDensity * 12f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        isFakeBoldText = true
    }
    private val bounds = RectF()
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var errorCount = 0

    var onMove: ((dx: Float, dy: Float) -> Unit)? = null

    /** 崩溃提示：浮球变红。 */
    fun setRed(red: Boolean) {
        paint.color = if (red) 0xFFE53935.toInt() else ConsoleTheme.primary
        invalidate()
    }

    /** 未读 Lua 错误角标计数（右上角红点）；0 隐藏。 */
    fun setErrorCount(n: Int) {
        errorCount = n
        invalidate()
    }

    init {
        contentDescription = "控制台"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bounds, 48f, 48f, paint)
        val y = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("控制台", width / 2f, y, textPaint)
        if (errorCount > 0) drawBadge(canvas)
    }

    /** 右上角未读错误角标：红色圆底 + 白色计数（>99 → 99+）。 */
    private fun drawBadge(canvas: Canvas) {
        val r = resources.displayMetrics.scaledDensity * 7f
        val cx = width - r - resources.displayMetrics.scaledDensity * 3f
        val cy = r + resources.displayMetrics.scaledDensity * 3f
        badgePaint.color = 0xFFE53935.toInt()
        canvas.drawCircle(cx, cy, r, badgePaint)
        val label = if (errorCount > 99) "99+" else errorCount.toString()
        badgeText.color = 0xFFFFFFFF.toInt()
        badgeText.textSize = resources.displayMetrics.scaledDensity * 8f
        val by = cy - (badgeText.descent() + badgeText.ascent()) / 2f
        canvas.drawText(label, cx, by, badgeText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (abs(dx) > 8 || abs(dy) > 8) {
                    moved = true
                    onMove?.invoke(dx, dy)
                    downX = event.rawX
                    downY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) onTap()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
