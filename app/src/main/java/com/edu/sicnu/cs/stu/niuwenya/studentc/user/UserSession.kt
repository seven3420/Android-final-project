package com.edu.sicnu.cs.stu.niuwenya.studentc.user

import android.content.Context

/**
 * 本地登录会话（课程设计简化实现）。
 *
 * 后端登录接口归成员 B 负责，这里先做纯本地登录：账号 + 6 位以上密码即视为登录成功，
 * 把账号写进 SharedPreferences 作为会话标记，App 启动时据此决定进登录页还是主页。
 * 等 B 的登录接口就绪后，只需在 LoginActivity 的登录成功回调里换成真实请求，
 * 其余页面（启动拦截、个人中心、退出登录）都不用改。
 */
object UserSession {

    private const val PREFS_NAME = "user_session"
    private const val KEY_ACCOUNT = "account"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLoggedIn(context: Context): Boolean = account(context).isNotBlank()

    fun login(context: Context, account: String) {
        prefs(context).edit().putString(KEY_ACCOUNT, account).apply()
    }

    fun logout(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun account(context: Context): String =
        prefs(context).getString(KEY_ACCOUNT, "").orEmpty()

    /** 手机号显示为"影迷 8888"，其他账号原样展示。 */
    fun nickname(context: Context): String {
        val value = account(context)
        return if (isPhone(value)) "影迷${value.takeLast(4)}" else value
    }

    /** 手机号中间四位打码，如 138****8888。 */
    fun maskedAccount(context: Context): String {
        val value = account(context)
        return if (isPhone(value)) value.replaceRange(3, 7, "****") else value
    }

    private fun isPhone(value: String): Boolean =
        value.length == 11 && value.all { it.isDigit() }
}
