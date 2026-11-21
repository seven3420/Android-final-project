package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base

import androidx.lifecycle.ViewModel
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
     * 统一执行网络请求：
     * 1. HTTP 401 / code 401 -> ApiException(401)
     * 2. code != 200 -> ApiException(code, msg)
     * 3. 网络/解析异常统一转成 ApiException，UI 层只需要 catch ApiException
     */
    protected suspend fun <T> executeRequest(
        block: suspend () -> Response<ApiResponse<T>>
    ): T = withContext(Dispatchers.IO) {
        try {
            val response = block()
            val body = response.body()

            if (response.code() == ErrorCode.TOKEN_INVALID) {
                throw ApiException(ErrorCode.TOKEN_INVALID, body?.msg ?: "登录已失效，请重新登录")
            }
            if (!response.isSuccessful) {
                throw ApiException(response.code(), body?.msg ?: "请求失败(${response.code()})")
            }
            if (body == null) {
                throw ApiException(ErrorCode.PARSE_ERROR, "响应体为空")
            }
            if (body.code != ErrorCode.SUCCESS) {
                throw ApiException(body.code, body.msg.ifEmpty { "请求失败" })
            }

            body.data ?: throw ApiException(body.code, "data 为空")
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
}
