package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception

object ErrorCode {
    const val SUCCESS = 200

    /** token 失效，B-2 统一跳登录页 */
    const val TOKEN_INVALID = 401

    /** 座位冲突，由 A 组定义 */
    const val SEAT_CONFLICT = 2001

    /** 本地网络异常（无连接、超时、DNS 失败等） */
    const val NETWORK_ERROR = -1

    /** 响应解析失败 */
    const val PARSE_ERROR = -2
}
