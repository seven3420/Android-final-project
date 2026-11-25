package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model

/** A 组后端登录请求体。 */
data class LoginRequest(
    val phone: String,
    val password: String
)

/** 登录返回的数据部分。 */
data class LoginResult(
    val token: String?,
    val userInfo: UserInfo? = null
)

data class UserInfo(
    val id: Int = 0,
    val nickname: String = "",
    val avatar: String? = null
)
