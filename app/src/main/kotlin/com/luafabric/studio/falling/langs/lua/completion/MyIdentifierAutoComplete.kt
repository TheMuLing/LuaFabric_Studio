package com.luafabric.studio.falling.langs.lua.completion

import android.os.Build
import androidx.annotation.RequiresApi
import com.luafabric.studio.falling.langs.lua.tools.ClassMethodScanner
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.util.MutableInt
import java.util.Locale
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Lua 代码自动补全核心类
 *
 * 提供以下补全功能：
 * - 关键字补全
 * - 标识符补全
 * - 类名补全（短类名和全限定类名）
 * - 类成员补全（方法、字段、属性）
 * - 包函数补全
 * - 链式调用类型推断补全
 */
class MyIdentifierAutoComplete {

    /** 基础映射表：类名 -> 成员映射 */
    var basemap: HashMap<String, HashMap<String, CompletionName>>? = null

    /** 类映射表：短类名 -> 全类名列表 */
    var classmap: HashMap<String, List<String>>? = null
        set(value) {
            field = value
            fullClassNameCache.clear()
            value?.values?.forEach { fullNames ->
                fullClassNameCache.addAll(fullNames)
            }
        }

    /** 关键字映射表 */
    private var keywordMap: Map<String, Any>? = null

    /** 关键字数组 */
    private var keywords: Array<String>? = null

    /** 关键字是否以小写形式存储 */
    private var keywordsAreLowCase = false

    /** 变量类型映射表：变量名 -> 类名 */
    var mmap: HashMap<String, String> = HashMap()

    /** 是否区分大小写 */
    private var caseSensitive: Boolean = false

    /** 包函数映射表 */
    private val packageMap: MutableMap<String, List<String>> = HashMap()

    /** 类名简化缓存：全类名 -> 短类名 */
    private val classNameCache = HashMap<String, String>()

    /** 全限定类名缓存，用于快速查找 */
    private val fullClassNameCache = mutableSetOf<String>()

    /**
     * 标识符过滤器接口
     */
    interface Identifiers {

        /**
         * 根据前缀过滤标识符
         *
         * @param prefix 前缀字符串
         * @param results 存储过滤结果的列表
         */
        fun filterIdentifiers(prefix: String, results: MutableList<String>)
    }

    /**
     * 默认构造函数
     *
     * 初始化默认变量映射
     */
    constructor() {
        mmap["activity"] = "com.androlua.LuaActivity"
        mmap["this"] = "com.androlua.LuaActivity"
    }

    /**
     * 带参数构造函数
     *
     * @param keywords 关键字数组
     * @param basemap 基础映射表
     */
    constructor(
        keywords: Array<String>?,
        basemap: HashMap<String, HashMap<String, CompletionName>>?
    ) : this() {
        setKeywords(keywords, true)
        this.basemap = basemap
    }

    /**
     * 设置大小写敏感性
     *
     * @param caseSensitive true 表示区分大小写
     */
    fun setCaseSensitive(caseSensitive: Boolean) {
        this.caseSensitive = caseSensitive
    }

    /**
     * 注册包函数
     *
     * @param packageName 包名
     * @param functions 函数列表
     */
    fun addPackage(packageName: String, functions: List<String>) {
        packageMap[packageName] = functions
    }

    /**
     * 添加单个关键字
     *
     * @param keyword 要添加的关键字
     */
    fun addKeyword(keyword: String) {
        if (keywordMap?.containsKey(keyword) == true) {
            return
        }

        val newKeywords = ArrayList<String>()
        keywords?.let { newKeywords.addAll(it) }

        if (!newKeywords.contains(keyword)) {
            newKeywords.add(keyword)
        }

        setKeywords(newKeywords.toTypedArray(), keywordsAreLowCase)
    }

    /**
     * 设置关键字列表
     *
     * @param keywords 关键字数组
     * @param lowerCase 是否转换为小写存储
     */
    fun setKeywords(keywords: Array<String>?, lowerCase: Boolean) {
        this.keywords = keywords
        this.keywordsAreLowCase = lowerCase
        val map = HashMap<String, Any>()

        keywords?.forEach { keyword ->
            map[keyword] = true
        }
        this.keywordMap = map
    }

