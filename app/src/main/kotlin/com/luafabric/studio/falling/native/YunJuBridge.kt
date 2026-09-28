package com.luafabric.studio.falling.native

import android.content.Context

/**
 * 云居 DAU 上报 native 桥。
 *
 * 逻辑全部在 native 层：
 *  - 发送前做 VPN 接口 / WLAN 代理门控检查，命中则跳过本次请求（装作无法连接）；
 *  - 未命中则 HTTPS POST 到 https://yunju.99kpk.top/API/tj_add.php
 *    （appid=2283&key=1790465304，native 硬编码）；
 *  - 任何失败静默，仅 LogCat（tag=YunJu）记录。
 */
object YunJuBridge {

    init {
        System.loadLibrary("yunju")
    }

    /**
     * 上报一次 DAU。
     * @return 1=已上报；-1=被门控拦截或失败
     */
    external fun nativeTjAdd(context: Context): Int

    /**
     * 源码论坛帖子列表（ForumList.php，native 直发）。
     * @return 响应体字符串（含 HTTP 头，Kotlin 侧剥离 JSON 后 Gson 解析）；
     *         被门控拦截或网络失败返回 null
     */
    external fun nativeForumList(context: Context, forumId: Int): String?

    /**
     * 发表评论（Comment.php，native 直发）。
     * @param postId 帖子 ID；user/qq/nickname 取当前登录用户身份；content 评论内容
     * @return 响应体字符串；被门控拦截或网络失败返回 null
     */
    external fun nativeComment(
        context: Context,
        postId: Long,
        user: String,
        qq: String,
        nickname: String,
        content: String
    ): String?

    /**
     * 拉取评论 + 回复列表（CommentList.php，native 直发）。
     * @param postId 帖子 ID；user 为当前登录用户身份
     * @return 响应体字符串；被门控拦截或网络失败返回 null
     */
    external fun nativeCommentList(context: Context, postId: Long, user: String): String?

    /**
     * 点赞/取消点赞（Praise.php，native 直发）。
     * @param postId 帖子 ID；user/qq 为当前登录用户身份
     * @return 响应体字符串；被门控拦截或网络失败返回 null
     */
    external fun nativePraise(context: Context, postId: Long, user: String, qq: String): String?

    /**
     * 收藏/取消收藏（Follow.php，native 直发）。
     * @param postId 帖子 ID；user/qq 为当前登录用户身份
     * @return 响应体字符串；被门控拦截或网络失败返回 null
     */
    external fun nativeFollow(context: Context, postId: Long, user: String, qq: String): String?

    /**
     * 回复评论（CommentReply.php，native 直发）。
     * @param postId 帖子 ID；commentId 被回复的评论 ID；user/qq/nickname 为当前登录用户身份
     * @return 响应体字符串；被门控拦截或网络失败返回 null
     */
    external fun nativeCommentReply(
        context: Context,
        postId: Long,
        commentId: Long,
        user: String,
        qq: String,
        nickname: String,
        content: String
    ): String?
}
