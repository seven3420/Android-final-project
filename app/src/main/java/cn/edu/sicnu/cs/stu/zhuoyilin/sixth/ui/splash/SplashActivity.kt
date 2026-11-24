package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.splash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.MainActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.R
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.common.auth.AuthManager
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.ui.login.LoginActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 启动页：有 token 且校验有效 -> 首页；否则 -> 登录页。
 */
class SplashActivity : AppCompatActivity() {

    private val viewModel: SplashViewModel by viewModels()

    private val localNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { proceed() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        requestLocalNetworkPermissionIfNeeded()
    }

    /**
     * Android 17（API 37）起，访问局域网 http 后端需要用户授权本地网络权限，
     * 否则请求会一直超时。
     */
    private fun requestLocalNetworkPermissionIfNeeded() {
        // ACCESS_LOCAL_NETWORK 是 Android 17(API 37) 才引入的权限
        // compileSdk 34 无法引用 Manifest.permission.ACCESS_LOCAL_NETWORK
        val perm = "android.permission.ACCESS_LOCAL_NETWORK"
        val needRequest = Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED

        if (needRequest) {
            localNetworkPermissionLauncher.launch(perm)
        } else {
            proceed()
        }
    }

    private fun proceed() {
        if (!AuthManager.isLoggedIn()) {
            goLogin()
            return
        }

        lifecycleScope.launch {
            // 校验最多等 5 秒，避免后端未启动时卡在启动页
            val valid = withTimeoutOrNull(5000) { viewModel.isTokenValid() } ?: true
            if (valid) {
                goMain()
            } else {
                AuthManager.clearToken()
                goLogin()
            }
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