    /**
     * 请求自动补全
     *
     * @param contentRef 文本内容引用
     * @param position 光标位置
     * @param prefix 前缀字符串
     * @param publisher 补全发布器
     * @param identifiers 标识符提供器
     */
    fun requireAutoComplete(
        contentRef: ContentReference,
        position: CharPosition,
        prefix: String,
        publisher: CompletionPublisher,
        identifiers: Identifiers?
    ) {
        val items = createCompletionItemList(prefix, identifiers, contentRef.toString())

        val comparator = Comparator<CompletionItem> { a, b ->
            fun getGroupOrder(item: CompletionItem): Int {
                return when (item.kind) {
                    CompletionItemKind.Identifier -> 0
                    CompletionItemKind.Keyword -> 1
                    CompletionItemKind.Class -> 2
                    CompletionItemKind.Method -> 3
                    CompletionItemKind.Field -> 4
                    CompletionItemKind.Property -> 5
                    else -> 6
                }
            }

            val groupA = getGroupOrder(a)
            val groupB = getGroupOrder(b)

            if (groupA != groupB) {
                groupA - groupB
            } else {
                a.label.toString().compareTo(b.label.toString(), ignoreCase = true)
            }
        }

        publisher.addItems(items)
        publisher.setComparator(comparator)
    }

    /**
     * 简化类名显示
     *
     * @param fullClassName 完整类名
     * @return 简化后的类名（仅保留最后一部分）
     */
    private fun simplifyClassName(fullClassName: String): String {
        classNameCache[fullClassName]?.let { return it }

        val simpleName = when {
            fullClassName.contains('.') -> fullClassName.substringAfterLast('.')
            fullClassName.contains('$') -> fullClassName.substringAfterLast('$')
            else -> fullClassName
        }

        classNameCache[fullClassName] = simpleName
        return simpleName
    }

    /**
     * 简化参数列表显示
     *
     * @param parameters 参数列表字符串
     * @return 简化后的参数列表（类名简化为短名）
     */
    private fun simplifyParameters(parameters: String): String {
        if (parameters.isEmpty()) return ""

        return parameters.split(',')
            .joinToString(", ") { param ->
                param.trim().split(' ').joinToString(" ") { part ->
                    if (part.contains('.') || part.contains('$')) {
                        simplifyClassName(part)
                    } else {
                        part
                    }
                }
            }
    }

