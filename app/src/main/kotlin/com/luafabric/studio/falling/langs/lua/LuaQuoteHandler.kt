package com.luafabric.studio.falling.langs.lua

import io.github.rosemoe.sora.lang.QuickQuoteHandler
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.StylesUtils
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.TextRange

/**
 * Lua引号处理器
 * 处理自动添加引号的功能
 */
class LuaQuoteHandler : QuickQuoteHandler {

    /**
     * 处理输入引号
     *
     * @param candidateCharacter 输入的字符
     * @param text 文本内容
     * @param cursor 光标范围
     * @param style 样式信息
     * @return 处理结果
     */
    override fun onHandleTyping(
        candidateCharacter: String,
        text: Content,
        cursor: TextRange,
        style: Styles?
    ): QuickQuoteHandler.HandleResult {
        if (style != null &&
            !StylesUtils.checkNoCompletion(style, cursor.start) &&
            !StylesUtils.checkNoCompletion(style, cursor.end) &&
            "\"" == candidateCharacter &&
            cursor.start.line == cursor.end.line
        ) {
            text.insert(cursor.start.line, cursor.start.column, "\"")
            text.insert(cursor.end.line, cursor.end.column + 1, "\"")
            return QuickQuoteHandler.HandleResult(
                true,
                TextRange(
                    text.indexer.getCharPosition(cursor.startIndex + 1),
                    text.indexer.getCharPosition(cursor.endIndex + 1)
                )
            )
        }
        return QuickQuoteHandler.HandleResult.NOT_CONSUMED
    }
}