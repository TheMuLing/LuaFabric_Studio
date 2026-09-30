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

    /**
     * 在线状态心跳上报（zaixian.php，native 直发 yuju:81）。
     * @param backstage 云居后台账号（恒 YUNJU_ADMIN）；appid 恒 YUNJU_APP_ID；
     *        user 当前登录用户账号（未登录不上报，Kotlin 侧控流）
     * @return 响应体字符串（含 HTTP 头，Kotlin 侧裁剪 JSON）；被门控拦截或网络失败返回 null
     */
    external fun nativeOnlineSubmit(
        context: Context,
        backstage: String,
        appid: String,
        user: String
    ): String?

    /**
     * 云居账号登录（user_dl.php，native 直发，套 VPN/代理门控）。
     * @param user QQ 账号；pass 密码
     * @return 响应体字符串；被门控拦截或网络失败返回 null
     */
    external fun nativeLogin(context: Context, user: String, pass: String): String?

    /** 签到（user_qiandao.php，native 直发，套门控）。@return 同上 */
    external fun nativeSignIn(context: Context, user: String): String?

    /** 拉取用户实时信息（user_yhxx.php，native 直发，套门控）。@return 同上 */
    external fun nativeFetchUserInfo(context: Context, user: String): String?

    /**
     * 注册（user_azc.php，native 直发，套门控）。
     * @param qq 账号；pass 密码；name 昵称；email 邮箱；code 验证码
     */
    external fun nativeRegister(
        context: Context,
        user: String,
        pass: String,
        qq: String,
        name: String,
        email: String,
        code: String
    ): String?

    /** 发送注册验证码（user_yzm.php，native 直发，套门控）。@param email 邮箱 */
    external fun nativeSendCode(context: Context, email: String): String?

    /** 找回密码（user_zhmm.php，native 直发，套门控）。@param email 邮箱 */
    external fun nativeFindPassword(context: Context, email: String): String?

    /**
     * 图片单次直传（FileUpload.php，multipart/form-data，native 直发 yuju:81，套门控）。
     * @param backstage 云居后台账号（恒 YUNJU_ADMIN）；appid 恒 YUNJU_APP_ID
     * @param filePath 本地压缩后图片绝对路径；fileName 原始文件名；fileType MIME（如 image/jpeg）
     * @return 响应体字符串（含 HTTP 头，Kotlin 侧裁剪 JSON）；被门控拦截或网络失败返回 null
     */
    external fun nativeUploadImage(
        context: Context,
        backstage: String,
        appid: String,
        filePath: String,
        fileName: String,
        fileType: String
    ): String?

    /**
     * 发帖（Issue.php，native 直发 yuju:81，套门控）。
     * @param user 恒 YUNJU_ADMIN；qq/nickname 当前登录用户；forumId 板块 ID；
     *        title/content/img 帖子标题/正文（含分类标志前缀）/图片直链
     * @return 响应体字符串；被门控拦截或网络失败返回 null
     */
    external fun nativeIssuePost(
        context: Context,
        user: String,
        qq: String,
        nickname: String,
        forumId: Int,
        title: String,
        content: String,
        img: String
    ): String?
}
