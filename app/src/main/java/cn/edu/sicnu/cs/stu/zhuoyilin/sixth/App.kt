package cn.edu.sicnu.cs.stu.zhuoyilin.sixth

import android.app.Application
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.cache.CacheModule
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.common.auth.AuthManager
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.RetrofitClient
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.util.SpUtil

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AuthManager.init(this)
        RetrofitClient.init(this)
        CacheModule.init(this)

        // C 的订单模块复用同一个后端地址和 B 的登录 token
        com.edu.sicnu.cs.stu.niuwenya.studentc.order.api.RetrofitClient.init(
            BuildConfig.API_ORIGIN,
            { SpUtil.getToken(this) }
        )
    }
}
