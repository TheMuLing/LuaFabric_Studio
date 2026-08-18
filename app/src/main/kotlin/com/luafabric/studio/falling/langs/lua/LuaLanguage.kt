package com.luafabric.studio.falling.langs.lua

import android.os.Bundle
import com.luafabric.studio.falling.langs.lua.completion.CompletionHelper
import com.luafabric.studio.falling.langs.lua.completion.CompletionName
import com.luafabric.studio.falling.langs.lua.completion.MyIdentifierAutoComplete
import com.luafabric.studio.falling.langs.lua.completion.MyPrefixChecker
import com.luafabric.studio.falling.langs.lua.format.LuaFormatter
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.QuickQuoteHandler
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.StylesUtils
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.text.TextUtils
import io.github.rosemoe.sora.widget.SymbolPairMatch

/**
 * Lua语言实现
 * 提供语法分析、代码补全、格式化等功能
 */
class LuaLanguage : Language {
    private var autoComplete: MyIdentifierAutoComplete
    private val baseMap: HashMap<String, HashMap<String, CompletionName>>
    private val classMap: HashMap<String, List<String>>
    private val javaQuoteHandler: LuaQuoteHandler
    private val manager: LuaIncrementalAnalyzeManager
    private val map: HashMap<String, String>
    private val newlineHandlers: Array<NewlineHandler>

    private var caseSensitive: Boolean = false

    private var highlightHexColorsEnabled: Boolean = false

    companion object {
        private val DEFAULT_KEYWORDS = listOf(
            "and",
            "break",
            "case",
            "catch",
            "continue",
            "default",
            "defer",
            "newClass",
            "do",
            "else",
            "elseif",
            "end",
            "false",
            "finally",
            "for",
            "function",
            "goto",
            "if",
            "in",
            "lambda",
            "local",
            "nil",
            "not",
            "or",
            "repeat",
            "return",
            "switch",
            "then",
            "true",
            "try",
            "until",
            "when",
            "while",
            "is",
            "self",
            "__add",
            "__band",
            "__bnot",
            "__bor",
            "__bxor",
            "__call",
            "__close",
            "__concat",
            "__div",
            "__eq",
            "__gc",
            "__idiv",
            "__index",
            "__le",
            "__len",
            "__lt",
            "__mod",
            "__mul",
            "__newindex",
            "__pow",
            "__shl",
            "__shr",
            "__sub",
            "__tostring",
            "__unm",
            "_ENV",
            "_G",
            "assert",
            "collectgarbage",
            "dofile",
            "error",
            "getfenv",
            "getmetatable",
            "ipairs",
            "load",
            "loadfile",
            "loadstring",
            "module",
            "next",
            "pairs",
            "pcall",
            "print",
            "rawequal",
            "rawget",
            "rawlen",
            "rawset",
            "require",
            "select",
            "self",
            "setfenv",
            "setmetatable",
            "tointeger",
            "tonumber",
            "tostring",
            "type",
            "unpack",
            "xpcall",
            "call",
            "compile",
            "dump",
            "each",
            "enum",
            "import",
            "loadbitmap",
            "loadlayout",
            "loadmenu",
            "service",
            "set",
            "task",
            "thread",
            "timer",
            "onCreate",
            "onStart",
            "onResume",
            "onPause",
            "onStop",
            "onDestroy",
            "onActivityResult",
            "onResult",
            "onCreateOptionsMenu",
            "onOptionsItemSelected",
            "onClick",
            "onTouch",
            "onLongClick",
            "onItemClick",
            "onItemLongClick"
        )

        private val DEFAULT_PACKAGES = mapOf(
            "coroutine" to listOf(
                "create",
                "isyieldable",
                "resume",
                "running",
                "status",
                "wrap",
                "yield"
            ),
            "debug" to listOf(
                "debug", "gethook", "getinfo", "getlocal", "getmetatable", "getregistry",
                "getupvalue", "getuservalue", "sethook", "setlocal", "setmetatable", "setupvalue",
                "setuservalue", "traceback", "upvalueid", "upvaluejoin"
            ),
            "io" to listOf(
                "close",
                "flush",
                "info",
                "input",
                "isdir",
                "lines",
                "ls",
                "mkdir",
                "open",
                "output",
                "popen",
                "read",
                "readall",
                "stderr",
                "stdin",
                "stdout",
                "tmpfile",
                "type",
                "write"
            ),
            "luajava" to listOf(
                "astable", "bindClass", "clear", "coding", "createArray", "createProxy",
                "getContext", "instanceof", "loadLib", "loaded", "luapath", "new", "newArray",
                "newInstance", "override", "package", "tostring"
            ),
            "math" to listOf(
                "abs",
                "acos",
                "asin",
                "atan",
                "atan2",
                "ceil",
                "cos",
                "cosh",
                "deg",
                "exp",
                "floor",
                "fmod",
                "frexp",
                "huge",
                "ldexp",
                "log",
                "log10",
                "max",
                "maxinteger",
                "min",
                "mininteger",
                "modf",
                "pi",
                "pow",
                "rad",
                "random",
                "randomseed",
                "sin",
                "sinh",
                "sqrt",
                "tan",
                "tanh",
                "tointeger",
                "type",
                "ult"
            ),
            "os" to listOf(
                "clock",
                "date",
                "difftime",
                "execute",
                "exit",
                "getenv",
                "remove",
                "rename",
                "setlocale",
                "time",
                "tmpname"
            ),
            "package" to listOf(
                "config",
                "cpath",
                "loaded",
                "loaders",
                "loadlib",
                "path",
                "preload",
                "searchers",
                "searchpath",
                "seeall"
            ),
            "string" to listOf(
                "byte",
                "char",
                "dump",
                "find",
                "format",
                "gfind",
                "gmatch",
                "gsub",
                "len",
                "lower",
                "match",
                "rep",
                "reverse",
                "sub",
                "upper",
                "split"
            ),
            "table" to listOf(
                "clear",
                "clone",
                "concat",
                "const",
                "find",
                "foreach",
                "foreachi",
                "gfind",
                "insert",
                "maxn",
                "move",
                "pack",
                "remove",
                "size",
                "sort",
                "slice",
                "unpack"
            ),
            "utf8" to listOf(
                "byte", "char", "charpattern", "charpos", "codepoint", "codes",
                "escape", "find", "fold", "gfind", "gmatch", "gsub", "insert", "len", "lower",
                "match", "ncasecmp", "next", "offset", "remove", "reverse", "sub", "title",
                "upper", "width", "widthindex"
            )
        )
    }

