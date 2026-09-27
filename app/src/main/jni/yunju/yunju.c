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
#define YUNJU_HOST      "yunju.99kpk.top"
#define YUNJU_PORT      "443"
#define YUNJU_PATH      "/API/tj_add.php"

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

/* ---- HTTPS POST 上报；返回 1=成功，-1=失败 ---- */
static int yunju_http_post(void) {
    int ret = -1;
    mbedtls_net_context server_fd;
    mbedtls_ssl_context ssl;
    mbedtls_ssl_config conf;

    mbedtls_net_init(&server_fd);
    mbedtls_ssl_init(&ssl);
    mbedtls_ssl_config_init(&conf);

    /* 1. TCP 连接（mbedtls 自带 DNS 解析） */
    ret = mbedtls_net_connect(&server_fd, YUNJU_HOST, YUNJU_PORT, MBEDTLS_NET_PROTO_TCP);
    if (ret != 0) {
        LOGE("net_connect fail: -0x%04X (%s)", (unsigned)-ret, YUNJU_HOST);
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
    ret = mbedtls_ssl_set_hostname(&ssl, YUNJU_HOST);
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

    /* 5. POST 请求 */
    static const char BODY[] = "appid=" YUNJU_APP_ID "&key=" YUNJU_ADMIN_KEY;
    char request[512];
    int req_len = snprintf(request, sizeof(request),
        "POST " YUNJU_PATH " HTTP/1.1\r\n"
        "Host: " YUNJU_HOST "\r\n"
        "Content-Type: application/x-www-form-urlencoded\r\n"
        "Content-Length: %d\r\n"
        "Connection: close\r\n"
        "\r\n"
        "%s", (int)strlen(BODY), BODY);

    ret = mbedtls_ssl_write(&ssl, (const unsigned char *)request, (size_t)req_len);
    if (ret <= 0) {
        LOGE("ssl_write fail: %d", ret);
        goto out;
    }
    LOGI("POST sent (%d bytes)", req_len);

    /* 6. 读响应（Connection: close → 读到 0 或错误结束） */
    char buf[4096];
    size_t total = 0;
    for (;;) {
        ret = mbedtls_ssl_read(&ssl, (unsigned char *)buf + total,
                               sizeof(buf) - 1 - total);
        if (ret > 0) {
            total += (size_t)ret;
            if (total >= sizeof(buf) - 1) break;
        } else if (ret == 0 || ret == MBEDTLS_ERR_SSL_WANT_READ ||
                   ret == MBEDTLS_ERR_SSL_WANT_WRITE) {
            break;
        } else {
            LOGE("ssl_read fail: -0x%04X", (unsigned)-ret);
            break;
        }
    }
    buf[total] = '\0';

    /* 7. 记响应 code/msg 到日志（静默，不上报 UI） */
    const char *code_at = strstr(buf, "\"code\"");
    const char *msg_at = strstr(buf, "\"msg\"");
    if (code_at) {
        const char *v = strchr(code_at, ':');
        LOGI("response code=%s", v ? v + 1 : "?");
    }
    if (msg_at) {
        const char *v = strchr(msg_at, ':');
        LOGI("response msg=%s", v ? v + 1 : "?");
    }
    ret = (total > 0) ? 1 : -1;
    LOGI("HTTP response received (%zu bytes)", total);

out:
    mbedtls_ssl_free(&ssl);
    mbedtls_ssl_config_free(&conf);
    mbedtls_net_free(&server_fd);
    return ret;
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

    return yunju_http_post();
}
