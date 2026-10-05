package com.luafabric.studio.falling.ui.forum

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.luafabric.studio.falling.core.StudioMmkv
import com.luafabric.studio.falling.native.YunJuBridge
import com.luafabric.studio.falling.ui.login.LoginRepository
import com.luafabric.studio.falling.ui.login.YunJuApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/** 发帖分类隐藏标志 JSON 键：正文首行单行 JSON（显示端剥离，用户不可见） */
private const val FORUM_MARKER_KEY = "forum_category"

/** 标签隐藏标志 JSON 键：正文首行单行 JSON（显示端剥离，用户不可见） */
private const val FORUM_TAGS_KEY = "tags"

// ---- 帖子元数据隐藏标志键（完整单词，正文首行单行 JSON）----
/** 是否付费帖 */
private const val META_KEY_PAID = "paid"
/** 收费模式："fixed" | "percent" */
private const val META_KEY_PRICE_MODE = "price_mode"
/** 固定价（金币整数） */
private const val META_KEY_FIXED_PRICE = "fixed_price"
/** 百分比模式固定底价（金币整数） */
private const val META_KEY_PERCENT_FLOOR = "percent_floor"
/** 百分比（1~90 整数） */
private const val META_KEY_PERCENT = "percent"
/** 可查看最低账号等级（0~100，0=无限制） */
private const val META_KEY_MIN_LEVEL = "min_level"

/** 收费模式：固定价 */
const val PRICE_MODE_FIXED = "fixed"

/** 收费模式：百分比 */
const val PRICE_MODE_PERCENT = "percent"

/** 付费/已购本地兜底：MMKV 域与键（pending=已购兜底；pending_payout=卖家到账待补发） */
private const val MMKV_ID_FORUM_PURCHASE = "studio_forum_purchase"
private const val MMKV_KEY_PENDING = "pending"
private const val MMKV_KEY_PENDING_PAYOUT = "pending_payout"

/** 标签词表本地缓存：MMKV 域与键 */
private const val MMKV_ID_FORUM_TAGS = "studio_forum_tags"
private const val MMKV_KEY_TAG_WORDLIST = "wordlist"

/** 搜索历史：MMKV 域与键 + 上限 */
private const val MMKV_ID_FORUM_SEARCH = "studio_forum_search"
private const val MMKV_KEY_SEARCH_HISTORY = "history"
private const val SEARCH_HISTORY_LIMIT = 20

/** 源码论坛帖子（ForumList data 项） */
data class ForumItem(
    @SerializedName("post_id") val postId: Long = 0L,
    @SerializedName("forum_id") val forumId: Int = 0,
    @SerializedName("forum_name") val forumName: String = "",
    val qq: String = "",
    val nickname: String = "",
    val title: String = "",
    val content: String = "",
    /** 帖子配图 URL（后端 img 字段，可能为空串表示无图） */
    val img: String = "",
    @SerializedName("create_time") val createTime: String = ""
)

/** ForumList 响应体 */
data class ForumListResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: List<ForumItem> = emptyList()
)

/** 论坛互动操作（评论/点赞/收藏/回复）统一响应体 */
data class ForumActionResponse(
    val code: Int = 0,
    val msg: String = ""
)

/**
 * 帖子元数据（正文首行隐藏 JSON，完整单词键）。
 * 老帖无标志 → 默认：免费 + 无等级限制 + 无标签。
 */
data class ForumPostMeta(
    val tags: List<String> = emptyList(),
    val paid: Boolean = false,
    val priceMode: String = PRICE_MODE_FIXED,
    val fixedPrice: Int = 0,
    val percentFloor: Int = 0,
    val percent: Int = 0,
    val minLevel: Int = 0
)

/** 金币 / 审计接口统一响应体（user_jb.php、fk_tj.php：1 成功、-1 失败） */
data class CoinActionResponse(
    val code: Int = 0,
    val msg: String = ""
)

/** 购买结果 */
sealed class PurchaseResult {
    /** 成功（含免费帖无需购买） */
    object Success : PurchaseResult()

    /** 失败：未扣款或不可购买 */
    data class Fail(val message: String) : PurchaseResult()

    /** 已扣款、已标记已购，但卖家到账失败（已入待补发队列，下次启动重试） */
    data class PayoutPending(val message: String) : PurchaseResult()
}

/** 待补发收款条目（加卖家金币失败时落库，下次启动重试） */
data class PendingPayout(
    @SerializedName("post_id") val postId: Long = 0L,
    val seller: String = "",
    val cost: Int = 0
)

/**
 * 源码论坛数据源：native 直发 https://yuju.99kpk.top:81/lt/ForumList.php
 * （user/admin/forum_id 硬编码在 native），Kotlin 仅负责解析。
 */