    /**
     * 获取中断级别
     */
    override fun getInterruptionLevel(): Int {
        return 0
    }

    /**
     * 是否使用Tab缩进
     */
    override fun useTab(): Boolean {
        return false
    }

    /**
     * 获取格式化器
     */
    override fun getFormatter(): Formatter {
        return LuaFormatter()
    }

    /**
     * 添加包函数
     *
     * @param packageName 包名
     * @param functions 函数列表
     */
    fun addPackage(packageName: String, functions: List<String>) {
        autoComplete.addKeyword(packageName)
        autoComplete.addPackage(packageName, functions)
    }

    /**
     * 设置补全大小写敏感
     *
     * @param caseSensitive 是否大小写敏感
     */
    fun setCompletionCaseSensitive(caseSensitive: Boolean) : LuaLanguage {
        this.caseSensitive = caseSensitive
        autoComplete.setCaseSensitive(caseSensitive)
        return this
    }
    
    fun setHighlightHexColorsEnabled(enabled: Boolean) : LuaLanguage {
    highlightHexColorsEnabled = enabled
    manager.highlightHexColorsEnabled = enabled
    return this
}

    /**
     * 释放内存
     */
    fun releaseMemory() {
        manager.releaseMemory()
    }

    /**
     * 默认构造函数
     */
    constructor() : this(null, null, null)

    /**
     * 带参数构造函数
     *
     * @param baseMap 基础映射表
     * @param classMap 类映射表
     * @param androidClasses 安卓类数组
     */
    constructor(
        baseMap: HashMap<String, HashMap<String, CompletionName>>?,
        classMap: HashMap<String, List<String>>?,
        androidClasses: Array<String>?
    ) {
        this.baseMap = baseMap ?: HashMap()
        this.classMap = classMap ?: HashMap()
        this.javaQuoteHandler = LuaQuoteHandler()
        this.map = HashMap()
        this.newlineHandlers = arrayOf(BraceHandler(this))

        // 创建自动补全实例，并传入基础映射表
        this.autoComplete = MyIdentifierAutoComplete(DEFAULT_KEYWORDS.toTypedArray(), this.baseMap)
        this.autoComplete.classmap = this.classMap

        // 传递变量映射表（如果需要）
        this.autoComplete.mmap.putAll(this.map)

        this.manager = LuaIncrementalAnalyzeManager()

        for ((packageName, functions) in DEFAULT_PACKAGES) {
            addPackage(packageName, functions)
        }

        val classNameSet = androidClasses?.toSet()
        this.manager.setClassMap(classNameSet)

    }

