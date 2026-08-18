package com.luafabric.studio.falling.langs.lua.format

import com.myopicmobile.textwarrior.common.AutoIndent
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.format.Formatter.FormatResultReceiver
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.TextRange

class LuaFormatter : Formatter {
    private var receiver: FormatResultReceiver? = null

    override fun format(text: Content, cursorRange: TextRange) {
        val formattedText = AutoIndent.format(text, 2)
        this.receiver!!.onFormatSucceed(formattedText, cursorRange)
    }

    override fun formatRegion(text: Content, rangeToFormat: TextRange, cursorRange: TextRange) {
        val formattedText = AutoIndent.format(text, 2)
        this.receiver?.onFormatSucceed(formattedText, cursorRange)
    }

    override fun setReceiver(receiver: FormatResultReceiver?) {
        this.receiver = receiver
    }

    override fun isRunning(): Boolean {
        return false
    }

    override fun destroy() {
    }

    companion object {
        val INSTANCE: LuaFormatter = LuaFormatter()
    }
}