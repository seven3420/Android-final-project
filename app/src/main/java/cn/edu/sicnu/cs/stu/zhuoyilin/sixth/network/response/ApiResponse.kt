package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.response

/**
 * 后端统一响应体，与 A 组约定：{ "code": 200, "msg": "success", "data": {} }
 */
data class ApiResponse<T>(
    val code: Int,
    val msg: String,
    val data: T?
)
