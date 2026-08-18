package com.luafabric.studio.falling.langs.lua

import io.github.rosemoe.sora.util.MyCharacter
import io.github.rosemoe.sora.util.TrieTree

class LuaTextTokenizer {

    private lateinit var source: CharSequence
    private var state: State? = null
    private var bufferLen: Int = 0
    private var line = 0
    private var column = 0
    var index = 0
    var offset = 0
    private var length = 0
    private var currToken: Tokens = Tokens.WHITESPACE
    private var lcCal = false

    private var longCommentEqualCount = 0
    private var longStringEqualCount = 0
    private var inLongString = false

    private var classNames: MutableSet<String> = HashSet()
    private var shortNameMap: MutableMap<String, Boolean>? = null
    private var classNamesVersion = -1
    
    var highlightHexColorsEnabled: Boolean = false

    private val tokens = ArrayList<HighlightToken>()

    companion object {
        private lateinit var keywords: TrieTree<Tokens>
        private lateinit var sKeywords: Array<String>

        init {
            doStaticInit()
        }

        private fun doStaticInit() {
            sKeywords = arrayOf(
                "and", "break", "do", "else", "elseif", "end", "false", "for", "function",
                "goto", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then",
                "true", "until", "while", "import", "require", "switch", "continue", "case",
                "default", "call", "collectgarbage", "compile", "coroutine", "assert", "error",
                "ipairs", "pairs", "next", "print", "rawequal", "rawget", "rawset", "select",
                "setmetatable", "getmetatable", "tonumber", "tostring", "type", "unpack",
                "lambda", "newClass", "_G", "try", "finally", "catch", "defer", "when"
            )

            val sTokens = arrayOf(
                Tokens.AND, Tokens.BREAK, Tokens.DO, Tokens.ELSE, Tokens.ELSEIF, Tokens.END,
                Tokens.FALSE, Tokens.FOR, Tokens.FUNCTION, Tokens.GOTO, Tokens.IF, Tokens.IN,
                Tokens.LOCAL, Tokens.NIL, Tokens.NOT, Tokens.OR, Tokens.REPEAT, Tokens.RETURN,
                Tokens.THEN, Tokens.TRUE, Tokens.UNTIL, Tokens.WHILE, Tokens.IMPORT,
                Tokens.REQUIRE, Tokens.SWITCH, Tokens.CONTINUE, Tokens.CASE, Tokens.DEFAULT,
                Tokens.CALL, Tokens.COLLECTGARBAGE, Tokens.COMPILE, Tokens.COROUTINE,
                Tokens.ASSERT, Tokens.ERROR, Tokens.IPAIRS, Tokens.PAIRS, Tokens.NEXT,
                Tokens.PRINT, Tokens.RAWEQUAL, Tokens.RAWGET, Tokens.RAWSET, Tokens.SELECT,
                Tokens.SETMETATABLE, Tokens.GETMETATABLE, Tokens.TONUMBER, Tokens.TOSTRING,
                Tokens.TYPE, Tokens.UNPACK, Tokens.LAMBDA, Tokens.NEWCLASS, Tokens._G,
                Tokens.TRY, Tokens.FINALLY, Tokens.CATCH, Tokens.DEFER, Tokens.WHEN
            )

            keywords = TrieTree()
            for (i in sKeywords.indices) {
                keywords.put(sKeywords[i], sTokens[i])
            }
        }
    }

    /**
     * 构造函数
     *
     * @param src 源代码
     * @param state 状态对象
     */
    constructor(src: CharSequence, state: State?) {
        this.source = src
        this.state = state
        init()
    }

    private fun init() {
        line = 0
        column = 0
        length = 0
        index = 0
        currToken = Tokens.WHITESPACE
        lcCal = false
        bufferLen = source.length
    }

    /**
     * 设置类名集合
     *
     * @param classNames 类名集合
     */
    fun setClassNames(classNames: Collection<String>?) {
        this.classNames.clear()
        classNames?.let {
            this.classNames.addAll(it)

            shortNameMap = HashMap()
            for (fullName in it) {
                val lastDot = fullName.lastIndexOf('.')
                if (lastDot != -1) {
                    val shortName = fullName.substring(lastDot + 1)
                    shortNameMap!![shortName] = true
                }
            }
        }
    }