object ForumRepository {

    private val gson = Gson()

    /**
     * @param forumId 板块 ID（1=源码实例，2=完整项目）
     * @return code==200 的帖子列表；被门控/网络失败/解析失败返回空列表
     */
    fun loadPosts(context: Context, forumId: Int = 1): List<ForumItem> {
        val raw = YunJuBridge.nativeForumList(context, forumId)
        if (raw == null) {
            android.util.Log.i("ForumLoad", "forumId=$forumId native返回null（被门控/网络失败）")
            return emptyList()
        }
        // 响应为 HTTP 头 + JSON body，取首个 '{' 起的完整 JSON；尾部可能残留
        // 块传输终结标记（\r\n0\r\n\r\n）/NUL/下行字节 —— 先定位根对象收尾的 '}' 再截断
        val jsonStart = raw.indexOf('{')
        if (jsonStart < 0) {
            android.util.Log.i(
                "ForumLoad",
                "forumId=$forumId 无JSON起始'{'，raw长度=${raw.length} 前200=${raw.take(200)}"
            )
            return emptyList()
        }
        var jsonBody = raw.substring(jsonStart)
        val jsonEnd = jsonBody.lastIndexOf('}')
        if (jsonEnd >= 0) jsonBody = jsonBody.substring(0, jsonEnd + 1)
        val resp = try {
            gson.fromJson(jsonBody, ForumListResponse::class.java)
        } catch (e: Exception) {
            // 前/后24码点逐字符打印：暴露 BOM/控制字符/畸形字节（如 \uFEFF 65279、\r 13、\n 10）
            val codePoints = jsonBody.take(24).map { it.code }.joinToString(",")
            val tailPoints = jsonBody.takeLast(24).map { it.code }.joinToString(",")
            android.util.Log.i(
                "ForumLoad",
                "forumId=$forumId JSON解析失败：$e 前500=${jsonBody.take(500)} 前24码点=$codePoints 后24码点=$tailPoints"
            )
            null
        }
        if (resp == null) return emptyList()
        if (resp.code != 200) {
            android.util.Log.i(
                "ForumLoad",
                "forumId=$forumId code=${resp.code} msg=${resp.msg} 帖子数=${resp.data.size}"
            )
            return emptyList()
        }
        android.util.Log.i("ForumLoad", "forumId=$forumId code=200 帖子数=${resp.data.size} msg=${resp.msg}")
        return resp.data
    }

    /** 帖子头像：QQ 头像（qlogo，任意 QQ 返回头像） */
    fun avatarUrl(qq: String): String = YunJuApi.avatarUrl(qq)

    /**
     * 从带 HTTP 头的原始响应提取纯 JSON body（复用 loadPosts 的裁剪策略：
     * 取首个 '{' 起、根对象收尾 '}' 截断）。解析失败返回 null。
     */
    private fun extractJson(raw: String): String? {
        val jsonStart = raw.indexOf('{')
        if (jsonStart < 0) return null
        var jsonBody = raw.substring(jsonStart)
        val jsonEnd = jsonBody.lastIndexOf('}')
        if (jsonEnd >= 0) jsonBody = jsonBody.substring(0, jsonEnd + 1)
        return jsonBody
    }

    /** 互动操作返回 code（200=成功，0=失败）；null=被门控/网络失败/解析失败 */
    private fun actionCode(raw: String?): Int? {
        if (raw == null) return null
        val json = extractJson(raw) ?: return null
        return try {
            gson.fromJson(json, ForumActionResponse::class.java).code
        } catch (e: Exception) {
            android.util.Log.i("ForumAction", "响应解析失败：$e 原文=${raw.take(300)}")
            null
        }
    }

    /** 发表评论（qq/nickname 为当前登录用户；user 由 native 内部常量填充） */
    fun comment(
        context: Context,
        postId: Long,
        qq: String,
        nickname: String,
        content: String
    ): Boolean {
        val raw = YunJuBridge.nativeComment(context, postId, qq, nickname, content)
        val code = actionCode(raw)
        android.util.Log.i("ForumComment", "postId=$postId qq=$qq content=$content code=$code")
        return code == 200
    }

    /**
     * 拉取评论 + 回复列表（CommentList）。
     * @return 裁剪后的纯 JSON body 原文；null=失败。
     * 后续真机确认 data 字段名后再按 Gson 模型解析。
     */
    fun loadComments(context: Context, postId: Long): String? {
        val raw = YunJuBridge.nativeCommentList(context, postId)
        if (raw == null) {
            android.util.Log.i("ForumComment", "postId=$postId 评论列表 native 返回 null（门控/网络失败）")
            return null
        }
        // 埋点：完整响应原文，用于真机确认 CommentList data 字段名
        android.util.Log.i("ForumComment", "postId=$postId 评论原文=${raw.trim().take(500)}")
        return extractJson(raw)
    }

