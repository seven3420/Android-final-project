package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.splash

import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.base.BaseViewModel
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.RetrofitClient
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.exception.ErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SplashViewModel : BaseViewModel() {

    /**
     * 用 getUserInfo 校验本地 token 是否仍然有效。
     * 401 -> 无效；网络异常 -> 保留登录态直接进首页，避免断网时被踢出。
     */
    suspend fun isTokenValid(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = RetrofitClient.apiService.getUserInfo()
            val body = response.body()
            when {
                response.code() == ErrorCode.HTTP_UNAUTHORIZED -> false
                body != null && body.code == ErrorCode.TOKEN_INVALID -> false
                else -> true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            true
        }
    }
}
