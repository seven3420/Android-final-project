package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.util

import android.content.Context
import android.content.SharedPreferences

object SpUtil {
    private const val SP_NAME = "app_sp"
    private const val KEY_TOKEN = "token"

    private fun getSp(context: Context): SharedPreferences {
        return context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(context: Context, token: String) {
        getSp(context).edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context): String? {
        return getSp(context).getString(KEY_TOKEN, null)
    }

    fun clearToken(context: Context) {
        getSp(context).edit().remove(KEY_TOKEN).apply()
    }
}
