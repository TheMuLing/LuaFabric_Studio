package com.luafabric.studio.falling.ui.forum

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.luafabric.studio.falling.native.YunJuBridge
import com.luafabric.studio.falling.ui.login.YunJuApi
import java.text.SimpleDateFormat
import java.util.Locale

/** 发帖身份兜底：未登录时以管理员账号参与互动（与 ForumScreen 一致） */
private const val FORUM_ADMIN = "3445352175"

/** 发帖分类隐藏标志 JSON 键：正文首行单行 JSON（显示端剥离，用户不可见） */
private const val FORUM_MARKER_KEY = "forum_category"

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

    /** 发表评论（qq/nickname 为当前登录用户；未登录由调用方兜底管理员） */
    fun comment(
        context: Context,
        postId: Long,
        user: String,
        qq: String,
        nickname: String,
        content: String
    ): Boolean {
        val raw = YunJuBridge.nativeComment(context, postId, user, qq, nickname, content)
        val code = actionCode(raw)
        android.util.Log.i("ForumComment", "postId=$postId user=$user content=$content code=$code")
        return code == 200
    }

    /**
     * 拉取评论 + 回复列表（CommentList）。
     * @return 裁剪后的纯 JSON body 原文；null=失败。
     * 后续真机确认 data 字段名后再按 Gson 模型解析。
     */
    fun loadComments(context: Context, postId: Long, user: String): String? {
        val raw = YunJuBridge.nativeCommentList(context, postId, user)
        if (raw == null) {
            android.util.Log.i("ForumComment", "postId=$postId 评论列表 native 返回 null（门控/网络失败）")
            return null
        }
        // 埋点：完整响应原文，用于真机确认 CommentList data 字段名
        android.util.Log.i("ForumComment", "postId=$postId 评论原文=${raw.trim().take(500)}")
        return extractJson(raw)
    }

    /** 点赞/取消（qq 为当前登录用户；未登录由调用方兜底管理员） */
    fun praise(context: Context, postId: Long, user: String, qq: String): Boolean {
        val raw = YunJuBridge.nativePraise(context, postId, user, qq)
        val code = actionCode(raw)
        android.util.Log.i("ForumLike", "postId=$postId user=$user code=$code")
        return code == 200
    }

    /** 收藏/取消（qq 为当前登录用户；未登录由调用方兜底管理员） */
    fun follow(context: Context, postId: Long, user: String, qq: String): Boolean {
        val raw = YunJuBridge.nativeFollow(context, postId, user, qq)
        val code = actionCode(raw)
        android.util.Log.i("ForumFollow", "postId=$postId user=$user code=$code")
        return code == 200
    }

    /** 回复评论（qq/nickname 为当前登录用户；未登录由调用方兜底管理员） */
    fun commentReply(
        context: Context,
        postId: Long,
        commentId: Long,
        user: String,
        qq: String,
        nickname: String,
        content: String
    ): Boolean {
        val raw = YunJuBridge.nativeCommentReply(
            context, postId, commentId, user, qq, nickname, content
        )
        val code = actionCode(raw)
        android.util.Log.i("ForumComment", "postId=$postId commentId=$commentId code=$code")
        return code == 200
    }

    /** 后端发帖时间格式：yy-MM-dd HH:mm（如 26-09-28 18:45） */
    private val FORUM_TIME_FORMAT = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())

    /**
     * 相对发帖时间文案：刚刚 / N分钟前 / N小时前 / N天前（超 7 天回退绝对时间原样）。
     * 解析失败或格式异常时原样返回 createTime，绝不因格式化问题吞掉时间。
     */
    fun formatRelativeTime(createTime: String): String {
        val parsed = runCatching { FORUM_TIME_FORMAT.parse(createTime) }.getOrNull() ?: return createTime
        val diff = System.currentTimeMillis() - parsed.time
        return when {
            diff < 60_000L -> "刚刚"
            diff < 3_600_000L -> "${diff / 60_000L}分钟前"
            diff < 86_400_000L -> "${diff / 3_600_000L}小时前"
            diff < 7 * 86_400_000L -> "${diff / 86_400_000L}天前"
            else -> createTime
        }
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
        val raw = YunJuBridge.nativeUploadImage(
            context, FORUM_ADMIN, YunJuApi.APP_ID, filePath, fileName, fileType
        )
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
     * @param content 需已拼接分类隐藏标志前缀（withCategoryMarker）
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
            context, FORUM_ADMIN, qq, nickname, forumId, title, content, img
        )
        val code = actionCode(raw)
        android.util.Log.i("ForumIssue", "forumId=$forumId title=$title code=$code")
        return code == 200
    }

    /** 构建分类隐藏标志：`{"forum_category":"分类名"}`（单行 JSON，显示端剥离，用户不可见） */
    fun buildCategoryMarker(category: String): String =
        gson.toJson(mapOf(FORUM_MARKER_KEY to category))

    /** 发帖提交前：标志行 + 换行 + 正文（"其他"类同样拼接，列表过滤对其做兜底归类） */
    fun withCategoryMarker(content: String, category: String): String {
        val marker = buildCategoryMarker(category)
        return if (content.isEmpty()) marker else "$marker\n$content"
    }

    /**
     * 剥离正文首行分类隐藏标志（列表卡片/详情渲染时调用）。
     * 首行恰为 {"forum_category":...} JSON 对象时整行剥除，其余情况（旧帖无标志/用户正文恰好以
     * 单行 JSON 开头）原样返回，不误伤内容。
     */
    fun stripCategoryMarker(content: String): String {
        if (!content.startsWith("{")) return content
        val nl = content.indexOf('\n')
        if (nl < 0) return content
        return try {
            val obj = JsonParser.parseString(content.substring(0, nl)).asJsonObject
            if (obj.has(FORUM_MARKER_KEY)) content.substring(nl + 1) else content
        } catch (e: Exception) {
            content
        }
    }
}
