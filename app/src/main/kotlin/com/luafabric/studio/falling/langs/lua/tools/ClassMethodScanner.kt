package com.luafabric.studio.falling.langs.lua.tools

import com.luafabric.studio.falling.langs.lua.completion.CompletionName
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

class ClassMethodScanner {

    /**
     * 扫描指定类列表及其方法、字段信息
     *
     * @param allClassNames 需要扫描的类名列表
     * @param progressCallback 进度回调，参数为 0..1 表示进度（针对此扫描步骤）
     * @return 类信息映射表：类名 -> (成员名 -> 补全信息)
     */
    fun scanClassesAndMethods(
        allClassNames: List<String>?,
        progressCallback: ((Float) -> Unit)? = null
    ): HashMap<String, HashMap<String, CompletionName>> {
        val classInfoMap = HashMap<String, HashMap<String, CompletionName>>()

        if (allClassNames.isNullOrEmpty()) {
            return classInfoMap
        }

        var scannedCount = 0
        var skippedCount = 0
        val total = allClassNames.size
        var processed = 0

        for (className in allClassNames) {
            processed++
            try {
                // 跳过已知的问题类或不需要的类
                if (shouldSkipClass(className)) {
                    skippedCount++
                    continue
                }

                val clazz = Class.forName(className)

                // 检查类是否包含 Compose 类型的字段/方法，如果包含则跳过整个类
                if (containsComposeTypes(clazz)) {
                    println("ClassMethodScanner: Skipping class with Compose types: $className")
                    skippedCount++
                    continue
                }

                val classInfo = HashMap<String, CompletionName>()

                // 获取公共方法
                try {
                    val declaredMethods = clazz.methods
                    for (method in declaredMethods) {
                        if (method.declaringClass == Any::class.java) {
                            continue // 跳过 Object 类的方法
                        }

                        // 跳过返回 Compose 类型的方法
                        if (isComposeType(method.returnType)) {
                            continue
                        }

                        // 跳过参数包含 Compose 类型的方法
                        if (hasComposeParameters(method)) {
                            continue
                        }

                        try {
                            val generic = getParameterTypesAsString(method)
                            classInfo[method.name] = CompletionName(
                                method.returnType.name,
                                CompletionItemKind.Method,
                                method.returnType.simpleName,
                                generic
                            )
                        } catch (t: Throwable) {
                            // 跳过这个方法
                        }
                    }
                } catch (t: Throwable) {
                    // 忽略错误
                }

                // 获取公共字段
                try {
                    val declaredFields = clazz.fields
                    for (field in declaredFields) {
                        // 跳过 Compose 类型的字段
                        if (isComposeType(field.type)) {
                            continue
                        }

                        try {
                            classInfo[field.name] = CompletionName(
                                field.type.name,
                                CompletionItemKind.Field,
                                field.type.simpleName,
                                ""
                            )
                        } catch (t: Throwable) {
                            // 跳过这个字段
                        }
                    }
                } catch (t: Throwable) {
                    // 忽略错误
                }

                // 处理 getter/setter 属性
                try {
                    val declaredFields = clazz.fields
                    for (field in declaredFields) {
                        // 跳过 Compose 类型的属性
                        if (isComposeType(field.type)) {
                            continue
                        }

                        try {
                            val fieldName = field.name
                            if (fieldName.isNullOrEmpty()) {
                                continue
                            }

                            val capitalizedFieldName =
                                fieldName.substring(0, 1).uppercase() + fieldName.substring(1)

                            // Getter 方法
                            val getterMethodName = "get$capitalizedFieldName"
                            try {
                                val getterMethod = clazz.getMethod(getterMethodName)
                                // 检查 getter 返回类型
                                if (isComposeType(getterMethod.returnType)) {
                                    continue
                                }
                                classInfo[fieldName] = CompletionName(
                                    getterMethod.returnType.name,
                                    CompletionItemKind.Property,
                                    getterMethod.returnType.simpleName,
                                    ""
                                )
                            } catch (e: NoSuchMethodException) {
                                if (field.type == Boolean::class.javaPrimitiveType) {
                                    val isGetterMethodName = "is$capitalizedFieldName"
                                    try {
                                        val isGetterMethod = clazz.getMethod(isGetterMethodName)
                                        if (isComposeType(isGetterMethod.returnType)) {
                                            continue
                                        }
                                        classInfo[fieldName] = CompletionName(
                                            isGetterMethod.returnType.name,
                                            CompletionItemKind.Property,
                                            isGetterMethod.returnType.simpleName,
                                            ""
                                        )
                                    } catch (ignored: NoSuchMethodException) {
                                    }
                                }
                            }

                            // Setter 方法
                            val setterMethodName = "set$capitalizedFieldName"
                            try {
                                val setterMethod = clazz.getMethod(setterMethodName, field.type)
                                classInfo[fieldName] = CompletionName(
                                    setterMethod.returnType.name,
                                    CompletionItemKind.Property,
                                    setterMethod.returnType.simpleName,
                                    ""
                                )
                            } catch (ignored: NoSuchMethodException) {
                            }
                        } catch (t: Throwable) {
                            // 跳过这个属性处理
                        }
                    }
                } catch (t: Throwable) {
                    // 忽略错误
                }

                // 替换内部类分隔符：$ → .
                classInfoMap[className.replace("\\$".toRegex(), ".")] = classInfo
                scannedCount++

            } catch (e: ClassNotFoundException) {
                // 忽略无法加载的类
                skippedCount++
            } catch (e: NoClassDefFoundError) {
                // 忽略类定义错误
                skippedCount++
            } catch (t: Throwable) {
                skippedCount++
            }

            // 每处理 10 个类或最后一个类时更新进度
            if (processed % 10 == 0 || processed == total) {
                progressCallback?.invoke(processed.toFloat() / total)
            }
        }

        println(
            "ClassMethodScanner: Scanned " +
                    "$scannedCount classes, skipped $skippedCount classes"
        )
        return classInfoMap
    }