    /** 点赞/取消（qq 为当前登录用户；user 由 native 内部常量填充） */
    fun praise(context: Context, postId: Long, qq: String): Boolean {
        val raw = YunJuBridge.nativePraise(context, postId, qq)
        val code = actionCode(raw)
        android.util.Log.i("ForumLike", "postId=$postId qq=$qq code=$code")
        return code == 200
    }

    /** 收藏/取消（qq 为当前登录用户；user 由 native 内部常量填充） */
    fun follow(context: Context, postId: Long, qq: String): Boolean {
        val raw = YunJuBridge.nativeFollow(context, postId, qq)
        val code = actionCode(raw)
        android.util.Log.i("ForumFollow", "postId=$postId qq=$qq code=$code")
        return code == 200
    }

    /** 回复评论（qq/nickname 为当前登录用户；user 由 native 内部常量填充） */
    fun commentReply(
        context: Context,
        postId: Long,
        commentId: Long,
        qq: String,
        nickname: String,
        content: String
    ): Boolean {
        val raw = YunJuBridge.nativeCommentReply(
            context, postId, commentId, qq, nickname, content
        )
        val code = actionCode(raw)
        android.util.Log.i("ForumComment", "postId=$postId commentId=$commentId code=$code")
        return code == 200
    }

    /**
     * 后端发帖/评论时间候选格式（真机样本既有 yy-MM-dd HH:mm 也有 yyyy-MM-dd HH:mm:ss，
     * 单一格式解析失败会导致相对日期开关「无效」——逐一尝试候选，任一命中即算）。
     */
    private val FORUM_TIME_FORMATS = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
        SimpleDateFormat("yy-MM-dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
    )

    /**
     * 相对发帖时间文案：刚刚 / N分钟前 / N小时前 / N天前 / N个月前 / N年前。
     * 不再设 7 天封顶——封顶会让「相对发帖日期」开关对旧帖看起来完全无效。
     * 解析失败或格式异常时原样返回 createTime，绝不因格式化问题吞掉时间。
     */
    fun formatRelativeTime(createTime: String): String {
        val parsed = FORUM_TIME_FORMATS.firstNotNullOfOrNull { f ->
            runCatching { f.parse(createTime) }.getOrNull()
        } ?: return createTime
        // 未来时间（设备时钟偏差）按「刚刚」，避免出现负数文案
        val diff = (System.currentTimeMillis() - parsed.time).coerceAtLeast(0L)
        val minute = 60_000L
        val hour = 3_600_000L
        val day = 86_400_000L
        val month = 30 * day
        val year = 365 * day
        return when {
            diff < minute -> "刚刚"
            diff < hour -> "${diff / minute}分钟前"
            diff < day -> "${diff / hour}小时前"
            diff < month -> "${diff / day}天前"
            diff < year -> "${diff / month}个月前"
            else -> "${diff / year}年前"
        }
    }

    /**
     * 帖子按发帖时间倒序（跨板块合并列表用）。
     * 时间解析失败者视为最早（排最后）；同值保持原有相对顺序（Kotlin 稳定排序）。
     */
    fun sortByTimeDesc(posts: List<ForumItem>): List<ForumItem> =
        posts.sortedByDescending { post ->
            FORUM_TIME_FORMATS.firstNotNullOfOrNull { f ->
                runCatching { f.parse(post.createTime) }.getOrNull()
            }?.time ?: Long.MIN_VALUE
        }

    /** FileUpload 响应体（code=1 成功，url 为图片直链） */
    private data class ForumUploadResponse(
        val code: Int = 0,
        val msg: String = "",
        val url: String = ""
    )

    /**
     * 图片单次直传（FileUpload.php，multipart/form-data，native 直发 yuju:81）。
     * @param filePath 本地压缩后图片绝对路径；fileName 原始文件名；fileType MIME
     * @return 上传成功返回图片直链；被门控/失败/解析失败返回 null
     */
    fun uploadImage(
        context: Context,
        filePath: String,
        fileName: String,
        fileType: String = "image/jpeg"
    ): String? {
        val raw = YunJuBridge.nativeUploadImage(context, filePath, fileName, fileType)
        if (raw == null) {
            android.util.Log.i("ForumUpload", "native 返回 null（门控/网络失败）")
            return null
        }
        val json = extractJson(raw) ?: return null
        val resp = runCatching { gson.fromJson(json, ForumUploadResponse::class.java) }.getOrNull()
            ?: run {
                android.util.Log.i("ForumUpload", "响应解析失败：${json.take(300)}")
                return null
            }
        if (resp.code != 1) {
            android.util.Log.i("ForumUpload", "code=${resp.code} msg=${resp.msg}")
            return null
        }
        android.util.Log.i("ForumUpload", "code=1 msg=${resp.msg} url=${resp.url}")
        return resp.url.takeIf { it.isNotBlank() }
    }

