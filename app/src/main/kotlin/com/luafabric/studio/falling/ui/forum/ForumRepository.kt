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
        val raw = YunJuBridge.nativeForumList(context, forumId) ?: return emptyList()
        // 响应为 HTTP 头 + JSON body，取首个 '{' 起的完整 JSON
        val jsonStart = raw.indexOf('{')
        if (jsonStart < 0) return emptyList()
        val resp = try {
            gson.fromJson(raw.substring(jsonStart), ForumListResponse::class.java)
        } catch (e: Exception) {
            null
        }
        return if (resp != null && resp.code == 200) resp.data else emptyList()
    }
}
