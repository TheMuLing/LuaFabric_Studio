/*
 * yunju.c — 云居 DAU 上报专用 native 客户端
 *
 * 流程（每次进入项目列表触发一次）：
 *   1. 门控检查：VPN 接口（54 模式）命中 或 WLAN HTTP 代理存在 → 跳过本次请求，
 *      装作无法连接到服务器（返回 -1）。
 *   2. 未命中 → mbedTLS 原生 HTTPS POST 到 https://yunju.99kpk.top/API/tj_add.php
 *      参数 appid=2283&key=1790465304（native 硬编码）。
 *   3. 任何失败静默：仅 LogCat(tag=YunJu) 记录，不上报 UI。
 *
 * 证书校验：跳过（authmode VERIFY_NONE）。
 * RNG：自定义 rng_urandom 直接读 /dev/urandom，绕开 entropy/ctr_drbg 熵源问题。
 */
#include <jni.h>
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

#include "mbedtls/net_sockets.h"
#include "mbedtls/ssl.h"

#define LOG_TAG "YunJu"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* ---- 云居硬编码参数 ---- */
#define YUNJU_APP_ID    "2283"
#define YUNJU_ADMIN_KEY "1790465304"
#define YUNJU_ADMIN     "3445352175"
#define YUNJU_FORUM_DEFAULT "1"
#define YUNJU_HOST      "yunju.99kpk.top"
#define YUNJU_PORT      "443"
#define YUNJU_PATH      "/API/tj_add.php"

/* 源码论坛 ForumList 接口（yuju 域名 + 81 端口，区别于 DAU 上报的 yunju） */
#define FORUM_HOST      "yuju.99kpk.top"
#define FORUM_PORT      "81"
#define FORUM_PATH      "/lt/ForumList.php"

/* 源码论坛互动接口（评论/回复/点赞/收藏，同 yuju 域名 + 81 端口） */
#define FORUM_COMMENT_PATH      "/lt/Comment.php"
#define FORUM_COMMENT_LIST_PATH "/lt/CommentList.php"
#define FORUM_PRAISE_PATH       "/lt/Praise.php"
#define FORUM_FOLLOW_PATH       "/lt/Follow.php"
#define FORUM_REPLY_PATH        "/lt/CommentReply.php"

/* ---- VPN 接口模式表（源自 Lua VPN_PATTERNS，54 项） ---- */
static const char * const VPN_PATTERNS[] = {
    "tun", "ppp", "pptp", "l2tp", "ipsec", "wg", "utun", "tap", "gre", "ipip",
    "sit", "pppoe", "ovpn", "vpn", "vtun", "n2n", "zerotier", "tailscale",
    "openvpn", "wireguard", "strongswan", "racoon", "openswan", "libreswan",
    "softether", "anyconnect", "fortissl", "pulse", "globalprotect", "checkpoint",
    "juniper", "f5", "citrix", "sstp", "ike", "l2tpipsec", "pptpvpn", "openconnect",
    "vpnclient", "vpngate", "protonvpn", "nordvpn", "expressvpn", "surfshark",
    "vyprvpn", "hidemyass", "privatevpn", "windscribe", "tunnelbear", "hotspotshield",
    "ipvanish", "purevpn", "cyberghost", "zenmate"
};
#define VPN_PATTERNS_COUNT (sizeof(VPN_PATTERNS) / sizeof(VPN_PATTERNS[0]))

/* 大小写不敏感子串匹配（对应 Lua: string.lower(iface) 后 plain find） */
static int match_vpn_pattern(const char *iface) {
    size_t n = strlen(iface);
    for (size_t p = 0; p < VPN_PATTERNS_COUNT; p++) {
        const char *pat = VPN_PATTERNS[p];
        size_t m = strlen(pat);
        if (m > n) continue;
        for (size_t i = 0; i + m <= n; i++) {
            size_t j = 0;
            while (j < m && tolower((unsigned char)iface[i + j]) == pat[j]) j++;
            if (j == m) return 1;
        }
    }
    return 0;
}