    /**
     * 检查类是否包含任何 Compose 相关的类型
     *
     * @param clazz 要检查的类
     * @return 如果包含 Compose 类型则返回 true
     */
    private fun containsComposeTypes(clazz: Class<*>): Boolean {
        try {
            // 检查字段类型
            for (field in clazz.declaredFields) {
                if (isComposeType(field.type)) {
                    return true
                }
            }

            // 检查方法返回类型和参数类型
            for (method in clazz.declaredMethods) {
                if (isComposeType(method.returnType)) {
                    return true
                }
                for (paramType in method.parameterTypes) {
                    if (isComposeType(paramType)) {
                        return true
                    }
                }
            }

            // 检查构造方法参数
            for (constructor in clazz.declaredConstructors) {
                for (paramType in constructor.parameterTypes) {
                    if (isComposeType(paramType)) {
                        return true
                    }
                }
            }
        } catch (t: Throwable) {
            // 如果反射失败，保守起见假设可能包含 Compose 类型
            return false
        }
        return false
    }

    /**
     * 检查是否为 Compose 类型
     *
     * @param type 要检查的类型
     * @return 如果是 Compose 类型则返回 true
     */
    private fun isComposeType(type: Class<*>?): Boolean {
        if (type == null) return false

        val typeName = type.name.lowercase()

        // 检查包名
        if (typeName.contains("androidx.compose")) return true
        if (typeName.contains("kotlinx.compose")) return true
        if (typeName.contains("com.google.accompanist")) return true

        // 检查常见的 Compose 类名（简单名称）
        val simpleName = type.simpleName.lowercase()
        val composeIndicators = arrayOf(
            "drawerstate", "mutablestate", "snapshostate", "remember",
            "composable", "composer", "composition", "recomposer",
            "modifier", "content", "slottable", "anchoreddraggablestate"
        )
        for (indicator in composeIndicators) {
            if (simpleName.contains(indicator)) {
                return true
            }
        }

        return false
    }

    /**
     * 检查方法参数是否包含 Compose 类型
     *
     * @param method 要检查的方法
     * @return 如果参数包含 Compose 类型则返回 true
     */
    private fun hasComposeParameters(method: Method): Boolean {
        for (paramType in method.parameterTypes) {
            if (isComposeType(paramType)) {
                return true
            }
        }
        return false
    }