    /**
     * 创建补全项列表
     *
     * @param prefix 前缀字符串
     * @param identifiers 标识符提供器
     * @param fullContent 完整文本内容（可选）
     * @return 补全项列表
     */
    fun createCompletionItemList(
        prefix: String,
        identifiers: Identifiers?,
        fullContent: String? = null
    ): List<CompletionItem> {
        if (prefix.isEmpty()) {
            return emptyList()
        }

        val result = ArrayList<CompletionItem>()
        val addedItems = HashSet<String>()

        // 处理类成员补全（如 activity.、this.、obj.method().）
        if (prefix.contains(".")) {
            val lastDotIndex = prefix.lastIndexOf('.')
            val beforeDot = prefix.substring(0, lastDotIndex)
            val afterDot = prefix.substring(lastDotIndex + 1)

            // 尝试获取变量类型
            var className: String? = mmap[beforeDot]

            // 尝试从 classmap 查找
            if (className == null) {
                val classList = classmap?.get(beforeDot)
                if (classList != null && classList.isNotEmpty()) {
                    className = classList[0]
                }
            }

            // 尝试解析链式调用
            if (className == null && beforeDot.contains(".")) {
                val resolvedType =
                    ClassMethodScanner.getReturnType(classmap, basemap, beforeDot, mmap)
                if (resolvedType != "nullclass") {
                    className = resolvedType
                }
            }

            // 获取类成员并生成补全项
            if (className != null) {
                val classMembers = basemap?.get(className)
                if (classMembers != null) {
                    for ((memberName, completionName) in classMembers) {
                        val matches = if (caseSensitive) {
                            memberName.startsWith(afterDot)
                        } else {
                            memberName.startsWith(afterDot, ignoreCase = true)
                        }

                        if (!matches || addedItems.contains("member:$memberName")) {
                            continue
                        }

                        val (displayLabel, desc) = when (completionName.type) {
                            CompletionItemKind.Method -> {
                                val params = simplifyParameters(completionName.generic)
                                val label = if (params.isNotEmpty()) {
                                    "$memberName($params)"
                                } else {
                                    "$memberName()"
                                }
                                val returnType = simplifyClassName(completionName.name)
                                label to returnType
                            }

                            CompletionItemKind.Field,
                            CompletionItemKind.Property -> {
                                memberName to simplifyClassName(completionName.name)
                            }

                            else -> {
                                memberName to simplifyClassName(completionName.name)
                            }
                        }

                        val item = SimpleCompletionItem(
                            displayLabel,
                            desc,
                            afterDot.length,
                            memberName
                        ).kind(completionName.type)

                        result.add(item)
                        addedItems.add("member:$memberName")
                    }
                }
            }
        }

        // 全限定类名补全（输入包含点时）
        if (prefix.contains(".") && result.isEmpty()) {
            val matchedFullClassNames = fullClassNameCache.filter { fullName ->
                if (caseSensitive) {
                    fullName.startsWith(prefix)
                } else {
                    fullName.startsWith(prefix, ignoreCase = true)
                }
            }

            matchedFullClassNames.take(50).forEach { fullClassName ->
                val simpleName = simplifyClassName(fullClassName)
                val itemKey = "class:$fullClassName"

                if (addedItems.contains(itemKey)) {
                    return@forEach
                }

                // 输入包含点时提交全限定类名，否则提交短类名
                val commitText = if (prefix.contains(".")) fullClassName else simpleName

                result.add(
                    SimpleCompletionItem(
                        simpleName,
                        fullClassName,
                        prefix.length,
                        commitText
                    ).kind(CompletionItemKind.Class)
                )
                addedItems.add(itemKey)
            }
        }

        // 短类名补全
        if ((!prefix.contains(".") || prefix.length <= 3) && result.isEmpty()) {
            val currentClassmap = classmap
            currentClassmap?.let { classes ->
                for ((shortClassName, fullNames) in classes) {
                    if (shortClassName.count { it == '$' } >= 2) continue

                    val matches = if (caseSensitive) {
                        shortClassName.startsWith(prefix)
                    } else {
                        shortClassName.startsWith(prefix, ignoreCase = true)
                    }

                    if (!matches || shortClassName.matches(".*\\.\\d+$".toRegex())) {
                        continue
                    }

                    fullNames.forEach { fullName ->
                        val itemKey = "class:$fullName"
                        if (addedItems.contains(itemKey)) {
                            return@forEach
                        }

                        // 输入包含点时提交全限定类名，否则提交短类名
                        val commitText = if (prefix.contains(".")) fullName else shortClassName

                        result.add(
                            SimpleCompletionItem(
                                shortClassName,
                                fullName,
                                prefix.length,
                                commitText
                            ).kind(CompletionItemKind.Class)
                        )
                        addedItems.add(itemKey)
                    }
                }
            }
        }

        // 关键字补全
        keywords?.let { kwds ->
            for (keyword in kwds) {
                if (keyword.count { it == '$' } >= 2) continue

                val matches = if (caseSensitive) {
                    keyword.startsWith(prefix)
                } else {
                    keyword.startsWith(prefix, ignoreCase = true)
                }

                if (matches && !addedItems.contains("keyword:$keyword")) {
                    result.add(
                        SimpleCompletionItem(keyword, "Keyword", prefix.length, keyword)
                            .kind(CompletionItemKind.Keyword)
                    )
                    addedItems.add("keyword:$keyword")
                }
            }
        }

        // 标识符补全（始终执行）
        if (identifiers != null) {
            val idList = ArrayList<String>()
            identifiers.filterIdentifiers(prefix, idList)

            for (id in idList) {
                if (id.count { it == '$' } >= 2) continue

                val isKeyword = keywordMap?.containsKey(
                    if (caseSensitive) id else id.lowercase(Locale.ROOT)
                ) == true

                if (isKeyword) continue

                val itemKey = "identifier:$id"
                if (addedItems.contains(itemKey)) continue

                // 尝试从 mmap 中获取类型信息
                val type = mmap[id]
                val description = if (type != null) "Variable: $type" else "Identifier"

                result.add(
                    SimpleCompletionItem(id, description, prefix.length, id)
                        .kind(CompletionItemKind.Identifier)
                )
                addedItems.add(itemKey)
            }
        }

        // 后备：从 mmap 中补充变量名（即使未在 identifiers 中出现）
        for ((varName, type) in mmap) {
            if (addedItems.contains("identifier:$varName")) continue
            val matches = if (caseSensitive) {
                varName.startsWith(prefix)
            } else {
                varName.startsWith(prefix, ignoreCase = true)
            }
            if (matches) {
                result.add(
                    SimpleCompletionItem(varName, "Variable: $type", prefix.length, varName)
                        .kind(CompletionItemKind.Identifier)
                )
                addedItems.add("identifier:$varName")
            }
        }

        // 包函数补全
        if (prefix.contains(".") && result.isEmpty()) {
            val parts = prefix.split("\\.".toRegex())
            if (parts.isEmpty()) return result

            val pkgName = parts[0]
            val funcPrefix = if (parts.size > 1) parts.last() else ""
            val showAll = prefix.endsWith(".")
            val addedFuncs = HashSet<String>()

            packageMap[pkgName]?.let { funcs ->
                for (func in funcs) {
                    if (func.count { it == '$' } >= 2) continue

                    val matches = when {
                        showAll -> true
                        caseSensitive -> func.startsWith(funcPrefix)
                        else -> func.startsWith(funcPrefix, ignoreCase = true)
                    }

                    if (!matches) continue

                    val fullPath = "$pkgName.$func"
                    val itemKey = "packagefunc:$fullPath"
                    if (addedFuncs.contains(itemKey)) continue

                    result.add(
                        SimpleCompletionItem(
                            func,
                            "Package function: $fullPath",
                            prefix.length,
                            fullPath
                        ).kind(CompletionItemKind.Function)
                    )
                    addedFuncs.add(itemKey)
                }
            }
        }

        return result
    }