/* ---- JNI 辅助 ---- */
static jboolean clear_exception(JNIEnv *env) {
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

/*
 * 获取当前活跃网络的 LinkProperties。
 * 对应 Lua: cm.getLinkProperties(cm.getActiveNetwork())
 */
static jobject get_link_properties(JNIEnv *env, jobject context) {
    jclass ctx_cls = (*env)->FindClass(env, "android/content/Context");
    if (!ctx_cls) { clear_exception(env); return NULL; }

    jfieldID fid_service = (*env)->GetStaticFieldID(
        env, ctx_cls, "CONNECTIVITY_SERVICE", "Ljava/lang/String;");
    jmethodID mid_getSystemService = (*env)->GetMethodID(
        env, ctx_cls, "getSystemService", "(Ljava/lang/String;)Ljava/lang/Object;");
    if (!fid_service || !mid_getSystemService) { clear_exception(env); return NULL; }

    jstring svc = (*env)->GetStaticObjectField(env, ctx_cls, fid_service);
    jobject cm = (*env)->CallObjectMethod(env, context, mid_getSystemService, svc);
    if (clear_exception(env) || !cm) return NULL;

    jclass cm_cls = (*env)->GetObjectClass(env, cm);
    jmethodID mid_getActiveNetwork = (*env)->GetMethodID(
        env, cm_cls, "getActiveNetwork", "()Landroid/net/Network;");
    jmethodID mid_getLinkProperties = (*env)->GetMethodID(
        env, cm_cls, "getLinkProperties",
        "(Landroid/net/Network;)Landroid/net/LinkProperties;");
    if (!mid_getActiveNetwork || !mid_getLinkProperties) { clear_exception(env); return NULL; }

    jobject network = (*env)->CallObjectMethod(env, cm, mid_getActiveNetwork);
    if (clear_exception(env) || !network) return NULL;

    jobject link = (*env)->CallObjectMethod(env, cm, mid_getLinkProperties, network);
    if (clear_exception(env)) return NULL;
    return link;
}

/*
 * VPN 检测（对应 Lua isVpnGrab）。
 * 逻辑：遍历路由，取第一个 default route；其接口存在时，
 *   匹配 VPN 模式 → 返回 1（命中）；不匹配 → 返回 0。无 default route → 0。
 */
static int check_vpn(JNIEnv *env, jobject context) {
    jobject link = get_link_properties(env, context);
    if (!link) return 0;

    jclass link_cls = (*env)->GetObjectClass(env, link);
    jmethodID mid_getRoutes = (*env)->GetMethodID(
        env, link_cls, "getRoutes", "()Ljava/util/List;");
    if (!mid_getRoutes) { clear_exception(env); return 0; }

    jobject routes = (*env)->CallObjectMethod(env, link, mid_getRoutes);
    if (clear_exception(env) || !routes) return 0;

    jclass list_cls = (*env)->FindClass(env, "java/util/List");
    jmethodID mid_size = (*env)->GetMethodID(env, list_cls, "size", "()I");
    jmethodID mid_get = (*env)->GetMethodID(
        env, list_cls, "get", "(I)Ljava/lang/Object;");
    if (!mid_size || !mid_get) { clear_exception(env); return 0; }

    jint n = (*env)->CallIntMethod(env, routes, mid_size);
    if (clear_exception(env)) return 0;

    for (jint i = 0; i < n; i++) {
        jobject route = (*env)->CallObjectMethod(env, routes, mid_get, i);
        if (clear_exception(env) || !route) return 0;

        jclass route_cls = (*env)->GetObjectClass(env, route);
        jmethodID mid_isDefault = (*env)->GetMethodID(
            env, route_cls, "isDefaultRoute", "()Z");
        jmethodID mid_getInterface = (*env)->GetMethodID(
            env, route_cls, "getInterface", "()Ljava/lang/String;");
        if (!mid_isDefault || !mid_getInterface) { clear_exception(env); return 0; }

        jboolean isDefault = (*env)->CallBooleanMethod(env, route, mid_isDefault);
        if (clear_exception(env)) return 0;
        if (!isDefault) continue;

        jstring iface = (*env)->CallObjectMethod(env, route, mid_getInterface);
        if (clear_exception(env)) return 0;
        if (iface != NULL) {
            const char *c_iface = (*env)->GetStringUTFChars(env, iface, NULL);
            if (c_iface) {
                int matched = match_vpn_pattern(c_iface);
                (*env)->ReleaseStringUTFChars(env, iface, c_iface);
                return matched; /* 命中→1；未命中→0（对应 Lua return false） */
            }
        }
        return 0;
    }
    return 0;
}

/*
 * WLAN 代理检测（对应 Lua isWlanGrab）。
 * LinkProperties.getHttpProxy() != null → 命中。
 */
static int check_wlan(JNIEnv *env, jobject context) {
    jobject link = get_link_properties(env, context);
    if (!link) return 0;

    jclass link_cls = (*env)->GetObjectClass(env, link);
    jmethodID mid_getHttpProxy = (*env)->GetMethodID(
        env, link_cls, "getHttpProxy", "()Landroid/net/ProxyInfo;");
    if (!mid_getHttpProxy) { clear_exception(env); return 0; }

    jobject proxy = (*env)->CallObjectMethod(env, link, mid_getHttpProxy);
    if (clear_exception(env)) return 0;
    return proxy != NULL ? 1 : 0;
}

/* ---- 自定义 RNG：读 /dev/urandom，绕开 entropy 无熵源问题 ---- */
static int rng_urandom(void *p_rng, unsigned char *output, size_t output_len) {
    (void)p_rng;
    FILE *f = fopen("/dev/urandom", "rb");
    if (!f) return -1;
    size_t got = fread(output, 1, output_len, f);
    fclose(f);
    return (got == output_len) ? 0 : -1;
}

/*
 * ---- HTTPS POST（泛化）----
 * 参数：host/port/path/body 由调用方传入（tj_add 与 ForumList 共用）。
 * 返回：成功 → malloc 的完整响应体（含 HTTP 头，调用方 free）；
 *       失败 → NULL。
 */
static char *yunju_http_post(const char *host, const char *port, const char *path,
                             const char *body) {
    int ret = -1;
    char *resp = NULL;
    size_t total = 0;
    mbedtls_net_context server_fd;
    mbedtls_ssl_context ssl;
    mbedtls_ssl_config conf;

    mbedtls_net_init(&server_fd);
    mbedtls_ssl_init(&ssl);
    mbedtls_ssl_config_init(&conf);

    /* 1. TCP 连接（mbedtls 自带 DNS 解析） */
    ret = mbedtls_net_connect(&server_fd, host, port, MBEDTLS_NET_PROTO_TCP);
    if (ret != 0) {
        LOGE("net_connect fail: -0x%04X (%s:%s)", (unsigned)-ret, host, port);
        goto out;
    }

    /* 2. SSL 配置：客户端、流传输、跳过证书校验 */
    ret = mbedtls_ssl_config_defaults(&conf, MBEDTLS_SSL_IS_CLIENT,
                                     MBEDTLS_SSL_TRANSPORT_STREAM,
                                     MBEDTLS_SSL_PRESET_DEFAULT);
    if (ret != 0) {
        LOGE("config_defaults fail: -0x%04X", (unsigned)-ret);
        goto out;
    }
    mbedtls_ssl_conf_authmode(&conf, MBEDTLS_SSL_VERIFY_NONE);
    mbedtls_ssl_conf_rng(&conf, rng_urandom, NULL);
    /* 读超时 10s（配合 BIO 用 mbedtls_net_recv_timeout） */
    mbedtls_ssl_conf_read_timeout(&conf, 10000);

    /* 3. SSL 上下文 + SNI + BIO（recv 用带超时版） */
    ret = mbedtls_ssl_setup(&ssl, &conf);
    if (ret != 0) {
        LOGE("ssl_setup fail: -0x%04X", (unsigned)-ret);
        goto out;
    }
    ret = mbedtls_ssl_set_hostname(&ssl, host);
    if (ret != 0) {
        LOGE("set_hostname fail: -0x%04X", (unsigned)-ret);
        goto out;
    }
    mbedtls_ssl_set_bio(&ssl, &server_fd, mbedtls_net_send, NULL, mbedtls_net_recv_timeout);

    /* 4. 握手 */
    ret = mbedtls_ssl_handshake(&ssl);
    if (ret != 0) {
        LOGE("handshake fail: -0x%04X", (unsigned)-ret);
        goto out;
    }

    /* 5. POST 请求（Host 恒带端口） */
    char request[512];
    int req_len = snprintf(request, sizeof(request),
        "POST %s HTTP/1.1\r\n"
        "Host: %s:%s\r\n"
        "Content-Type: application/x-www-form-urlencoded\r\n"
        "Content-Length: %d\r\n"
        "Connection: close\r\n"
        "\r\n"
        "%s", path, host, port, (int)strlen(body), body);

    ret = mbedtls_ssl_write(&ssl, (const unsigned char *)request, (size_t)req_len);
    if (ret <= 0) {
        LOGE("ssl_write fail: %d", ret);
        goto out;
    }
    LOGI("POST sent (%d bytes) -> %s:%s%s", req_len, host, port, path);

    /* 6. 读响应（Connection: close → 读到 0 或错误结束；缓冲动态扩容防截断） */
    size_t cap = 4096;
    resp = (char *)malloc(cap);
    if (!resp) goto out;
    for (;;) {
        if (total >= cap - 1) {
            size_t ncap = cap * 2;
            char *nbuf = (char *)realloc(resp, ncap);
            if (!nbuf) break;
            resp = nbuf;
            cap = ncap;
        }
        ret = mbedtls_ssl_read(&ssl, (unsigned char *)resp + total, cap - 1 - total);
        if (ret > 0) {
            total += (size_t)ret;
        } else if (ret == 0 || ret == MBEDTLS_ERR_SSL_WANT_READ ||
                   ret == MBEDTLS_ERR_SSL_WANT_WRITE) {
            break;
        } else {
            LOGE("ssl_read fail: -0x%04X", (unsigned)-ret);
            break;
        }
    }
    resp[total] = '\0';

    /* 8. 裁剪 HTTP 头，返回以 '{' 开头的 body：
     *    响应头若含 '{'（如 Set-Cookie 等）会误导 Kotlin 侧 indexOf('{')，
     *    故先从 '\r\n\r\n' 定位，再 strchr 找真正的 JSON 起点 */
    char *body_at = strstr(resp, "\r\n\r\n");
    char *src = (body_at != NULL) ? (body_at + 4) : resp;
    char *json_start = strchr(src, '{');
    if (json_start != NULL) {
        memmove(resp, json_start, strlen(json_start) + 1);
    }

    /* 7. 记响应 code/msg 到日志（静默，不上报 UI） */
    if (resp != NULL) {
        const char *code_at = strstr(resp, "\"code\"");
        const char *msg_at = strstr(resp, "\"msg\"");
        if (code_at) {
            const char *v = strchr(code_at, ':');
            LOGI("response code=%s", v ? v + 1 : "?");
        }
        if (msg_at) {
            const char *v = strchr(msg_at, ':');
            LOGI("response msg=%s", v ? v + 1 : "?");
        }
    }
    if (total == 0) {
        free(resp);
        resp = NULL;
    }
    LOGI("HTTP response received (%zu bytes)", total);

out:
    mbedtls_ssl_free(&ssl);
    mbedtls_ssl_config_free(&conf);
    mbedtls_net_free(&server_fd);
    return resp;
}

/*
 * JNI 入口：POST tj_add（DAU 上报）。
 * 返回 1=已上报；-1=被门控拦截或失败。
 */
JNIEXPORT jint JNICALL
Java_com_luafabric_studio_falling_native_YunJuBridge_nativeTjAdd(JNIEnv *env,
                                                                 jclass clazz,
                                                                 jobject context) {
    (void)clazz;

    /* 门控：VPN 或 WLAN 代理命中 → 不发本次请求，装作无法连接 */
    if (check_vpn(env, context)) {
        LOGI("gated: VPN interface detected, request skipped (simulate no network)");
        clear_exception(env);
        return -1;
    }
    if (check_wlan(env, context)) {
        LOGI("gated: WLAN proxy detected, request skipped (simulate no network)");
        clear_exception(env);
        return -1;
    }

    static const char BODY[] = "appid=" YUNJU_APP_ID "&key=" YUNJU_ADMIN_KEY;
    char *resp = yunju_http_post(YUNJU_HOST, YUNJU_PORT, YUNJU_PATH, BODY);
    if (resp == NULL) return -1;
    free(resp);
    return 1;
}

/*
 * JNI 入口：POST ForumList（源码论坛帖子列表）。
 * 返回响应体字符串（含 HTTP 头，Kotlin 侧剥离 JSON 后 Gson 解析）；
 * 被门控或失败返回 NULL。
 */
JNIEXPORT jstring JNICALL
Java_com_luafabric_studio_falling_native_YunJuBridge_nativeForumList(JNIEnv *env,
                                                                     jclass clazz,
                                                                     jobject context,
                                                                     jint forumId) {
    (void)clazz;

    /* 门控：VPN 或 WLAN 代理命中 → 不发本次请求，Kotlin 侧按加载失败处理 */
    if (check_vpn(env, context)) {
        LOGI("gated: VPN interface detected, forum request skipped");
        clear_exception(env);
        return NULL;
    }
    if (check_wlan(env, context)) {
        LOGI("gated: WLAN proxy detected, forum request skipped");
        clear_exception(env);
        return NULL;
    }

    char body[128];
    snprintf(body, sizeof(body),
             "user=" YUNJU_ADMIN "&forum_id=%d", (int)forumId);

    char *resp = yunju_http_post(FORUM_HOST, FORUM_PORT, FORUM_PATH, body);
    if (resp == NULL) return NULL;
    jstring js = (*env)->NewStringUTF(env, resp);
    free(resp);
    if (clear_exception(env)) return NULL;
    return js;
}

/*
 * 表单 URL 编码（RFC 3986：非保留字符 → %XX，空格 → +）。
 * 返回 malloc 字符串，调用方 free；失败返回 NULL。
 */
static char *url_encode(const char *s) {
    if (!s) return NULL;
    size_t len = strlen(s);
    /* 最坏情况：全部字符各占 3 字节 */
    char *out = (char *)malloc(len * 3 + 1);
    if (!out) return NULL;
    size_t o = 0;
    static const char hex[] = "0123456789ABCDEF";
    for (size_t i = 0; i < len; i++) {
        unsigned char c = (unsigned char)s[i];
        if (c == ' ') {
            out[o++] = '+';
        } else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
                   (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') {
            out[o++] = (char)c;
        } else {
            out[o++] = '%';
            out[o++] = hex[c >> 4];
            out[o++] = hex[c & 0x0F];
        }
    }
    out[o] = '\0';
    return out;
}

/*
 * 论坛互动请求公共入口：门控 + POST + 裁剪纯 JSON body。
 * path 由调用方给定，body 为已拼好（含 urlencode 值）的表单串。
 */
static jstring forum_interact(JNIEnv *env, jobject context, const char *path,
                              const char *body) {
    if (check_vpn(env, context)) {
        LOGI("gated: VPN interface detected, forum request skipped");
        clear_exception(env);
        return NULL;
    }
    if (check_wlan(env, context)) {
        LOGI("gated: WLAN proxy detected, forum request skipped");
        clear_exception(env);
        return NULL;
    }
    char *resp = yunju_http_post(FORUM_HOST, FORUM_PORT, path, body);
    if (resp == NULL) return NULL;
    jstring js = (*env)->NewStringUTF(env, resp);
    free(resp);
    if (clear_exception(env)) return NULL;
    return js;
}

/*
 * JNI 入口：POST Comment.php（发表评论）
 * 参数 user/qq/nickname 由 Kotlin 侧取当前登录用户身份传入（user 恒为管理员）。
 */
JNIEXPORT jstring JNICALL
Java_com_luafabric_studio_falling_native_YunJuBridge_nativeComment(JNIEnv *env,
                                                                   jclass clazz,
                                                                   jobject context,
                                                                   jlong postId,
                                                                   jstring user,
                                                                   jstring qq,
                                                                   jstring nickname,
                                                                   jstring content) {
    (void)clazz;
    const char *c_user = (*env)->GetStringUTFChars(env, user, NULL);
    const char *c_qq = (*env)->GetStringUTFChars(env, qq, NULL);
    const char *c_nick = (*env)->GetStringUTFChars(env, nickname, NULL);
    const char *c_content = (*env)->GetStringUTFChars(env, content, NULL);
    if (!c_user || !c_qq || !c_nick || !c_content) {
        if (c_user) (*env)->ReleaseStringUTFChars(env, user, c_user);
        if (c_qq) (*env)->ReleaseStringUTFChars(env, qq, c_qq);
        if (c_nick) (*env)->ReleaseStringUTFChars(env, nickname, c_nick);
        if (c_content) (*env)->ReleaseStringUTFChars(env, content, c_content);
        return NULL;
    }
    char *e_qq = url_encode(c_qq);
    char *e_nick = url_encode(c_nick);
    char *e_content = url_encode(c_content);
    jstring js = NULL;
    if (e_qq && e_nick && e_content) {
        char body[1024];
        snprintf(body, sizeof(body),
                 "user=%s&post_id=%lld&qq=%s&nickname=%s&content=%s",
                 c_user, (long long)postId, e_qq, e_nick, e_content);
        js = forum_interact(env, context, FORUM_COMMENT_PATH, body);
    }
    if (e_qq) free(e_qq);
    if (e_nick) free(e_nick);
    if (e_content) free(e_content);
    (*env)->ReleaseStringUTFChars(env, user, c_user);
    (*env)->ReleaseStringUTFChars(env, qq, c_qq);
    (*env)->ReleaseStringUTFChars(env, nickname, c_nick);
    (*env)->ReleaseStringUTFChars(env, content, c_content);
    return js;
}

/*
 * JNI 入口：POST CommentList.php（拉取评论 + 回复列表）
 */
JNIEXPORT jstring JNICALL
Java_com_luafabric_studio_falling_native_YunJuBridge_nativeCommentList(JNIEnv *env,
                                                                       jclass clazz,
                                                                       jobject context,
                                                                       jlong postId,
                                                                       jstring user) {
    (void)clazz;
    const char *c_user = (*env)->GetStringUTFChars(env, user, NULL);
    if (!c_user) return NULL;
    char body[256];
    snprintf(body, sizeof(body), "user=%s&post_id=%lld", c_user, (long long)postId);
    jstring js = forum_interact(env, context, FORUM_COMMENT_LIST_PATH, body);
    (*env)->ReleaseStringUTFChars(env, user, c_user);
    return js;
}

/*
 * JNI 入口：POST Praise.php（点赞/取消，qq 为操作者）
 */
JNIEXPORT jstring JNICALL
Java_com_luafabric_studio_falling_native_YunJuBridge_nativePraise(JNIEnv *env,
                                                                  jclass clazz,
                                                                  jobject context,
                                                                  jlong postId,
                                                                  jstring user,
                                                                  jstring qq) {
    (void)clazz;
    const char *c_user = (*env)->GetStringUTFChars(env, user, NULL);
    const char *c_qq = (*env)->GetStringUTFChars(env, qq, NULL);
    if (!c_user || !c_qq) {
        if (c_user) (*env)->ReleaseStringUTFChars(env, user, c_user);
        if (c_qq) (*env)->ReleaseStringUTFChars(env, qq, c_qq);
        return NULL;
    }
    char *e_qq = url_encode(c_qq);
    jstring js = NULL;
    if (e_qq) {
        char body[256];
        snprintf(body, sizeof(body), "user=%s&post_id=%lld&qq=%s",
                 c_user, (long long)postId, e_qq);
        js = forum_interact(env, context, FORUM_PRAISE_PATH, body);
        free(e_qq);
    }
    (*env)->ReleaseStringUTFChars(env, user, c_user);
    (*env)->ReleaseStringUTFChars(env, qq, c_qq);
    return js;
}

/*
 * JNI 入口：POST Follow.php（收藏/取消收藏，qq 为操作者）
 */
JNIEXPORT jstring JNICALL
Java_com_luafabric_studio_falling_native_YunJuBridge_nativeFollow(JNIEnv *env,
                                                                  jclass clazz,
                                                                  jobject context,
                                                                  jlong postId,
                                                                  jstring user,
                                                                  jstring qq) {
    (void)clazz;
    const char *c_user = (*env)->GetStringUTFChars(env, user, NULL);
    const char *c_qq = (*env)->GetStringUTFChars(env, qq, NULL);
    if (!c_user || !c_qq) {
        if (c_user) (*env)->ReleaseStringUTFChars(env, user, c_user);
        if (c_qq) (*env)->ReleaseStringUTFChars(env, qq, c_qq);
        return NULL;
    }
    char *e_qq = url_encode(c_qq);
    jstring js = NULL;
    if (e_qq) {
        char body[256];
        snprintf(body, sizeof(body), "user=%s&post_id=%lld&qq=%s",
                 c_user, (long long)postId, e_qq);
        js = forum_interact(env, context, FORUM_FOLLOW_PATH, body);
        free(e_qq);
    }
    (*env)->ReleaseStringUTFChars(env, user, c_user);
    (*env)->ReleaseStringUTFChars(env, qq, c_qq);
    return js;
}

/*
 * JNI 入口：POST CommentReply.php（回复某条评论）
 */
JNIEXPORT jstring JNICALL
Java_com_luafabric_studio_falling_native_YunJuBridge_nativeCommentReply(JNIEnv *env,
                                                                        jclass clazz,
                                                                        jobject context,
                                                                        jlong postId,
                                                                        jlong commentId,
                                                                        jstring user,
                                                                        jstring qq,
                                                                        jstring nickname,
                                                                        jstring content) {
    (void)clazz;
    const char *c_user = (*env)->GetStringUTFChars(env, user, NULL);
    const char *c_qq = (*env)->GetStringUTFChars(env, qq, NULL);
    const char *c_nick = (*env)->GetStringUTFChars(env, nickname, NULL);
    const char *c_content = (*env)->GetStringUTFChars(env, content, NULL);
    if (!c_user || !c_qq || !c_nick || !c_content) {
        if (c_user) (*env)->ReleaseStringUTFChars(env, user, c_user);
        if (c_qq) (*env)->ReleaseStringUTFChars(env, qq, c_qq);
        if (c_nick) (*env)->ReleaseStringUTFChars(env, nickname, c_nick);
        if (c_content) (*env)->ReleaseStringUTFChars(env, content, c_content);
        return NULL;
    }
    char *e_qq = url_encode(c_qq);
    char *e_nick = url_encode(c_nick);
    char *e_content = url_encode(c_content);
    jstring js = NULL;
    if (e_qq && e_nick && e_content) {
        char body[1024];
        snprintf(body, sizeof(body),
                 "user=%s&post_id=%lld&comment_id=%lld&qq=%s&nickname=%s&content=%s",
                 c_user, (long long)postId, (long long)commentId,
                 e_qq, e_nick, e_content);
        js = forum_interact(env, context, FORUM_REPLY_PATH, body);
    }
    if (e_qq) free(e_qq);
    if (e_nick) free(e_nick);
    if (e_content) free(e_content);
    (*env)->ReleaseStringUTFChars(env, user, c_user);
    (*env)->ReleaseStringUTFChars(env, qq, c_qq);
    (*env)->ReleaseStringUTFChars(env, nickname, c_nick);
    (*env)->ReleaseStringUTFChars(env, content, c_content);
    return js;
}