    /**
     * 设置长字符串等号计数
     *
     * @param count 等号计数
     */
    fun setLongStringEqualCount(count: Int) {
        longStringEqualCount = count
    }

    /**
     * 设置是否在长字符串中
     *
     * @param inLongString 是否在长字符串中
     */
    fun setInLongString(inLongString: Boolean) {
        this.inLongString = inLongString
    }

    /**
     * 获取是否在长字符串中
     */
    fun isInLongString(): Boolean {
        return inLongString
    }

    /**
     * 获取长注释等号计数
     */
    fun getLongCommentEqualCount(): Int {
        return longCommentEqualCount
    }

    /**
     * 获取长字符串等号计数
     */
    fun getLongStringEqualCount(): Int {
        return longStringEqualCount
    }

    /**
     * 设置类名版本
     *
     * @param version 版本号
     */
    fun setClassNamesVersion(version: Int) {
        classNamesVersion = version
    }

    /**
     * 获取类名版本
     */
    fun getClassNamesVersion(): Int {
        return classNamesVersion
    }

    /**
     * 回退指定长度
     *
     * @param length 回退长度
     */
    fun pushBack(length: Int) {
        if (length > getTokenLength()) {
            throw IllegalArgumentException("pushBack length too large")
        }
        this.length -= length
    }

    private fun isIdentifierPart(ch: Char): Boolean {
        return MyCharacter.isJavaIdentifierPart(ch)
    }

    private fun isIdentifierStart(ch: Char): Boolean {
        return MyCharacter.isJavaIdentifierStart(ch)
    }

    /**
     * 获取标记文本
     */
    fun getTokenText(): CharSequence {
        return source.subSequence(offset, offset + length)
    }

    /**
     * 获取标记长度
     */
    fun getTokenLength(): Int {
        return length
    }

    /**
     * 获取当前标记
     */
    fun getToken(): Tokens {
        return currToken
    }

    private fun charAt(i: Int): Char {
        if (i < 0 || i >= source.length) {
            throw IndexOutOfBoundsException("Index: $i, Size: ${source.length}")
        }
        return source[i]
    }

    private fun charAt(): Char {
        return source[offset + length]
    }

    /**
     * 获取下一个标记
     */
    fun nextToken(): Tokens {
        val nextTokenInternal = nextTokenInternal()
        currToken = nextTokenInternal
        return nextTokenInternal
    }

    fun nextTokenInternal(): Tokens {
        tokens.clear()

        if (lcCal) {
            var r = false
            for (i in offset until offset + length) {
                val ch = charAt(i)
                when (ch) {
                    '\r' -> {
                        r = true
                        line++
                        column = 0
                    }

                    '\n' -> {
                        if (r) {
                            r = false
                        } else {
                            line++
                            column = 0
                        }
                    }

                    else -> {
                        r = false
                        column++
                    }
                }
            }
        }

        index += length
        offset += length

        if (offset >= bufferLen) {
            return Tokens.EOF
        }

        val ch2 = source[offset]
        length = 1

        return when {
            ch2 == '\n' -> Tokens.NEWLINE
            ch2 == '\r' -> {
                scanNewline()
                Tokens.NEWLINE
            }

            isWhitespace(ch2) -> {
                while (offset + length < bufferLen) {
                    val chLocal = charAt(offset + length)
                    if (!isWhitespace(chLocal) || chLocal == '\r' || chLocal == '\n') {
                        break
                    }
                    length++
                }
                Tokens.WHITESPACE
            }

            isIdentifierStart(ch2) -> scanIdentifier(ch2)
            else -> scanOther(ch2)
        }
    }

