package com.luafabric.console.output

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object ClipboardHelper {

    fun copy(context: Context, text: String, hint: String = "已复制") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("console", text))
        Toast.makeText(context, hint, Toast.LENGTH_SHORT).show()
    }
}
