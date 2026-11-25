package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base

import androidx.lifecycle.ViewModel
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.common.auth.AuthManager
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception.ApiException
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception.ErrorCode
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.response.ApiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

open class BaseViewModel : ViewModel() {

    /**
     * 登录页等场景可置为 false，避免登录失败（1001）时又触发跳转登录页。
     */
    open val autoHandleTokenExpired: Boolean = true

    /**
     * 统一执行网络请求：
     * 1. HTTP 401 / code 1001 -> 清 token 并跳登录页，抛 ApiException(1001)
     * 2. code != 0 -> ApiException(code, msg)
     * 3. 网络/解析异常统一转成 ApiException，UI 层只需要 catch ApiException
     */
    protected suspend fun <T> executeRequest(
        block: suspend () -> Response<ApiResponse<T>>
    ): T = withContext(Dispatchers.IO) {
        try {
            val response = block()
            val body = response.body()

            if (response.code() == ErrorCode.HTTP_UNAUTHORIZED) {
                onTokenInvalid(body?.msg)
            }
            if (!response.isSuccessful) {
                throw ApiException(response.code(), body?.msg ?: "请求失败(${response.code()})")
            }
            if (body == null) {
                throw ApiException(ErrorCode.PARSE_ERROR, "响应体为空")
            }
            if (body.code == ErrorCode.TOKEN_INVALID) {
                onTokenInvalid(body.msg)
            }
            if (body.code != ErrorCode.SUCCESS) {
                throw ApiException(body.code, body.msg.ifEmpty { "请求失败" })
            }

            body.data ?: throw ApiException(ErrorCode.PARSE_ERROR, "data 为空")
        } catch (e: ApiException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ApiException(ErrorCode.NETWORK_ERROR, e.message ?: "网络异常")
        } catch (e: Exception) {
            throw ApiException(ErrorCode.PARSE_ERROR, e.message ?: "数据解析异常")
        }
    }

    private fun onTokenInvalid(msg: String?): Nothing {
        if (autoHandleTokenExpired) {
            AuthManager.handleTokenExpired()
        }
        throw ApiException(ErrorCode.TOKEN_INVALID, msg ?: "登录已失效，请重新登录")
    }
}