    /**
     * 判断是否应该跳过某个类的扫描
     *
     * @param className 类名
     * @return 如果应该跳过则返回 true
     */
    private fun shouldSkipClass(className: String?): Boolean {
        if (className.isNullOrEmpty()) {
            return true
        }

        // 过滤所有 Kotlin 合成类（Lambda、匿名类等）
        if (className.contains("$$")) {
            return true // Kotlin 内部合成类
        }

        // 过滤 ExternalSyntheticLambda 类（Kotlin 编译器生成的 Lambda 类）
        if (className.contains("ExternalSyntheticLambda")) {
            return true
        }

        // 过滤所有 Compose 相关类（不区分大小写）
        val lowerClassName = className.lowercase()
        if (lowerClassName.contains("compose")) {
            println("ClassMethodScanner: Skipping Compose class: $className")
            return true
        }

        // 过滤所有 kotlinx.compose 相关类
        if (className.contains("kotlinx.compose")) {
            println("ClassMethodScanner: Skipping Kotlin Compose class: $className")
            return true
        }

        // 过滤所有 androidx.compose 相关类
        if (className.contains("androidx.compose")) {
            println("ClassMethodScanner: Skipping AndroidX Compose class: $className")
            return true
        }

        // 过滤所有 com.google.accompanist 相关类（Compose 工具库）
        if (className.contains("com.google.accompanist")) {
            println("ClassMethodScanner: Skipping Accompanist class: $className")
            return true
        }

        // 过滤所有 kotlinx.coroutines 相关类
        if (className.contains("kotlinx.coroutines")) {
            println("ClassMethodScanner: Skipping Coroutines class: $className")
            return true
        }

        // 过滤所有 androidx.lifecycle 相关类
        if (className.contains("androidx.lifecycle")) {
            println("ClassMethodScanner: Skipping Lifecycle class: $className")
            return true
        }

        // 过滤常见的 Kotlin 协程/Flow 相关类
        if (className.contains("kotlinx.coroutines.flow")) {
            return true
        }

        // 过滤 Kotlin 函数式接口
        if (className.contains("kotlin.jvm.functions.Function")) {
            return true
        }

        // 过滤 Kotlin 元数据/反射类
        if (className.contains("kotlin.Metadata") || className.contains("kotlin.reflect")) {
            return true
        }

        return false
    }

