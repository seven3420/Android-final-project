package cn.edu.sicnu.cs.stu.zhuoyilin.sixth

import android.app.Application
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.common.auth.AuthManager
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.RetrofitClient

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AuthManager.init(this)
        RetrofitClient.init(this)
    }
}
