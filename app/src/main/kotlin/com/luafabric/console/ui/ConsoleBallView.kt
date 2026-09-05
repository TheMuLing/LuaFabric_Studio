package com.luafabric.console.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** 悬浮球：圆角矩形「控制台」，支持拖动与点击。 */
@SuppressLint("ViewConstructor")
class ConsoleBallView(context: Context, private val onTap: () -> Unit) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3D5AFE.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = context.resources.displayMetrics.scaledDensity * 12f
        textAlign = Paint.Align.CENTER
    }
    private val bounds = RectF()
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    var onMove: ((dx: Float, dy: Float) -> Unit)? = null

    init {
        contentDescription = "控制台"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bounds, 24f, 24f, paint)
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
