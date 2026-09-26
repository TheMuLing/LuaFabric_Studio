package com.luafabric.studio.falling.ui.manual

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 手册数据源：读取 assets 中 manual.json 清单与 md 正文，并承担正文节选清洗。
 */
object ManualRepository {

    suspend fun loadPosts(context: Context): List<ManualPost> = withContext(Dispatchers.IO) {
        val json = context.assets.open("doc/manual.json").bufferedReader().use { it.readText() }
        val index = Gson().fromJson(json, ManualIndex::class.java)
        index.posts.sortedWith(
            compareBy(
                { if (it.order <= 0) Int.MAX_VALUE else it.order },
                { it.title.lowercase(Locale.ROOT) }
            )
        )
    }

    suspend fun loadMarkdown(context: Context, file: String): String = withContext(Dispatchers.IO) {
        context.assets.open(file).bufferedReader().use { it.readText() }
    }

    /**
     * 正文节选：取 md 首个空行前的段落，清洗 md 标记符号。
     * 规则：换行符用空格替代；md 标记符号过滤掉；N 个连续 tab 只用单空格替代。
     */
    fun extractExcerpt(md: String): String {
        val firstPara = md.split(Regex("\\r?\\n\\s*\\r?\\n")).firstOrNull() ?: return ""
        var text = firstPara
        // 逐行剥行首结构标记：标题 # / 引用 > / 列表 - * + / 数字序号
        text = text.lines().joinToString("\n") { line ->
            line.trimStart()
                .replace(Regex("^(#{1,6})\\s*"), "")
                .replace(Regex("^>\\s*"), "")
                .replace(Regex("^[-*+]\\s+"), "")
                .replace(Regex("^\\d+\\.\\s+"), "")
        }
        // 剥行内标记：图片/链接留文本、行内码留内容、强调符剥除
        text = text
            .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("`+([^`]+)`+"), "$1")
            .replace(Regex("\\*{1,2}([^*]+)\\*{1,2}"), "$1")
            .replace(Regex("_{1,2}([^_]+)_{1,2}"), "$1")
        // 换行 → 空格；连续空白（含 tab）→ 单空格
        return text.replace(Regex("\\s+"), " ").trim()
    }
}
