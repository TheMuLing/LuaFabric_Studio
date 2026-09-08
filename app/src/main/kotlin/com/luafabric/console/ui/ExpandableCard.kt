package com.luafabric.console.ui

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.luafabric.studio.falling.R

/**
 * 环境页折叠卡：圆角矩形标题行（左名称 + 右箭头），点击展开/收起内容。
 * 展开后内容背景与 surface 区分（surfaceContainer），标题为选中主色文本。
 */
class ExpandableCard(context: Context, title: String) : LinearLayout(context) {

    private val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
    }
    private val arrow = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageResource(R.drawable.ic_chevron_left)
        colorFilter = PorterDuffColorFilter(ConsoleTheme.onSurfaceVariant, PorterDuff.Mode.SRC_IN)
        layoutParams = LinearLayout.LayoutParams(context.dp(20), context.dp(20))
    }
    private var expanded = false

    init {
        orientation = LinearLayout.VERTICAL
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
            background = GradientDrawable().apply {
                setColor(ConsoleTheme.surfaceContainer)
                cornerRadius = context.dp(12).toFloat()
            }
            setOnClickListener { toggle() }
        }
        header.addView(
            TextView(context).apply {
                text = title
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(ConsoleTheme.primary)
                setPadding(0, 0, context.dp(8), 0)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(arrow)
        addView(header)

        body.setPadding(context.dp(10), context.dp(4), context.dp(10), context.dp(10))
        // 展开内容与 surface 区分：surfaceContainer 淡底 + 底圆角
        body.background = GradientDrawable().apply {
            setColor(ConsoleTheme.surfaceContainer and 0x00FFFFFF or 0x12000000)
            cornerRadii = floatArrayOf(
                0f, 0f, 0f, 0f,
                context.dp(12).toFloat(), context.dp(12).toFloat(),
                context.dp(12).toFloat(), context.dp(12).toFloat()
            )
        }
        addView(body)
    }

    fun addBody(view: View) {
        body.addView(
            view,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    fun setExpanded(on: Boolean) {
        expanded = on
        body.visibility = if (on) View.VISIBLE else View.GONE
        arrow.setImageResource(if (on) R.drawable.ic_chevron_down else R.drawable.ic_chevron_left)
    }

    private fun toggle() = setExpanded(!expanded)
}