    protected fun scanOther(ch2: Char): Tokens {
        var nextch: Char = 0.toChar()
        if (offset + 1 < bufferLen) {
            nextch = source[offset + 1]
        }

        // 处理三字符运算符
        if (offset + 2 < bufferLen) {
            val threeCharSeq = source.subSequence(offset, offset + 3).toString()
            when (threeCharSeq) {
                "<--" -> {
                    length = 3
                    return Tokens.ARROW_LEFT_LONG
                }

                "//=" -> {
                    length = 3
                    return Tokens.SLASH_SLASH_EQ
                }

                ">>=" -> {
                    length = 3
                    return Tokens.GTGT_EQ
                }

                "<<=" -> {
                    length = 3
                    return Tokens.LTLT_EQ
                }

                "<=>" -> {
                    length = 3
                    return Tokens.SPACESHIP
                }

                "..=" -> {
                    length = 3
                    return Tokens.DOT_DOT_EQ
                }

                "..<" -> {
                    length = 3
                    return Tokens.DOT_DOT_LT
                }

                "?.." -> {
                    length = 3
                    return Tokens.QUESTION_DOT_DOT
                }

                "===" -> {
                    length = 3
                    return Tokens.EQEQEQ
                }

                "!==" -> {
                    length = 3
                    return Tokens.NEQEQ
                }

                "/**" -> {
                    length = 3
                    return Tokens.SLASH_STAR_STAR
                }

                "###" -> {
                    length = 3
                    return Tokens.HASH_HASH_HASH
                }
            }
        }

        // 处理两字符运算符
        if (offset + 1 < bufferLen) {
            val twoCharSeq = source.subSequence(offset, offset + 2).toString()
            when (twoCharSeq) {
                "->" -> {
                    length = 2
                    return Tokens.ARROW
                }

                "<-" -> {
                    length = 2
                    return Tokens.ARROW_LEFT
                }

                "=>" -> {
                    length = 2
                    return Tokens.FAT_ARROW
                }

                "==" -> {
                    length = 2
                    return Tokens.EQEQ
                }

                "!=" -> {
                    length = 2
                    return Tokens.NEQ
                }

                "~=" -> {
                    length = 2
                    return Tokens.TILDE_EQ
                }

                "<=" -> {
                    length = 2
                    return Tokens.LEQ
                }

                ">=" -> {
                    length = 2
                    return Tokens.GEQ
                }

                "++" -> {
                    length = 2
                    return Tokens.PLUS_PLUS
                }

                "+=" -> {
                    length = 2
                    return Tokens.PLUS_EQ
                }

                "-=" -> {
                    length = 2
                    return Tokens.MINUS_EQ
                }

                "*=" -> {
                    length = 2
                    return Tokens.STAR_EQ
                }

                "/=" -> {
                    length = 2
                    return Tokens.SLASH_EQ
                }

                "%=" -> {
                    length = 2
                    return Tokens.PERCENT_EQ
                }

                "&=" -> {
                    length = 2
                    return Tokens.AMP_EQ
                }

                "|=" -> {
                    length = 2
                    return Tokens.BAR_EQ
                }

                "^=" -> {
                    length = 2
                    return Tokens.CARET_EQ
                }

                "=<" -> {
                    length = 2
                    return Tokens.EQ_LT
                }

                "::" -> {
                    length = 2
                    return Tokens.COLON_COLON
                }

                ".." -> {
                    var dotCount = 2
                    while (offset + dotCount < bufferLen &&
                        charAt(offset + dotCount) == '.'
                    ) {
                        dotCount++
                    }
                    length = dotCount
                    return Tokens.DOT_DOT
                }

                "//" -> {
                    length = 2
                    return Tokens.SLASH_SLASH
                }

                "\\\\" -> {
                    length = 2
                    return Tokens.BACKSLASH_BACKSLASH
                }

                "/*" -> {
                    length = 2
                    return Tokens.SLASH_STAR
                }

                "*/" -> {
                    length = 2
                    return Tokens.STAR_SLASH
                }

                "~~" -> {
                    length = 2
                    return Tokens.TILDE_TILDE
                }

                "^^" -> {
                    length = 2
                    return Tokens.CARET_CARET
                }

                "##" -> {
                    length = 2
                    return Tokens.HASH_HASH
                }

                "$$" -> {
                    length = 2
                    return Tokens.DOLLAR_DOLLAR
                }

                "@@" -> {
                    length = 2
                    return Tokens.AT_AT
                }

                ":=" -> {
                    length = 2
                    return Tokens.COLON_EQ
                }

                "=:" -> {
                    length = 2
                    return Tokens.EQ_COLON
                }

                "?." -> {
                    length = 2
                    return Tokens.QUESTION_DOT
                }

                "?:" -> {
                    length = 2
                    return Tokens.QUESTION_COLON
                }

                "?=" -> {
                    length = 2
                    return Tokens.QUESTION_EQ
                }

                "?-" -> {
                    length = 2
                    return Tokens.QUESTION_MINUS
                }

                "?+" -> {
                    length = 2
                    return Tokens.QUESTION_PLUS
                }

                "!!" -> {
                    length = 2
                    return Tokens.NOT_NOT
                }

                "??" -> {
                    length = 2
                    return Tokens.NULL_COALESCING
                }

                "**" -> {
                    length = 2
                    return Tokens.STAR_STAR
                }

                "-|" -> {
                    length = 2
                    return Tokens.MINUS_BAR
                }

                "|>" -> {
                    length = 2
                    return Tokens.BAR_GT
                }

                "<|" -> {
                    length = 2
                    return Tokens.LT_BAR
                }

                ">>" -> {
                    length = 2
                    return Tokens.GTGT
                }

                "<<" -> {
                    length = 2
                    return Tokens.LTLT
                }
            }
        }

        if (isPrimeDigit(ch2)) {
            return scanNumber()
        }

        return when (ch2) {
            ';' -> Tokens.SEMICOLON
            '(' -> Tokens.LPAREN
            ')' -> Tokens.RPAREN
            ':' -> Tokens.COLON
            '<' -> scanLT()
            '>' -> scanGT()
            '!' -> if (nextch == '=') {
                length = 2
                Tokens.NEQ
            } else {
                Tokens.NOT
            }

            '"', '\'' -> {
                scanStringLiteral()
            }

            '#', '$', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L',
            'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
            'Y', 'Z', '\\', '_', '`', 'a', 'b', 'c', 'd', 'e', 'f', 'g',
            'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's',
            't', 'u', 'v', 'w', 'x', 'y', 'z' -> Tokens.UNKNOWN

            '%' -> scanOperatorTwo(Tokens.MOD)
            '&' -> scanOperatorTwo(Tokens.AND)
            '*' -> scanOperatorTwo(Tokens.MUL)
            '+' -> if (nextch == '=') {
                length = 2
                Tokens.AEQ
            } else {
                scanOperatorTwo(Tokens.ADD)
            }

            ',' -> Tokens.COMMA
            '-' -> if (nextch == '>') {
                length = 2
                Tokens.ARROW
            } else {
                scanDIV()
            }

            '.' -> if (nextch == '=') {
                length = 2
                scanOperatorTwo(Tokens.DOTEQ)
            } else {
                Tokens.DOT
            }

            '/' -> scanDIV()
            '=' -> if (nextch == '=') {
                length = 2
                scanOperatorTwo(Tokens.EQEQ)
            } else if (nextch == '>') {
                length = 2
                Tokens.OP
            } else {
                scanOperatorTwo(Tokens.EQ)
            }

            '?' -> Tokens.QUESTION
            '@' -> Tokens.AT
            '[' -> scanLongString(false)
            '^' -> scanOperatorTwo(Tokens.POW)
            '{' -> Tokens.LBRACE
            '|' -> if (nextch == '>') {
                length = 2
                Tokens.OP
            } else {
                scanOperatorTwo(Tokens.OR)
            }

            '}' -> Tokens.RBRACE
            '~' -> if (nextch == '=') {
                length = 2
                Tokens.NEQ
            } else {
                Tokens.XOR
            }

            else -> Tokens.UNKNOWN
        }
    }

