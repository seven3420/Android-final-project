package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model

/**
 * 登录请求体，字段名需与 A 组后端保持一致（如后端用 account 等，改这里即可）。
 */
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * 登录返回的数据部分，至少需要 token；其余字段按 A 组实际返回补充。
 */
data class LoginResult(
    val token: String?,
    val userId: Int? = null,
    val username: String? = null,
    val nickname: String? = null
)