    /**
     * 线程安全的标识符提供器
     */
    class SyncIdentifiers : Identifiers {

        private val lock: Lock = ReentrantLock(true)
        private val identifierMap = HashMap<String, MutableInt>()

        /**
         * 清空所有标识符
         */
        fun clear() {
            lock.lock()
            try {
                identifierMap.clear()
            } finally {
                lock.unlock()
            }
        }

        /**
         * 增加标识符计数
         *
         * @param id 标识符
         */
        @RequiresApi(Build.VERSION_CODES.N)
        fun identifierIncrease(id: String) {
            lock.lock()
            try {
                identifierMap.computeIfAbsent(id) { MutableInt(0) }.increase()
            } finally {
                lock.unlock()
            }
        }

        /**
         * 减少标识符计数
         *
         * @param id 标识符
         */
        fun identifierDecrease(id: String) {
            lock.lock()
            try {
                identifierMap[id]?.let { counter ->
                    if (counter.decreaseAndGet() <= 0) {
                        identifierMap.remove(id)
                    }
                }
            } finally {
                lock.unlock()
            }
        }

        override fun filterIdentifiers(prefix: String, results: MutableList<String>) {
            filterIdentifiers(prefix, results, true) // 默认阻塞
        }

        /**
         * 过滤标识符
         *
         * @param prefix 前缀字符串
         * @param results 存储结果的列表
         * @param block 是否阻塞等待锁（此参数已忽略，统一使用阻塞锁以保证数据完整）
         */
        fun filterIdentifiers(prefix: String, results: MutableList<String>, block: Boolean) {
            lock.lock() // 始终阻塞等待锁
            try {
                for (id in identifierMap.keys) {
                    // 简单前缀匹配，包含完全相等的情况
                    if (id.startsWith(prefix, ignoreCase = true)) {
                        results.add(id)
                    }
                }
            } finally {
                lock.unlock()
            }
        }
    }
}