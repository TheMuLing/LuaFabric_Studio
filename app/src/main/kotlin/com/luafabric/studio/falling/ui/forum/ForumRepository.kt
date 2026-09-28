package com.luafabric.studio.falling.ui.forum

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.luafabric.studio.falling.native.YunJuBridge

/** 源码论坛帖子（ForumList data 项） */
data class ForumItem(
    @SerializedName("post_id") val postId: Long = 0L,
    @SerializedName("forum_id") val forumId: Int = 0,
    @SerializedName("forum_name") val forumName: String = "",
    val qq: String = "",
    val nickname: String = "",
    val title: String = "",
    val content: String = "",
    @SerializedName("create_time") val createTime: String = ""
)

/** ForumList 响应体 */
data class ForumListResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: List<ForumItem> = emptyList()
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
}