    protected fun throwIfNeeded() {
        if (offset + length >= bufferLen) {
            throw RuntimeException("Token too long")
        }
    }
    
    private fun isHexColor(content: CharSequence): Boolean {
    if (content.isEmpty()) return false
    val start = content[0]
    var hexStart = 0
    when (start) {
        '#' -> hexStart = 1
        '0' -> {
            if (content.length < 2) return false
            val second = content[1]
            if (second == 'x' || second == 'X') {
                hexStart = 2
            } else {
                return false
            }
        }
        else -> return false
    }
    val hexPart = content.subSequence(hexStart, content.length)
    val len = hexPart.length
    if (len != 6 && len != 8) return false
    for (i in 0 until len) {
        val c = hexPart[i]
        if (!(c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F')) {
            return false
        }
    }
    return true
}


    protected fun scanNewline() {
        if (offset + length < bufferLen && charAt(offset + length) == '\n') {
            length++
        }
    }

    protected fun scanIdentifier(c: Char): Tokens {
        var node = keywords.root.map[c] as TrieTree.Node<Tokens>?
        val startOffset = offset
        val identifierStart = startOffset

        // 扫描标识符
        while (true) {
            val currentOffset = offset
            val currentLength = length

            // 检查是否超出边界
            if (currentOffset + currentLength >= bufferLen) {
                break
            }

            // 获取下一个字符
            val nextChar = charAt(currentOffset + currentLength)

            // 检查是否是标识符的一部分
            if (!isIdentifierPart(nextChar)) {
                break
            }

            // 增加长度并更新节点
            length++
            node = node?.map?.get(nextChar) as TrieTree.Node<Tokens>?
        }

        // 获取标识符文本
        val identifier = source.subSequence(identifierStart, identifierStart + length).toString()

        // 使用HashSet的O(1)查找
        if (shortNameMap != null && shortNameMap!!.containsKey(identifier)) {
            return Tokens.CLASS_NAME
        }

        // 关键词匹配
        if (node != null && node.token != null) {
            return node.token!!
        }

        // 默认返回标识符
        return Tokens.IDENTIFIER
    }

    protected fun scanTrans() {
        throwIfNeeded()
        val ch = charAt()
        when (ch) {
            '\\', 't', 'f', 'n', 'r', '0', '"', '\'', 'b' -> length++
            'u' -> {
                length++
                for (i in 0..3) {
                    throwIfNeeded()
                    if (!isDigit(charAt(offset + length))) {
                        return
                    }
                    length++
                }
            }
        }
    }

    /**
     * 扫描长字符串
     *
     * @param isContinuation 是否继续上一行的扫描
     */
    fun scanLongString(isContinuation: Boolean): Tokens {
        offset
        var eqCount = 0

        if (!isContinuation) {
            // 解析开始标记
            var i = offset + 1 // 跳过第一个'['
            while (i < bufferLen && charAt(i) == '=') {
                eqCount++
                i++
            }

            if (i < bufferLen && charAt(i) == '[') {
                length = i - offset + 1
            } else {
                // 不是有效的长字符串，回退到普通括号
                length = 1
                return Tokens.LBRACK
            }
        } else {
            // 继续上一行的长字符串，使用状态中的等号计数
            eqCount = longStringEqualCount
        }

        // 保存等号计数用于状态管理
        longStringEqualCount = eqCount

        // 扫描直到找到匹配的结束标记
        while (offset + length < bufferLen) {
            val ch = charAt(offset + length)

            if (ch == ']') {
                // 检查是否是结束标记
                var endIndex = offset + length + 1
                var endEqCount = 0

                // 计算结束标记的等号数量
                while (endIndex < bufferLen && charAt(endIndex) == '=') {
                    endEqCount++
                    endIndex++
                }

                // 检查是否有匹配的']'
                if (endIndex < bufferLen && charAt(endIndex) == ']' && endEqCount == eqCount) {
                    // 找到匹配的结束标记
                    length = endIndex - offset + 1
                    return Tokens.LONG_STRING
                }
            }

            length++
        }

        // 没有找到结束标记，返回不完整的长字符串
        return Tokens.LONG_STRING_INCOMPLETE
    }

    protected fun scanStringLiteral(): Tokens {
    if (offset + 1 >= bufferLen) {
        return Tokens.STRING
    }

    val quote = source[offset] // 起始引号
    val startPos = offset       // 记录起始位置，用于提取内容

    while (offset + length < bufferLen) {
        val ch = source[offset + length]

        // 转义字符处理
        if (ch == '\\') {
            length++
            scanTrans()
            continue
        }

        // 字符串结束（遇到匹配的引号）
        if (ch == quote) {
            length++ // 包含结束引号
            // 提取字符串内容（不含引号）
            val content = source.subSequence(startPos + 1, startPos + length - 1).toString()
                return if (highlightHexColorsEnabled && isHexColor(content)) Tokens.HEX_COLOR else Tokens.STRING
        }

        // 未闭合字符串（遇到换行）
        if (ch == '\n') {
            return Tokens.STRING
        }

        length++
    }

    // 到文件末尾仍未闭合
    return Tokens.STRING
}
    protected fun scanNumber(): Tokens {
    // 检测十六进制前缀 0x 或 0X，返回 HEX_COLOR
    if (offset + 1 < bufferLen && source[offset] == '0' &&
        (source[offset + 1] == 'x' || source[offset + 1] == 'X')
    ) {
        length = 2 // 包含 "0x"
        // 扫描后续的十六进制数字（0-9A-Fa-f）
        while (offset + length < bufferLen && isHexDigit(source[offset + length])) {
            length++
        }
       return if (highlightHexColorsEnabled) Tokens.HEX_COLOR else Tokens.NUMBER
         }

    // 原有的十进制/浮点数数字处理逻辑（保持不变）
    if (offset + length == bufferLen) {
        return Tokens.NUMBER
    }
    var flag = false
    while (offset + length < bufferLen && isDigit(charAt())) {
        length++
    }
    if (offset + length == bufferLen) {
        return Tokens.NUMBER
    }
    val ch = charAt()
    return when {
        ch == '.' -> {
            if (flag) {
                return Tokens.NUMBER
            }
            if (offset + length + 1 == bufferLen) {
                return Tokens.NUMBER
            }
            length++
            throwIfNeeded()
            while (offset + length < bufferLen && isDigit(charAt())) {
                length++
            }
            if (offset + length == bufferLen) {
                return Tokens.NUMBER
            }
            var ch2 = charAt()
            if (ch2 == 'e' || ch2 == 'E') {
                length++
                throwIfNeeded()
                if (charAt() == '-' || charAt() == '+') {
                    length++
                    throwIfNeeded()
                }
                while (offset + length < bufferLen && isPrimeDigit(charAt())) {
                    length++
                }
                if (offset + length == bufferLen) {
                    return Tokens.NUMBER
                }
                ch2 = charAt()
            }
            if (ch2 == 'f' || ch2 == 'F' || ch2 == 'D' || ch2 == 'd') {
                length++
            }
            Tokens.NUMBER
        }

        ch == 'l' || ch == 'L' -> {
            length++
            Tokens.NUMBER
        }

        ch == 'F' || ch == 'f' || ch == 'D' || ch == 'd' -> {
            length++
            Tokens.NUMBER
        }

        else -> Tokens.NUMBER
    }
}

    protected fun scanDIV(): Tokens {
        if (offset + 1 >= bufferLen) {
            return Tokens.DIV
        }
        val ch = charAt(offset)
        val nextChar = charAt(offset + 1)
        if (ch == '-' && nextChar == '-') {
            longCommentEqualCount = 0
            var i = offset + 2
            if (i < bufferLen && charAt(i) == '[') {
                // 可能是长注释
                i++ // 跳过'['
                while (i < bufferLen && charAt(i) == '=') {
                    longCommentEqualCount++
                    i++
                }
                if (i < bufferLen && charAt(i) == '[') {
                    // 是长注释开始
                    length = i - offset + 1
                    var finished = false
                    while (true) {
                        if (offset + length >= bufferLen) {
                            break
                        }
                        if (charAt(offset + length) == ']') {
                            var j = offset + length + 1
                            var closeEqCount = 0
                            while (j < bufferLen && charAt(j) == '=') {
                                closeEqCount++
                                j++
                            }
                            if (j < bufferLen &&
                                charAt(j) == ']' &&
                                closeEqCount == longCommentEqualCount
                            ) {
                                length = j - offset + 1
                                finished = true
                                break
                            }
                        }
                        length++
                    }
                    return if (finished) Tokens.LONG_COMMENT_COMPLETE else Tokens.LONG_COMMENT_INCOMPLETE
                }
            }
            // 单行注释
            while (offset + length < bufferLen && charAt(offset + length) != '\n') {
                length++
            }
            return Tokens.LINE_COMMENT
        }
        return Tokens.DIV
    }

    /**
     * 扫描长注释
     *
     * @param isContinuation 是否继续上一行的扫描
     */
    fun scanLongComment(isContinuation: Boolean): Tokens {
        var eqCount = 0

        if (!isContinuation) {
            // 解析开始标记
            var i = offset + 2 // 跳过"--"
            if (i < bufferLen && charAt(i) == '[') {
                i++ // 跳过'['
                while (i < bufferLen && charAt(i) == '=') {
                    eqCount++
                    i++
                }

                if (i < bufferLen && charAt(i) == '[') {
                    length = i - offset + 1
                } else {
                    // 不是有效的长注释，回退到单行注释
                    while (offset + length < bufferLen && charAt(offset + length) != '\n') {
                        length++
                    }
                    return Tokens.LINE_COMMENT
                }
            } else {
                // 不是长注释，回退到单行注释
                while (offset + length < bufferLen && charAt(offset + length) != '\n') {
                    length++
                }
                return Tokens.LINE_COMMENT
            }
        } else {
            // 继续上一行的长注释
            eqCount = longCommentEqualCount
        }

        // 保存等号计数用于状态管理
        longCommentEqualCount = eqCount

        // 扫描直到找到匹配的结束标记
        while (offset + length < bufferLen) {
            val ch = charAt(offset + length)

            if (ch == ']') {
                // 检查是否是结束标记
                var endIndex = offset + length + 1
                var endEqCount = 0

                // 计算结束标记的等号数量
                while (endIndex < bufferLen && charAt(endIndex) == '=') {
                    endEqCount++
                    endIndex++
                }

                // 检查是否有匹配的']'
                if (endIndex < bufferLen && charAt(endIndex) == ']' && endEqCount == eqCount) {
                    // 找到匹配的结束标记
                    length = endIndex - offset + 1
                    return Tokens.LONG_COMMENT_COMPLETE
                }
            }

            length++
        }

        // 没有找到结束标记，返回不完整的长注释
        return Tokens.LONG_COMMENT_INCOMPLETE
    }

    protected fun scanLT(): Tokens {
        if (offset + 1 < bufferLen) {
            val ch = source[offset + 1]
            return when (ch) {
                ':' -> {
                    length = 2
                    Tokens.CLT
                }

                '<' -> {
                    length = 2
                    Tokens.LTLT
                }

                '=' -> {
                    if (offset + 2 < bufferLen && source[offset + 2] == '>') {
                        length = 3
                        Tokens.OP
                    } else {
                        length = 2
                        Tokens.LEQ
                    }
                }

                '>' -> {
                    length = 2
                    Tokens.LTGT
                }

                else -> Tokens.LT
            }
        }
        return Tokens.LT
    }

    protected fun scanGT(): Tokens {
        if (offset + 1 < bufferLen) {
            val ch = source[offset + 1]
            return when (ch) {
                '=' -> {
                    length = 2
                    Tokens.GEQ
                }

                '>' -> {
                    length = 2
                    Tokens.GTGT
                }

                else -> Tokens.GT
            }
        }
        return Tokens.GT
    }

    protected fun scanOperatorTwo(ifWrong: Tokens): Tokens {
        return ifWrong
    }

    /**
     * 重置分词器
     *
     * @param charSequence 新的字符序列
     * @param state 状态对象
     */
    fun reset(charSequence: CharSequence, state: State) {
        reset(charSequence)
        this.state = state
    }

    /**
     * 重置分词器
     *
     * @param src 新的字符序列
     */
    fun reset(src: CharSequence) {
        source = src
        line = 0
        column = 0
        length = 0
        index = 0
        currToken = Tokens.WHITESPACE
        bufferLen = src.length
    }

    /**
     * 释放标记
     */
    fun releaseTokens() {
        tokens.forEach { token ->
            token.text = null // 释放字符串引用
        }
        tokens.clear()
        source = "" // 释放原始内容引用
    }

    private fun isHexDigit(c: Char): Boolean {
        return c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
    }

    protected fun isDigit(c: Char): Boolean {
        return c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f'
    }

    protected fun isPrimeDigit(c: Char): Boolean {
        return c in '0'..'9'
    }

    protected fun isWhitespace(c: Char): Boolean {
        return c == '\t' || c == ' ' || c == '\u000C' || c == '\n' || c == '\r'
    }
}