    companion object {
        /**
         * 获取链式调用的最终返回值类型
         *
         * @param classMap 存储所有类的 HashMap（短名->全名列表）
         * @param classInfoMap 类名->成员映射
         * @param input 需要解析的语句
         * @param mMap 变量名->类型名映射
         * @return 最终返回值类型
         */
        @JvmStatic
        fun getReturnType(
            classMap: HashMap<String, List<String>>?,
            classInfoMap: HashMap<String, HashMap<String, CompletionName>>?,
            input: String,
            mMap: Map<String, String>?
        ): String {
            if (input.isNullOrEmpty()) return "nullclass"

            // 如果输入已经在变量映射中，直接使用映射的值
            if (mMap != null && mMap.containsKey(input)) {
                val mappedValue = mMap[input]
                if (mappedValue != null && mappedValue != input) {
                    // 尝试递归解析映射的值
                    val result = getReturnType(classMap, classInfoMap, mappedValue, mMap)
                    if ("nullclass" != result) {
                        return result
                    }
                }
            }

            // 如果输入是一个类名，直接返回
            if (classInfoMap != null && classInfoMap.containsKey(input)) {
                return input
            }

            // 尝试按点号分割来解析链式调用
            if (input.contains(".")) {
                val parts = input.split("\\.".toRegex()).toTypedArray()
                if (parts.size >= 2) {
                    // 尝试解析第一部分
                    val firstPart = parts[0]
                    val rest = parts.sliceArray(1 until parts.size).joinToString(".")

                    // 检查第一部分是否在变量映射中
                    var firstPartType: String? = null
                    if (mMap != null && mMap.containsKey(firstPart)) {
                        firstPartType = mMap[firstPart]
                    } else if (classMap != null && classMap.containsKey(firstPart)) {
                        val classList = classMap[firstPart]
                        if (classList != null && classList.isNotEmpty()) {
                            firstPartType = classList[0]
                        }
                    }

                    if (firstPartType != null) {
                        // 现在我们有第一部分的类型，尝试获取剩余部分的返回类型
                        val result = getReturnTypeFromClassInfo(classInfoMap, firstPartType, rest)
                        if ("nullclass" != result) {
                            return result
                        }
                    }
                }
            }

            // 如果输入看起来像一个类名（包含点号），尝试直接作为类名查找
            if (input.contains(".") && classInfoMap != null && classInfoMap.containsKey(input)) {
                return input
            }

            // 尝试在 classMap 中查找
            if (classMap != null && classMap.containsKey(input)) {
                val classList = classMap[input]
                if (classList != null && classList.isNotEmpty()) {
                    return classList[0]
                }
            }

            return "nullclass"
        }

        /**
         * 从类信息中获取成员调用的返回类型
         */
        private fun getReturnTypeFromClassInfo(
            classInfoMap: HashMap<String, HashMap<String, CompletionName>>?,
            className: String?,
            memberChain: String?
        ): String {
            if (className == null || memberChain == null || classInfoMap == null) {
                return "nullclass"
            }

            // 获取类信息
            val classInfo = classInfoMap[className]
            if (classInfo == null) {
                return "nullclass"
            }

            // 如果成员链包含点号，递归处理
            if (memberChain.contains(".")) {
                val parts = memberChain.split("\\.", limit = 2)
                val firstMember = parts[0]
                val rest = if (parts.size > 1) parts[1] else ""

                val memberInfo = classInfo[firstMember]
                if (memberInfo == null) {
                    return "nullclass"
                }

                val memberType = memberInfo.name
                if ("void" == memberType) {
                    return "void"
                }

                // 递归解析剩余的成员链
                return getReturnTypeFromClassInfo(classInfoMap, memberType, rest)
            } else {
                // 单个成员，直接查找
                val memberInfo = classInfo[memberChain]
                if (memberInfo == null) {
                    return "nullclass"
                }

                val memberType = memberInfo.name
                return if ("void" == memberType) "void" else memberType
            }
        }

        /**
         * 获取方法参数类型字符串表示
         */
        @JvmStatic
        fun getParameterTypesAsString(method: Method): String {
            val list = ArrayList<String>()
            try {
                for (t in method.genericParameterTypes) {
                    try {
                        list.add(typeToString(t))
                    } catch (e: Throwable) {
                        // 如果某个参数类型无法转换，使用其简单名称
                        list.add(getTypeSimpleName(t))
                    }
                }
            } catch (t: Throwable) {
                // 如果获取参数类型失败，返回空字符串
                return ""
            }
            return list.joinToString(", ")
        }

        /**
         * 将 Type 对象转换为字符串
         */
        private fun typeToString(type: Type): String {
            when (type) {
                is Class<*> -> return type.name
                is ParameterizedType -> {
                    val sb = StringBuilder(typeToString(type.rawType))
                    val args = type.actualTypeArguments
                    if (args.isNotEmpty()) {
                        sb.append("<")
                        for (i in args.indices) {
                            sb.append(typeToString(args[i]))
                            if (i < args.size - 1) sb.append(", ")
                        }
                        sb.append(">")
                    }
                    return sb.toString()
                }

                is GenericArrayType -> {
                    return typeToString(type.genericComponentType) + "[]"
                }

                is TypeVariable<*> -> {
                    return type.name
                }

                else -> return "?"
            }
        }

        /**
         * 获取类型的简单名称，避免复杂的泛型解析
         */
        private fun getTypeSimpleName(type: Type): String {
            when (type) {
                is Class<*> -> return type.simpleName
                is ParameterizedType -> {
                    val rawType = type.rawType
                    if (rawType is Class<*>) {
                        return rawType.simpleName
                    }
                }
            }
            return "Object"
        }
    }
}