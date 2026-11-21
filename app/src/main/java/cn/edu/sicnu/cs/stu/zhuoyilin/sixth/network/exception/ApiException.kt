package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception

class ApiException(
    val code: Int,
    override val message: String?
) : Exception(message)
