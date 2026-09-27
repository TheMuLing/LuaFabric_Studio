package com.luafabric.studio.falling.ui.login

import com.google.gson.annotations.SerializedName

/** 云居后端常量（appid/key 与 native 层 YunJu 硬编码一致） */
object YunJuApi {
    const val APP_ID = "2283"
    const val APP_KEY = "1790465304"
    const val LOGIN_URL = "https://yunju.99kpk.top/API/user_dl.php"
    const val SIGN_URL = "https://yunju.99kpk.top/API/user_qiandao.php"
    const val REGISTER_URL = "https://yunju.99kpk.top/API/user_azc.php"
    const val SEND_CODE_URL = "https://yunju.99kpk.top/API/user_yzm.php"
    const val FIND_PASS_URL = "https://yunju.99kpk.top/API/user_zhmm.php"
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
    val message: String
)