    /**
     * 获取分析管理器
     */
    override fun getAnalyzeManager(): AnalyzeManager {
        return manager
    }

    /**
     * 获取快速引号处理器
     */
    override fun getQuickQuoteHandler(): QuickQuoteHandler {
        return javaQuoteHandler
    }

    /**
     * 销毁资源
     */
    override fun destroy() {
        // 清理资源
        releaseMemory()
    }

    /**
     * 请求自动补全
     *
     * @param contentReference 内容引用
     * @param charPosition 光标位置
     * @param completionPublisher 补全发布器
     * @param bundle 额外参数
     */
    override fun requireAutoComplete(
        contentReference: ContentReference,
        charPosition: CharPosition,
        completionPublisher: CompletionPublisher,
        bundle: Bundle
    ) {
        try {
            val computePrefix = CompletionHelper.computePrefix(
                contentReference,
                charPosition,
                MyPrefixChecker()
            )

            val syncIdentifiers = manager.identifiers
            if (syncIdentifiers != null && computePrefix.isNotEmpty()) {
                autoComplete.requireAutoComplete(
                    contentReference,
                    charPosition,
                    computePrefix,
                    completionPublisher,
                    syncIdentifiers
                )
            }
        } catch (e3: Exception) {
            e3.printStackTrace()
        }
    }

    /**
     * 获取缩进前进量
     *
     * @param contentReference 内容引用
     * @param line 行号
     * @param column 列号
     */
    override fun getIndentAdvance(contentReference: ContentReference, line: Int, column: Int): Int {
        return getIndentAdvance(contentReference.getLine(line).substring(0, column))
    }

    private fun getIndentAdvance(str: String): Int {
        val luaTextTokenizer = LuaTextTokenizer(str, State())
        var i = 0

        while (true) {
            val nextToken = luaTextTokenizer.nextToken()
            if (nextToken == Tokens.EOF) {
                break
            }

            when (nextToken) {
                Tokens.FUNCTION, Tokens.FOR, Tokens.SWITCH,
                Tokens.REPEAT, Tokens.WHILE, Tokens.IF -> i++

                Tokens.END, Tokens.RETURN, Tokens.BREAK -> i--
                else -> {}
            }
        }

        return maxOf(0, i) * 2
    }

    /**
     * 获取符号对匹配器
     */
    override fun getSymbolPairs(): SymbolPairMatch {
        return SymbolPairMatch.DefaultSymbolPairs()
    }

    /**
     * 获取换行处理器
     */
    override fun getNewlineHandlers(): Array<NewlineHandler> {
        return newlineHandlers
    }

    private fun getNonEmptyTextBefore(charSequence: CharSequence, i: Int, i2: Int): String {
        var pos = i
        while (pos > 0 && Character.isWhitespace(charSequence[pos - 1])) {
            pos--
        }
        return charSequence.subSequence(maxOf(0, pos - i2), pos).toString()
    }

    /**
     * 大括号换行处理器
     */
    class BraceHandler(private val outer: LuaLanguage) : NewlineHandler {
        /**
         * 检查是否需要处理换行
         */
        override fun matchesRequirement(
            content: Content,
            charPosition: CharPosition,
            styles: Styles?
        ): Boolean {
            val line = content.getLine(charPosition.line)
            if (styles != null && StylesUtils.checkNoCompletion(styles, charPosition)) {
                return false
            }

            val before = outer.getNonEmptyTextBefore(line, charPosition.column, 8)
            val trimmedBefore = before.trim()

            return "end" == trimmedBefore || "until" == trimmedBefore
        }

        /**
         * 处理换行
         */
        override fun handleNewline(
            content: Content,
            charPosition: CharPosition,
            styles: Styles?,
            tabSize: Int
        ): NewlineHandleResult {
            val line = content.getLine(charPosition.line)
            val column = charPosition.column
            val before = line.subSequence(0, column).toString()

            val baseIndent = TextUtils.countLeadingSpaceCount(before, tabSize)
            val indentLevel = outer.getIndentAdvance(before)

            val sb = StringBuilder("\n")
            val indentStr = TextUtils.createIndent(
                baseIndent + indentLevel,
                tabSize,
                outer.useTab()
            )
            sb.append(indentStr)

            return NewlineHandleResult(sb, indentStr.length)
        }
    }
}