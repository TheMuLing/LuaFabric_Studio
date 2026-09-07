package com.luafabric.console.ui.tabs

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.luafabric.console.ui.ConsoleTheme
import com.luafabric.console.ui.dp

/** 未落地页签占位。 */
class PlaceholderView(context: Context, tabName: String) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(ConsoleTheme.surface)
        addView(
            TextView(context).apply {
                text = "「$tabName」页（后续提交）"
                textSize = 14f
                setTextColor(ConsoleTheme.onSurfaceVariant)
            }
        )
        val hint = TextView(context).apply {
            text = "提示：长按输出条目进入多选模式"
            textSize = 11f
            setTextColor(ConsoleTheme.onSurfaceVariant)
        }
        addView(
            hint,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = context.dp(8)
            }
        )
    }
}