    /**
     * 发帖（Issue.php，native 直发 yuju:81）。
     * @param content 需已拼接标签隐藏标志前缀（withTagsMarker；无标签则原样）
     * @param img 图片直链（无图传空串）
     * @return code==200 成功
     */
    fun submitPost(
        context: Context,
        qq: String,
        nickname: String,
        forumId: Int,
        title: String,
        content: String,
        img: String
    ): Boolean {
        val raw = YunJuBridge.nativeIssuePost(
            context, qq, nickname, forumId, title, content, img
        )
        val code = actionCode(raw)
        android.util.Log.i("ForumIssue", "forumId=$forumId title=$title code=$code")
        return code == 200
    }

    /**
     * 剥离正文首行隐藏标志（分类 `forum_category` 或标签 `tags`）。
     * 首行恰为含上述任一键的 JSON 对象时整行剥除；单行即标志（正文为空）时返回空串；
     * 其余情况（旧帖无标志/用户正文恰好以单行 JSON 开头）原样返回，不误伤内容。
     */
    fun stripCategoryMarker(content: String): String {
        if (!content.startsWith("{")) return content
        val nl = content.indexOf('\n')
        val head = if (nl < 0) content else content.substring(0, nl)
        return try {
            val obj = JsonParser.parseString(head).asJsonObject
            if (obj.has(FORUM_MARKER_KEY) || obj.has(FORUM_TAGS_KEY)) {
                if (nl < 0) "" else content.substring(nl + 1)
            } else {
                content
            }
        } catch (e: Exception) {
            content
        }
    }

    // ---- 标签（全局单一受控词表 + 正文首行隐藏标志）----

    /** 构建元数据标志：完整单词键的单行 JSON（恒含 tags，显示端剥离，用户不可见） */
    fun buildMetaMarker(meta: ForumPostMeta): String {
        val arr = JsonArray()
        meta.tags.forEach { arr.add(it) }
        val obj = JsonObject()
        obj.add(FORUM_TAGS_KEY, arr)
        obj.addProperty(META_KEY_PAID, meta.paid)
        obj.addProperty(META_KEY_PRICE_MODE, meta.priceMode)
        obj.addProperty(META_KEY_FIXED_PRICE, meta.fixedPrice)
        obj.addProperty(META_KEY_PERCENT_FLOOR, meta.percentFloor)
        obj.addProperty(META_KEY_PERCENT, meta.percent)
        obj.addProperty(META_KEY_MIN_LEVEL, meta.minLevel)
        return obj.toString()
    }

    /** 发帖提交前：元数据标志行 + 换行 + 正文（恒写标志，免费帖也写 paid:false） */
    fun withMetaMarker(content: String, meta: ForumPostMeta): String {
        val marker = buildMetaMarker(meta)
        return if (content.isEmpty()) marker else "$marker\n$content"
    }

    /**
     * 解析正文首行元数据标志；无标志/解析失败返回默认值
     * （老帖：免费 + minLevel 0 + 无标签）。
     */
    fun parseMeta(content: String): ForumPostMeta {
        if (!content.startsWith("{")) return ForumPostMeta()
        val nl = content.indexOf('\n')
        val head = if (nl < 0) content else content.substring(0, nl)
        return try {
            val obj = JsonParser.parseString(head).asJsonObject
            if (!obj.has(FORUM_TAGS_KEY)) return ForumPostMeta()
            ForumPostMeta(
                tags = obj.getAsJsonArray(FORUM_TAGS_KEY)
                    ?.mapNotNull { it.asString.takeIf { s -> s.isNotBlank() } }
                    ?: emptyList(),
                paid = obj.get(META_KEY_PAID)?.asBoolean ?: false,
                priceMode = obj.get(META_KEY_PRICE_MODE)?.asString ?: PRICE_MODE_FIXED,
                fixedPrice = obj.get(META_KEY_FIXED_PRICE)?.asInt ?: 0,
                percentFloor = obj.get(META_KEY_PERCENT_FLOOR)?.asInt ?: 0,
                percent = obj.get(META_KEY_PERCENT)?.asInt ?: 0,
                minLevel = obj.get(META_KEY_MIN_LEVEL)?.asInt ?: 0
            )
        } catch (e: Exception) {
            ForumPostMeta()
        }
    }

    /** 解析正文首行标签（parseMeta 的标签视图；无标志返回空列表） */
    fun parseTags(content: String): List<String> = parseMeta(content).tags

