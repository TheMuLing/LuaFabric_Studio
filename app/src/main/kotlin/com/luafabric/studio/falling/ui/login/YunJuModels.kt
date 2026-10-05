package com.luafabric.studio.falling.ui.login

import com.google.gson.annotations.SerializedName

/** 云居后端常量（appid/key 等凭证仅在 native 层 YunJu 硬编码，Kotlin 侧不再持有） */
object YunJuApi {
    const val AVATAR_URL = "https://q1.qlogo.cn/g?b=qq&nk=%s&s=640"

    fun avatarUrl(qq: String): String = AVATAR_URL.format(qq)
}

/**
 * 登录/签到接口统一返回结构。
 * 后端所有字段均为字符串（含 code 字段）。
 * sign/vip 为 "true"/"false" 字符串。
 */
data class YunJuResponse(
    val code: String = "",
    val msg: String = "",
    val name: String = "",
    @SerializedName("QQ") val qq: String = "",
    val email: String = "",
    val coin: String = "",
    val autograph: String = "",
    val sign: String = "",
    val time: String = "",
    val vip: String = "",
    val viptime: String = "",
    val exp: String = "",
    val level: String = ""
)

/** 登录结果封装 */
data class LoginResult(
    val success: Boolean,
    val user: YunJuResponse?,
    val message: String,
    /** 后端原始 code；响应缺失/畸形时为 null */
    val code: String? = null
)
