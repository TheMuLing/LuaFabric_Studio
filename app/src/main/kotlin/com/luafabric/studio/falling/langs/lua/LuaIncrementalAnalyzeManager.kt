package com.luafabric.studio.falling.langs.lua

import android.os.Bundle
import com.luafabric.studio.falling.langs.lua.completion.MyIdentifierAutoComplete
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.brackets.SimpleBracketsCollector
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.lang.styling.span.SpanClickableUrl
import io.github.rosemoe.sora.lang.styling.span.SpanExtAttrs
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.util.IntPair
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.util.Stack
import java.util.regex.Pattern

/**
 * Lua增量分析管理器
 * 负责语法分析、代码块识别和语法高亮生成
 */
class LuaIncrementalAnalyzeManager :
    AsyncIncrementalAnalyzeManager<State, HighlightToken>() {

    val diagnosticsContainer = DiagnosticsContainer()
    private val tokenizerProvider = ThreadLocal<LuaTextTokenizer>()
    val identifiers = MyIdentifierAutoComplete.SyncIdentifiers()

    private var androidClasses: Set<String>? = null
    private var classNamesVersion = 0
    
    var highlightHexColorsEnabled: Boolean = false

    companion object {
        private const val STATE_INCOMPLETE_COMMENT = 1
        private const val STATE_INCOMPLETE_LONG_STRING = 2

        @Volatile
        private var instance: LuaIncrementalAnalyzeManager? = null

        private val URL_PATTERN: Pattern = Pattern.compile(
            "https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)"
        )

    }

    init {
        instance = this
    }

    /**
     * 释放内存
     */
    fun releaseMemory() {
        identifiers.clear()
        tokenizerProvider.get()?.releaseTokens()
        System.gc()
    }
    
    /**
     * 设置类名集合
     *
     * @param androidClasses 类名集合
     */
    fun setClassMap(androidClasses: Set<String>?) {
        this.androidClasses = androidClasses
        classNamesVersion++
        updateAllTokenizersClassNames()
    }

    private fun updateAllTokenizersClassNames() {
        val tokenizer = obtainTokenizer()
        tokenizer.setClassNames(androidClasses)
        tokenizer.setClassNamesVersion(classNamesVersion)
    }

    private fun getAllTokenizers(): List<LuaTextTokenizer> {
        return emptyList()
    }

    private fun obtainTokenizer(): LuaTextTokenizer {
        var res = tokenizerProvider.get()
        if (res == null) {
            res = LuaTextTokenizer("", State())
            tokenizerProvider.set(res)
        }
        return res
    }

    override fun reset(content: ContentReference, extraArguments: Bundle) {
        super.reset(content, extraArguments)
        identifiers.clear()
    }

    override fun computeBlocks(
        text: Content,
        delegate: CodeBlockAnalyzeDelegate
    ): List<CodeBlock> {
        return performComputeBlocks(text, delegate)
    }

    private fun performComputeBlocks(
        text: Content,
        delegate: CodeBlockAnalyzeDelegate
    ): List<CodeBlock> {
        val stack = Stack<CodeBlock>()
        val blocks = ArrayList<CodeBlock>()
        var maxSwitch = 0
        var currSwitch = 0
        val brackets = SimpleBracketsCollector()
        val bracketsStack = Stack<Long>()

        for (i in 0 until text.lineCount) {
            if (delegate.isNotCancelled) {
                break
            }

            val state = getState(i)
            val checkForIdentifiers = state.state.state == 0 ||
                    (state.state.state == STATE_INCOMPLETE_COMMENT && state.tokens.size > STATE_INCOMPLETE_COMMENT)

            if (state.state.hasBraces || checkForIdentifiers) {
                for (i1 in state.tokens.indices) {
                    val tokenRecord = state.tokens[i1]
                    val token = tokenRecord.token

                    when (token) {
                        Tokens.LBRACE, Tokens.FUNCTION, Tokens.FOR, Tokens.WHILE,
                        Tokens.IF, Tokens.CONTINUE, Tokens.REPEAT, Tokens.SWITCH -> {
                            val offset = tokenRecord.offset
                            if (stack.isEmpty()) {
                                if (currSwitch > maxSwitch) {
                                    maxSwitch = currSwitch
                                }
                                currSwitch = 0
                            }
                            currSwitch++
                            val block = CodeBlock()
                            block.startLine = i
                            block.startColumn = offset
                            stack.push(block)
                        }

                        Tokens.RBRACE, Tokens.END -> {
                            val offset2 = tokenRecord.offset
                            if (stack.isNotEmpty()) {
                                val block2 = stack.pop()
                                block2.endLine = i
                                block2.endColumn = offset2
                                if (block2.startLine != block2.endLine) {
                                    blocks.add(block2)
                                }
                            }
                        }

                        else -> {
                            // 处理其他token
                        }
                    }

                    val type = getType(token)
                    if (type > 0) {
                        if (isStart(token)) {
                            bracketsStack.push(
                                IntPair.pack(
                                    type,
                                    text.getCharIndex(i, tokenRecord.offset)
                                )
                            )
                        } else if (bracketsStack.isNotEmpty()) {
                            val record = bracketsStack.pop()
                            val typeRecord = IntPair.getFirst(record).toInt()
                            if (typeRecord == type) {
                                brackets.add(
                                    IntPair.getSecond(record).toInt(),
                                    text.getCharIndex(i, tokenRecord.offset)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (delegate.isNotCancelled) {
            withReceiver { r ->
                r.updateBracketProvider(this, brackets)
            }
        }

        return blocks
    }

    private fun getType(token: Tokens): Int {
        return when (token) {
            Tokens.LBRACE, Tokens.RBRACE -> 3
            Tokens.LBRACK, Tokens.RBRACK -> 2
            Tokens.LPAREN, Tokens.RPAREN -> STATE_INCOMPLETE_COMMENT
            else -> 0
        }
    }

    private fun isStart(token: Tokens): Boolean {
        return token == Tokens.LBRACE || token == Tokens.LBRACK || token == Tokens.LPAREN
    }

    override fun getInitialState(): State {
        return State()
    }

    override fun stateEquals(state: State, another: State): Boolean {
        return state.equals(another)
    }

    override fun onAddState(state: State) {
        state.identifiers?.forEach { identifier ->
            identifiers.identifierIncrease(identifier)
        }
    }

    override fun onAbandonState(state: State) {
        state.identifiers?.forEach { identifier ->
            identifiers.identifierDecrease(identifier)
        }
    }

    override fun tokenizeLine(
        line: CharSequence,
        state: State,
        lineIndex: Int
    ): IncrementalAnalyzeManager.LineTokenizeResult<State, HighlightToken> {
        val tokens = ArrayList<HighlightToken>()
        var newState = 0
        val stateObj = State()
        stateObj.longCommentEqualCount = state.longCommentEqualCount
        stateObj.longStringEqualCount = state.longStringEqualCount
        stateObj.inLongString = state.inLongString

        val tokenizer = obtainTokenizer()
        tokenizer.reset(line, stateObj)
        tokenizer.highlightHexColorsEnabled = this.highlightHexColorsEnabled
        
        newState = when (state.state) {
            0 -> tokenizeNormal(line, 0, tokens, stateObj)
            STATE_INCOMPLETE_COMMENT -> {
                tokenizer.offset = 0
                val token = tokenizer.scanLongComment(true)
                val tokenLength = tokenizer.getTokenLength()
                tokens.add(HighlightToken(token, 0))

                when (token) {
                    Tokens.LONG_COMMENT_INCOMPLETE -> {
                        stateObj.longCommentEqualCount = tokenizer.getLongCommentEqualCount()
                        STATE_INCOMPLETE_COMMENT
                    }

                    Tokens.LONG_COMMENT_COMPLETE -> {
                        val remainingOffset = tokenLength
                        if (remainingOffset < line.length) {
                            tokenizeNormal(line, remainingOffset, tokens, stateObj)
                        } else {
                            0
                        }
                    }

                    else -> {
                        0
                    }
                }
            }

            STATE_INCOMPLETE_LONG_STRING -> {
                tokenizer.offset = 0
                tokenizer.setLongStringEqualCount(stateObj.longStringEqualCount)
                tokenizer.setInLongString(stateObj.inLongString)

                val token = tokenizer.scanLongString(true)
                val tokenLength = tokenizer.getTokenLength()
                tokens.add(HighlightToken(token, 0))

                stateObj.longStringEqualCount = tokenizer.getLongStringEqualCount()
                stateObj.inLongString = tokenizer.isInLongString()

                when (token) {
                    Tokens.LONG_STRING_INCOMPLETE -> STATE_INCOMPLETE_LONG_STRING
                    Tokens.LONG_STRING -> {
                        stateObj.inLongString = false
                        val remainingOffset = tokenLength
                        if (remainingOffset < line.length) {
                            tokenizeNormal(line, remainingOffset, tokens, stateObj)
                        } else {
                            0
                        }
                    }

                    else -> {
                        stateObj.inLongString = false
                        0
                    }
                }
            }

            else -> 0
        }

        if (tokenizer.getClassNamesVersion() != classNamesVersion) {
            tokenizer.setClassNames(androidClasses)
            tokenizer.setClassNamesVersion(classNamesVersion)
        }

        if (tokens.isEmpty()) {
            tokens.add(HighlightToken(Tokens.UNKNOWN, 0))
        }

        stateObj.state = newState
        return IncrementalAnalyzeManager.LineTokenizeResult(stateObj, tokens)
    }
    
    private fun parseHexColor(hex: String): Int? {
    val colorStr = when {
        hex.startsWith("0x", ignoreCase = true) -> hex.substring(2)
        hex.startsWith("#") -> hex.substring(1)
        else -> hex
    }
    return when (colorStr.length) {
        6 -> {
            val color = colorStr.toIntOrNull(16) ?: return null
            0xFF000000.toInt() or color
        }
        8 -> {
            colorStr.toLongOrNull(16)?.toInt()
        }
        else -> null
    }
}
    private fun tokenizeNormal(
        text: CharSequence,
        offset: Int,
        tokens: MutableList<HighlightToken>,
        st: State
    ): Int {
        val tokenizer = obtainTokenizer()
        tokenizer.reset(text)
        tokenizer.offset = offset
        tokenizer.highlightHexColorsEnabled = this.highlightHexColorsEnabled
        var state = 0

        while (true) {
            val token = tokenizer.nextToken()
            if (token == Tokens.EOF) {
                break
            }

            val tokenText = tokenizer.getTokenText().toString()
            val highlightToken = HighlightToken(token, tokenizer.offset)

    if (token == Tokens.HEX_COLOR) {
    // 去除首尾引号
    val rawText = tokenText
    val unquoted = if (rawText.length >= 2 && (rawText.startsWith('"') || rawText.startsWith('\'')) && rawText.last() == rawText.first()) {
        rawText.substring(1, rawText.length - 1)
    } else {
        rawText
    }
    highlightToken.text = unquoted
}

            // 检测 URL（仅在字符串或注释中）
            if ((token == Tokens.STRING || token == Tokens.LINE_COMMENT ||
                        token == Tokens.LONG_COMMENT_COMPLETE || token == Tokens.LONG_COMMENT_INCOMPLETE) &&
                URL_PATTERN.matcher(tokenText).find()
            ) {
                // 将整个 token 文本作为 URL（或提取匹配部分，这里简化处理）
                highlightToken.url = tokenText
            }

            tokens.add(highlightToken)

            // 原有状态处理逻辑保持不变
            if (token == Tokens.LONG_STRING || token == Tokens.LONG_STRING_INCOMPLETE) {
                if (token == Tokens.LONG_STRING_INCOMPLETE) {
                    st.longStringEqualCount = tokenizer.getLongStringEqualCount()
                    st.inLongString = true
                    return STATE_INCOMPLETE_LONG_STRING
                }
            } else if (tokenizer.getTokenLength() < 1000 &&
                (token == Tokens.STRING ||
                        token == Tokens.LONG_COMMENT_COMPLETE ||
                        token == Tokens.LONG_COMMENT_INCOMPLETE ||
                        token == Tokens.LINE_COMMENT)
            ) {
                if (token == Tokens.LONG_COMMENT_INCOMPLETE) {
                    state = STATE_INCOMPLETE_COMMENT
                    break
                }
            } else {
                if (token == Tokens.LBRACE || token == Tokens.RBRACE) {
                    st.hasBraces = true
                }
                if (token == Tokens.IDENTIFIER) {
                    st.addIdentifier(tokenizer.getTokenText())
                }
                if (token == Tokens.LONG_COMMENT_INCOMPLETE) {
                    state = STATE_INCOMPLETE_COMMENT
                    break
                }
            }
        }
        return state
    }

    override fun generateSpansForLine(lineResult: IncrementalAnalyzeManager.LineTokenizeResult<State, HighlightToken>): List<Span> {
        val spans = ArrayList<Span>()
        val tokens = lineResult.tokens
        var previous = Tokens.UNKNOWN
        var classNamePrevious = false

        var nextIsLocal = false
        var inLocalDeclaration = false

        for (i in tokens.indices) {
            val tokenRecord = tokens[i]
            val token = tokenRecord.token
            val offset = tokenRecord.offset
            var span: Span

            when (token) {
                Tokens.LOCAL -> {
                    nextIsLocal = true
                    inLocalDeclaration = true
                }

                Tokens.COMMA -> {
                    if (inLocalDeclaration) {
                        nextIsLocal = true
                    }
                }

                Tokens.EQ, Tokens.NEWLINE -> {
                    inLocalDeclaration = false
                    nextIsLocal = false
                }

                else -> {
                    // 其他token不需要特殊处理
                }
            }

            span = when (token) {
                Tokens.WHITESPACE, Tokens.NEWLINE, Tokens.EQ -> {
                    SpanFactory.obtain(offset, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                }

                Tokens.STRING, Tokens.LONG_STRING, Tokens.LONG_STRING_INCOMPLETE, Tokens.CHARACTER_LITERAL -> {
                    classNamePrevious = false
                    SpanFactory.obtain(offset, TextStyle.makeStyle(EditorColorScheme.LITERAL, true))
                }

                Tokens.COLLECTGARBAGE, Tokens.COMPILE, Tokens.COROUTINE, Tokens.ASSERT,
                Tokens.ERROR, Tokens.IPAIRS, Tokens.PAIRS, Tokens.NEXT, Tokens.PRINT,
                Tokens.RAWEQUAL, Tokens.RAWGET, Tokens.RAWSET, Tokens.SELECT,
                Tokens.SETMETATABLE, Tokens.GETMETATABLE, Tokens.TONUMBER,
                Tokens.TOSTRING, Tokens.TYPE, Tokens.UNPACK, Tokens._G, Tokens.CALL -> {
                    classNamePrevious = false
                    SpanFactory.obtain(
                        offset,
                        TextStyle.makeStyle(EditorColorScheme.OPERATOR, 0, false, false, false)
                    )
                }

                Tokens.NUMBER, Tokens.TRUE, Tokens.FALSE -> {
                    classNamePrevious = false
                    SpanFactory.obtain(
                        offset,
                        TextStyle.makeStyle(EditorColorScheme.OPERATOR, 0, true, false, false)
                    )
                }
                
// 在 generateSpansForLine 的 HEX_COLOR 分支中
Tokens.HEX_COLOR -> {
    val span = SpanFactory.obtain(offset, TextStyle.makeStyle(EditorColorScheme.OPERATOR, 0, true, false, false))
    if (highlightHexColorsEnabled) {
        tokenRecord.text?.let { hex ->
            val color = parseHexColor(hex)
            if (color != null) {
                // 使用 EditorColor 包装颜色值
                span.setUnderlineColor(color)
            }
        }
    }
    span
}

                Tokens.IF, Tokens.THEN, Tokens.ELSE, Tokens.ELSEIF, Tokens.END,
                Tokens.FOR, Tokens.IN, Tokens.REPEAT, Tokens.RETURN, Tokens.BREAK,
                Tokens.UNTIL, Tokens.WHILE, Tokens.DO, Tokens.FUNCTION, Tokens.GOTO,
                Tokens.NIL, Tokens.NOT, Tokens.IMPORT, Tokens.REQUIRE, Tokens.SWITCH,
                Tokens.LAMBDA, Tokens.CONTINUE, Tokens.DEFAULT, Tokens.NEWCLASS,
                Tokens.CASE, Tokens.TRY, Tokens.FINALLY, Tokens.CATCH, Tokens.DEFER, Tokens.WHEN -> {
                    classNamePrevious = false
                    SpanFactory.obtain(
                        offset,
                        TextStyle.makeStyle(EditorColorScheme.KEYWORD, 0, true, false, false)
                    )
                }

                Tokens.LOCAL -> {
                    nextIsLocal = true
                    classNamePrevious = false
                    SpanFactory.obtain(
                        offset,
                        TextStyle.makeStyle(EditorColorScheme.KEYWORD, 0, true, false, false)
                    )
                }

                Tokens.IDENTIFIER -> {
                    var type = EditorColorScheme.TEXT_NORMAL

                    when {
                        nextIsLocal -> {
                            type = EditorColorScheme.LOCAL_VARIABLE
                        }

                        classNamePrevious -> {
                            type = EditorColorScheme.IDENTIFIER_VAR
                            classNamePrevious = false
                        }

                        previous == Tokens.DOT -> {
                            type = EditorColorScheme.IDENTIFIER_VAR
                        }

                        previous == Tokens.AT -> {
                            type = EditorColorScheme.ANNOTATION
                        }

                        else -> {
                            var j = i + 1
                            var next = Tokens.UNKNOWN
                            while (j < tokens.size) {
                                next = tokens[j].token
                                if (next != Tokens.WHITESPACE &&
                                    next != Tokens.NEWLINE &&
                                    next != Tokens.LONG_COMMENT_INCOMPLETE &&
                                    next != Tokens.LONG_COMMENT_COMPLETE &&
                                    next != Tokens.LINE_COMMENT
                                ) {
                                    break
                                }
                                j++
                            }

                            type = when (next) {
                                Tokens.LPAREN -> EditorColorScheme.FUNCTION_NAME
                                Tokens.DOT -> {
                                    classNamePrevious = true
                                    EditorColorScheme.IDENTIFIER_VAR
                                }

                                else -> {
                                    if (tokenRecord.token == Tokens.IDENTIFIER &&
                                        androidClasses != null &&
                                        androidClasses!!.contains(tokenRecord.text ?: "")
                                    ) {
                                        EditorColorScheme.CLASS_NAME
                                    } else {
                                        EditorColorScheme.TEXT_NORMAL
                                    }
                                }
                            }
                        }
                    }

                    SpanFactory.obtain(offset, TextStyle.makeStyle(type))
                }

                Tokens.LBRACE, Tokens.RBRACE -> {
                    SpanFactory.obtain(offset, TextStyle.makeStyle(EditorColorScheme.OPERATOR))
                }

                Tokens.LINE_COMMENT, Tokens.LONG_COMMENT_COMPLETE, Tokens.LONG_COMMENT_INCOMPLETE -> {
                    SpanFactory.obtain(
                        offset,
                        TextStyle.makeStyle(
                            EditorColorScheme.COMMENT,
                            0,
                            false,
                            true,
                            false,
                            true
                        )
                    )
                }

                Tokens.CLASS_NAME -> {
                    SpanFactory.obtain(offset, TextStyle.makeStyle(EditorColorScheme.CLASS_NAME))
                }

                else -> {
                    if (isOperator(token)) {
                        SpanFactory.obtain(offset, TextStyle.makeStyle(EditorColorScheme.OPERATOR))
                    } else {
                        SpanFactory.obtain(
                            offset,
                            TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
                        )
                    }
                }
            }

            tokenRecord.url?.let { url ->
                span.setSpanExt(SpanExtAttrs.EXT_INTERACTION_INFO, SpanClickableUrl(url))
                span.setUnderlineColor(io.github.rosemoe.sora.lang.styling.color.EditorColor(span.foregroundColorId))
            }

            spans.add(span)

            if (token == Tokens.EQ || token == Tokens.NEWLINE) {
                nextIsLocal = false
            }

            when (token) {
                Tokens.LINE_COMMENT, Tokens.LONG_COMMENT_COMPLETE,
                Tokens.LONG_COMMENT_INCOMPLETE, Tokens.WHITESPACE, Tokens.NEWLINE -> {
                    // 不更新previous
                }

                else -> {
                    previous = token
                }
            }
        }

        return spans
    }

    private fun isOperator(token: Tokens): Boolean {
        return when (token) {
            Tokens.ADD, Tokens.SUB, Tokens.MUL, Tokens.DIV, Tokens.MOD, Tokens.POW,
            Tokens.NEQ, Tokens.LT, Tokens.GT, Tokens.LEQ, Tokens.GEQ, Tokens.AT,
            Tokens.XOR, Tokens.QUESTION, Tokens.EQEQ, Tokens.LTEQ, Tokens.GTEQ,
            Tokens.DOTEQ, Tokens.LTLT, Tokens.LTGT, Tokens.CLT, Tokens.AEQ,
            Tokens.GTGT, Tokens.ARROW, Tokens.ARROW_LEFT_LONG, Tokens.ARROW_RIGHT_LONG,
            Tokens.SPACESHIP, Tokens.SLASH_SLASH_EQ, Tokens.GTGT_EQ, Tokens.LTLT_EQ, Tokens.DOT_DOT_EQ, Tokens.DOT_DOT_LT, Tokens.QUESTION_DOT_DOT,
            Tokens.NOT_NOT, Tokens.NULL_COALESCING, Tokens.STAR_STAR, Tokens.TILDE_TILDE,
            Tokens.CARET_CARET, Tokens.HASH_HASH, Tokens.AT_AT, Tokens.DOLLAR_DOLLAR,
            Tokens.COLON_EQ, Tokens.EQ_COLON, Tokens.QUESTION_DOT, Tokens.QUESTION_COLON,
            Tokens.QUESTION_EQ, Tokens.QUESTION_MINUS, Tokens.QUESTION_PLUS,
            Tokens.ARROW_LEFT, Tokens.TILDE_EQ, Tokens.EQEQEQ, Tokens.NEQEQ,
            Tokens.FAT_ARROW, Tokens.PLUS_PLUS, Tokens.PLUS_EQ, Tokens.MINUS_EQ,
            Tokens.STAR_EQ, Tokens.SLASH_EQ, Tokens.PERCENT_EQ, Tokens.AMP_EQ,
            Tokens.BAR_EQ, Tokens.CARET_EQ, Tokens.EQ_LT, Tokens.COLON_COLON,
            Tokens.DOT_DOT, Tokens.SLASH_SLASH, Tokens.BACKSLASH_BACKSLASH,
            Tokens.SLASH_STAR, Tokens.STAR_SLASH, Tokens.SLASH_STAR_STAR,
            Tokens.HASH_HASH_HASH, Tokens.MINUS_BAR, Tokens.BAR_GT, Tokens.LT_BAR,
            Tokens.AND, Tokens.OR, Tokens.OP -> true

            else -> false
        }
    }
}