    /** 成交价：固定价直取；百分比 = max(底价, round(买家当前金币 × 百分比/100))（四舍五入） */
    fun computeCost(meta: ForumPostMeta, buyerCoin: Int): Int = when (meta.priceMode) {
        PRICE_MODE_PERCENT ->
            maxOf(meta.percentFloor, Math.round(buyerCoin * meta.percent / 100.0).toInt())
        else -> meta.fixedPrice
    }

    // ---- 正文节选（完整 Lua 注释语义）----

    /**
     * 列表卡片正文节选：
     * 1) 剥离首行元数据标志；
     * 2) 跳过开头 Lua 注释，取首个非注释内容为起点；
     * 3) 换行符 / 连续 Tab / 连续空白 → 单空格；
     * 4) 节选中所有 Lua 注释（`--` 行注释、`--[[ ]]` · `--[==[ ]==]` 块注释）全部剔除；
     *    字符串（`"..."` `'...'` 与长括号字符串 `[[...]]`）内的 `--` 受保护，不作注释处理。
     */
    fun buildExcerpt(content: String): String {
        val body = stripCategoryMarker(content)
        val kept = StringBuilder(body.length)
        var i = 0
        val n = body.length
        while (i < n) {
            val c = body[i]
            // 注释：`--` 起始（行注释，或 `--[[`·`--[==[` 块注释）
            if (c == '-' && i + 1 < n && body[i + 1] == '-') {
                val level = longBracketLevel(body, i + 2)
                i = if (level >= 0) {
                    skipLongBracket(body, i + 2, level)
                } else {
                    var j = i
                    while (j < n && body[j] != '\n' && body[j] != '\r') j++
                    j
                }
                continue
            }
            // 短字符串：原样保留（含反斜杠转义）
            if (c == '"' || c == '\'') {
                kept.append(c)
                i++
                while (i < n) {
                    val ch = body[i]
                    kept.append(ch)
                    i++
                    if (ch == '\\' && i < n) {
                        kept.append(body[i])
                        i++
                        continue
                    }
                    if (ch == c) break
                }
                continue
            }
            // 长括号字符串：原样保留
            if (c == '[') {
                val level = longBracketLevel(body, i)
                if (level >= 0) {
                    val end = skipLongBracket(body, i, level)
                    kept.append(body, i, end)
                    i = end
                    continue
                }
            }
            kept.append(c)
            i++
        }
        // 空白折叠：任意空白串 → 单空格（同时抹掉开头缩进与尾部空白）
        val out = StringBuilder(kept.length)
        var inSpace = false
        for (ch in kept) {
            if (ch.isWhitespace()) {
                if (!inSpace && out.isNotEmpty()) {
                    out.append(' ')
                    inSpace = true
                }
            } else {
                out.append(ch)
                inSpace = false
            }
        }
        return out.toString().trim()
    }

    /** 判定 [start] 处是否为长括号开头 `[` + N 个 `=` + `[`；命中返回等号个数，否则 -1 */
    private fun longBracketLevel(s: String, start: Int): Int {
        if (start >= s.length || s[start] != '[') return -1
        var j = start + 1
        while (j < s.length && s[j] == '=') j++
        if (j < s.length && s[j] == '[') return j - start - 1
        return -1
    }

    /** 从长括号开头 [start] 跳到闭合处之后；未闭合则到串尾 */
    private fun skipLongBracket(s: String, start: Int, level: Int): Int {
        var i = start + 2 + level
        while (i < s.length) {
            if (s[i] == ']') {
                var j = i + 1
                var eq = 0
                while (j < s.length && s[j] == '=') {
                    j++
                    eq++
                }
                if (eq == level && j < s.length && s[j] == ']') return j + 1
            }
            i++
        }
        return s.length
    }

