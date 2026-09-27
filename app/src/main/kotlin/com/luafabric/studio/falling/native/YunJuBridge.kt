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
}
