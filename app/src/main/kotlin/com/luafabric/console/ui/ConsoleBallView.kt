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
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    var onMove: ((dx: Float, dy: Float) -> Unit)? = null

    /** 崩溃提示：浮球变红。 */
    fun setRed(red: Boolean) {
        paint.color = if (red) 0xFFE53935.toInt() else ConsoleTheme.primary
        invalidate()
    }

    init {
        contentDescription = "控制台"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bounds, 64f, 64f, paint)
        val y = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("控制台", width / 2f, y, textPaint)
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