    /** 标签归一化：trim + 全角转半角 + ASCII 小写（用于匹配/去重，不改变展示原文） */
    fun normalizeTag(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw.trim()) {
            sb.append(
                when {
                    ch in '\uFF01'..'\uFF5E' -> (ch - 0xFEE0).toChar() // 全角 ASCII → 半角
                    ch == '\u3000' -> ' ' // 全角空格
                    else -> ch
                }
            )
        }
        return sb.toString().lowercase()
    }

    /**
     * 从查询串提取 `#标签` 词元：归一化（trim+全角转半角+ASCII 小写）、去空、去重。
     * 供「#标签子串匹配」使用；多个标签之间为 OR 关系。
     */
    fun parseQueryTags(query: String): List<String> =
        query.split(Regex("\\s+"))
            .filter { it.startsWith("#") && it.length > 1 }
            .map { normalizeTag(it.substring(1)) }
            .filter { it.isNotEmpty() }
            .distinct()

    /** 从查询串提取非标签的纯文本部分（词元以空格重组），供文本匹配使用 */
    fun parseQueryText(query: String): String =
        query.split(Regex("\\s+"))
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .joinToString(" ")
            .trim()

    /**
     * 拉取并解析云居托管标签词表（文档 id=2，native 直发；content 为单行紧凑 JSON 数组）。
     * @return 去重后的词表；被门控/网络/解析失败返回 null（调用方保留旧缓存）
     */
    fun fetchTagWordlist(context: Context): List<String>? {
        val raw = YunJuBridge.fetchTagDoc(context)
        if (raw == null) {
            android.util.Log.i("ForumTags", "词表拉取 native 返回 null（门控/网络失败）")
            return null
        }
        val json = extractJson(raw)
        if (json == null) {
            android.util.Log.i("ForumTags", "词表响应无 JSON body，raw前200=${raw.take(200)}")
            return null
        }
        val content = try {
            JsonParser.parseString(json).asJsonObject.get("content")?.asString
        } catch (e: Exception) {
            android.util.Log.i("ForumTags", "词表响应解析失败：$e")
            null
        }
        if (content == null) {
            android.util.Log.i("ForumTags", "词表响应缺少 content 字段：${json.take(300)}")
            return null
        }
        val tags = try {
            val trimmed = content.trim()
            if (!trimmed.startsWith("[")) {
                android.util.Log.i("ForumTags", "词表 content 非 JSON 数组：${trimmed.take(200)}")
                return null
            }
            JsonParser.parseString(trimmed).asJsonArray
                .mapNotNull { it.asString.takeIf { s -> s.isNotBlank() } }
        } catch (e: Exception) {
            android.util.Log.i("ForumTags", "词表 content 解析失败：$e")
            return null
        }
        android.util.Log.i("ForumTags", "词表拉取成功，共 ${tags.size} 项")
        return tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    /** 读取本地缓存的标签词表；无缓存返回空词表 */
    fun loadCachedTagWordlist(context: Context): List<String> {
        val json = StudioMmkv.getString(context, MMKV_ID_FORUM_TAGS, MMKV_KEY_TAG_WORDLIST)
            ?: return emptyList()
        return try {
            JsonParser.parseString(json).asJsonArray
                .mapNotNull { it.asString.takeIf { s -> s.isNotBlank() } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 写入标签词表缓存 */
    fun cacheTagWordlist(context: Context, tags: List<String>) {
        val arr = JsonArray()
        tags.forEach { arr.add(it) }
        StudioMmkv.putString(context, MMKV_ID_FORUM_TAGS, MMKV_KEY_TAG_WORDLIST, arr.toString())
    }

    /**
     * 刷新标签词表：拉取成功→覆盖缓存；失败→用缓存；无缓存→空词表。
     * @return 最终可用词表
     */
    fun refreshTagWordlist(context: Context): List<String> {
        val fetched = fetchTagWordlist(context)
        if (fetched != null) {
            cacheTagWordlist(context, fetched)
            return fetched
        }
        val cached = loadCachedTagWordlist(context)
        android.util.Log.i("ForumTags", "词表拉取失败，使用缓存 ${cached.size} 项")
        return cached
    }

    // ---- 搜索历史（完整查询串，上限 20、去重、可清空）----

    /** 读取搜索历史（最新在前） */
    fun loadSearchHistory(context: Context): List<String> {
        val json = StudioMmkv.getString(context, MMKV_ID_FORUM_SEARCH, MMKV_KEY_SEARCH_HISTORY)
            ?: return emptyList()
        return try {
            JsonParser.parseString(json).asJsonArray
                .mapNotNull { it.asString.takeIf { s -> s.isNotBlank() } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 记录一次搜索（去重后置顶，超上限截断）；返回更新后的历史 */
    fun pushSearchHistory(context: Context, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return loadSearchHistory(context)
        val updated = (listOf(q) + loadSearchHistory(context).filter { it != q })
            .take(SEARCH_HISTORY_LIMIT)
        cacheSearchHistory(context, updated)
        return updated
    }

    /** 清空搜索历史 */
    fun clearSearchHistory(context: Context) {
        StudioMmkv.remove(context, MMKV_ID_FORUM_SEARCH, MMKV_KEY_SEARCH_HISTORY)
    }

    private fun cacheSearchHistory(context: Context, history: List<String>) {
        val arr = JsonArray()
        history.forEach { arr.add(it) }
        StudioMmkv.putString(context, MMKV_ID_FORUM_SEARCH, MMKV_KEY_SEARCH_HISTORY, arr.toString())
    }

    // ---- 付费购买（user_jb.php 扣/加币 + fk_tj.php 审计 + Follow.php 标记已购）----

    /** 拉取用户实时金币；失败返回 null */
    suspend fun fetchCoin(context: Context, user: String): Int? =
        LoginRepository.fetchUserInfo(context, user)?.coin?.toIntOrNull()

    private data class CoinCallResult(val ok: Boolean, val networkFail: Boolean)

    /**
     * 加减金币（value>0 扣、value<0 加）。
     * 网络类失败（native null / 解析失败）重试 1 次；业务失败（code=-1）不重试。
     */
    private fun callCoin(context: Context, user: String, value: Int): CoinCallResult {
        repeat(2) { attempt ->
            val raw = YunJuBridge.nativeAddCoin(context, user, value)
            if (raw != null) {
                val resp = extractJson(raw)?.let {
                    runCatching { gson.fromJson(it, CoinActionResponse::class.java) }.getOrNull()
                }
                if (resp != null) {
                    android.util.Log.i(
                        "ForumPay",
                        "addCoin user=$user value=$value code=${resp.code} msg=${resp.msg}"
                    )
                    return CoinCallResult(ok = resp.code == 1, networkFail = false)
                }
            }
            android.util.Log.i("ForumPay", "addCoin 网络/解析失败 user=$user value=$value attempt=$attempt")
        }
        return CoinCallResult(ok = false, networkFail = true)
    }

    /** 审计日志（fk_tj.php）：失败静默，仅日志 */
    private fun fkSubmit(context: Context, user: String, content: String) {
        val raw = runCatching { YunJuBridge.nativeFkSubmit(context, user, content) }.getOrNull()
        val code = raw?.let { extractJson(it) }?.let {
            runCatching { gson.fromJson(it, CoinActionResponse::class.java).code }.getOrNull()
        }
        android.util.Log.i("ForumPay", "fk_tj user=$user code=$code content=${content.take(200)}")
    }

    /** 审计内容：最简 JSON（完整单词键；coin 为该标注点账号当前金币） */
    private fun fkContent(post: ForumItem, seller: String, buyer: String, cost: Int, coin: Int): String {
        val o = JsonObject()
        o.addProperty("post_id", post.postId)
        o.addProperty("title", post.title)
        o.addProperty("seller", seller)
        o.addProperty("buyer", buyer)
        o.addProperty("cost", cost)
        o.addProperty("coin", coin)
        return o.toString()
    }

    /**
     * 购买付费帖（软付费墙）。
     * 序列：fk_tj(买家币) → 扣买家 → fk_tj(买家币) → fk_tj(卖家币) → 加卖家 → fk_tj(卖家币)（共 4 次）；
     * 扣款成功后 Follow 标记已购（失败落本地 pending 兜底）；
     * 加卖家失败 → 落待补发队列，买家仍视为已购（返回 PayoutPending）。
     */
    suspend fun purchase(context: Context, post: ForumItem, buyerQq: String): PurchaseResult =
        withContext(Dispatchers.IO) {
            val meta = parseMeta(post.content)
            if (!meta.paid) return@withContext PurchaseResult.Success
            val sellerQq = post.qq
            if (buyerQq.isBlank()) return@withContext PurchaseResult.Fail("请先登录")
            if (sellerQq.isBlank()) return@withContext PurchaseResult.Fail("帖子作者信息缺失")
            if (buyerQq == sellerQq) return@withContext PurchaseResult.Success

            val buyerCoin = fetchCoin(context, buyerQq)
                ?: return@withContext PurchaseResult.Fail("无法获取你的余额，请重试")
            val sellerCoin = fetchCoin(context, sellerQq)
                ?: return@withContext PurchaseResult.Fail("无法获取卖家信息，请重试")
            val cost = computeCost(meta, buyerCoin)
            if (cost <= 0) return@withContext PurchaseResult.Fail("价格异常")
            if (cost > buyerCoin) return@withContext PurchaseResult.Fail("金币不足")

            // 1. 标注买家现有金币
            fkSubmit(context, buyerQq, fkContent(post, sellerQq, buyerQq, cost, buyerCoin))
            // 2. 扣买家
            val deduct = callCoin(context, buyerQq, cost)
            if (!deduct.ok) {
                return@withContext PurchaseResult.Fail(
                    if (deduct.networkFail) "网络异常，购买失败" else "扣款失败（余额不足？）"
                )
            }
            // 3. 标注买家现有金币（扣后）
            fkSubmit(context, buyerQq, fkContent(post, sellerQq, buyerQq, cost, buyerCoin - cost))
            // 4. 标注卖家现有金币
            fkSubmit(context, sellerQq, fkContent(post, sellerQq, buyerQq, cost, sellerCoin))
            // 5. 加卖家
            val add = callCoin(context, sellerQq, -cost)
            // 6. 标注卖家现有金币（收后）
            fkSubmit(context, sellerQq, fkContent(post, sellerQq, buyerQq, cost, sellerCoin + cost))

            // 7. 标记已购（Follow.php）；失败落本地 pending 兜底
            if (!follow(context, post.postId, buyerQq)) {
                addPendingPurchased(context, post.postId)
                android.util.Log.i("ForumPay", "Follow 失败，落本地 pending postId=${post.postId}")
            }

            if (!add.ok) {
                addPendingPayout(context, PendingPayout(post.postId, sellerQq, cost))
                return@withContext PurchaseResult.PayoutPending("已购买，卖家到账待补发")
            }
            PurchaseResult.Success
        }

    /** 已购帖子列表（FollowList.php，data 元素与论坛帖子同构）；失败返回空列表 */
    fun loadPurchasedPosts(context: Context, qq: String): List<ForumItem> {
        if (qq.isBlank()) return emptyList()
        val raw = YunJuBridge.nativeFollowList(context, qq)
        if (raw == null) {
            android.util.Log.i("ForumFollow", "FollowList native 返回 null（门控/网络失败）")
            return emptyList()
        }
        // 埋点：完整响应原文，用于真机确认 data 元素结构
        android.util.Log.i("ForumFollow", "FollowList 原文=${raw.trim().take(800)}")
        val json = extractJson(raw) ?: return emptyList()
        val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
            ?: return emptyList()
        if (obj.get("code")?.asInt != 200) return emptyList()
        val arr = obj.getAsJsonArray("data") ?: return emptyList()
        val posts = arr.mapNotNull { el ->
            runCatching {
                when {
                    el.isJsonObject -> gson.fromJson(el, ForumItem::class.java)
                    el.isJsonPrimitive -> el.asString.toLongOrNull()?.let { ForumItem(postId = it) }
                    else -> null
                }
            }.getOrNull()
        }
        android.util.Log.i("ForumFollow", "FollowList 有效项=${posts.size}")
        return posts
    }

    /** 已购 id 集合：服务端 FollowList ∪ 本地 pending 兜底 */
    fun loadPurchasedIds(context: Context, qq: String): Set<Long> =
        (loadPurchasedPosts(context, qq).map { it.postId } + loadPendingPurchased(context)).toSet()

    // ---- 本地待同步 / 待补发队列 ----

    /** 读取已购本地兜底 post_id */
    private fun loadPendingPurchased(context: Context): List<Long> {
        val json = StudioMmkv.getString(context, MMKV_ID_FORUM_PURCHASE, MMKV_KEY_PENDING)
            ?: return emptyList()
        return runCatching {
            JsonParser.parseString(json).asJsonArray.mapNotNull { it.asLong }
        }.getOrDefault(emptyList())
    }

    private fun addPendingPurchased(context: Context, postId: Long) {
        val arr = JsonArray()
        (loadPendingPurchased(context) + postId).distinct().forEach { arr.add(it) }
        StudioMmkv.putString(context, MMKV_ID_FORUM_PURCHASE, MMKV_KEY_PENDING, arr.toString())
    }

    private fun loadPendingPayouts(context: Context): List<PendingPayout> {
        val json = StudioMmkv.getString(context, MMKV_ID_FORUM_PURCHASE, MMKV_KEY_PENDING_PAYOUT)
            ?: return emptyList()
        return runCatching {
            gson.fromJson(json, Array<PendingPayout>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    private fun savePendingPayouts(context: Context, items: List<PendingPayout>) {
        if (items.isEmpty()) {
            StudioMmkv.remove(context, MMKV_ID_FORUM_PURCHASE, MMKV_KEY_PENDING_PAYOUT)
        } else {
            StudioMmkv.putString(
                context, MMKV_ID_FORUM_PURCHASE, MMKV_KEY_PENDING_PAYOUT, gson.toJson(items)
            )
        }
    }

    private fun addPendingPayout(context: Context, item: PendingPayout) {
        savePendingPayouts(context, (loadPendingPayouts(context) + item).distinct())
    }

    /** 启动时重试待补发收款（加卖家金币）；成功即出队 */
    suspend fun retryPendingPayouts(context: Context) = withContext(Dispatchers.IO) {
        val items = loadPendingPayouts(context)
        if (items.isEmpty()) return@withContext
        val remain = ArrayList<PendingPayout>()
        for (item in items) {
            val ok = item.seller.isNotBlank() && callCoin(context, item.seller, -item.cost).ok
            if (!ok) remain.add(item)
        }
        android.util.Log.i("ForumPay", "待补发重试：原 ${items.size} 条，剩余 ${remain.size} 条")
        savePendingPayouts(context, remain)
    }
}
