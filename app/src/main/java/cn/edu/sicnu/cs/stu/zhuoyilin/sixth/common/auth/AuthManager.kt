package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.common.auth

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.login.LoginActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.util.SpUtil

/**
 * 全局登录态管理：token 存取、退出登录、401 统一跳转登录页。
 */
object AuthManager {
    private var appContext: Context? = null

    /** 防止多个并发请求同时 401 时重复跳转 */
    @Volatile
    private var lastRedirectAt = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isLoggedIn(): Boolean {
        return !SpUtil.getToken(requireContext()).isNullOrEmpty()
    }

    fun saveToken(token: String) {
        SpUtil.saveToken(requireContext(), token)
    }

    fun clearToken() {
        SpUtil.clearToken(requireContext())
    }

    fun logout() {
        clearToken()
        redirectToLogin()
    }

    fun handleTokenExpired() {
        clearToken()
        redirectToLogin()
    }

    private fun redirectToLogin() {
        val now = System.currentTimeMillis()
        if (now - lastRedirectAt < 1500L) return
        lastRedirectAt = now

        val context = requireContext()
        Handler(Looper.getMainLooper()).post {
            context.startActivity(Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    private fun requireContext(): Context {
        return checkNotNull(appContext) { "AuthManager 未初始化，请在 App 中调用 AuthManager.init(this)" }
    }
}
