package com.luafabric.studio.falling.langs.lua.completion

import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion

/**
 * 代码补全辅助类
 * 提供计算前缀和检查取消状态的功能
 */
class CompletionHelper {

    /**
     * 前缀检查器接口
     * 用于确定哪些字符可以作为前缀的一部分
     */
    interface PrefixChecker {
        /**
         * 检查字符是否可作为前缀
         *
         * @param c 要检查的字符
         * @return 如果字符可作为前缀返回 true
         */
        fun check(c: Char): Boolean
    }

    companion object {
        /**
         * 计算当前光标位置的前缀
         * 考虑括号匹配，智能识别方法调用链
         *
         * @param contentReference 文本内容引用
         * @param charPosition 光标位置
         * @param prefixChecker 前缀检查器
         * @return 计算出的前缀字符串
         */
        @JvmStatic
        fun computePrefix(
            contentReference: ContentReference,
            charPosition: CharPosition,
            prefixChecker: PrefixChecker
        ): String {
            var i = charPosition.column
            val line = contentReference.getLine(charPosition.line)

            var count = 0

            while (true) {
                val currentCount = count
                if (i <= 0 || (currentCount == 0 && !prefixChecker.check(line[i - 1]))) {
                    break
                }

                val i5 = i - 1
                count = when (line[i5]) {
                    '(' -> if (currentCount <= 0) break else currentCount - 1
                    ')' -> currentCount + 1
                    else -> currentCount
                }
                i--
            }

            val prefix = line.substring(i, charPosition.column)
            return prefix
        }

        /**
         * 检查补全线程是否被取消
         *
         * @return 如果补全线程被取消返回 true
         */
        @JvmStatic
        fun checkCancelled(): Boolean {
            val currentThread = Thread.currentThread()
            return if (currentThread is EditorAutoCompletion.CompletionThread) {
                currentThread.isCancelled
            } else {
                false
            }
        }
    }
}