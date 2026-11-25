package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network

import android.content.Context
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.util.SpUtil
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 有 token 时自动加到请求头 Authorization；与 A 组约定 header 名为 Authorization。
 */
class TokenInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = SpUtil.getToken(context)

        val newRequest = if (!token.isNullOrEmpty()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }
        return chain.proceed(newRequest)
    }
}
