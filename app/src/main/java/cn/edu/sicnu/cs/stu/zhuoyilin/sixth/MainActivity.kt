package cn.edu.sicnu.cs.stu.zhuoyilin.sixth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.common.auth.AuthManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    private val gameFragment = GameFragment.newInstance()
    private val helloFragment = HelloFragment.newInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val bottomNV = findViewById<BottomNavigationView>(R.id.bottomNV)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.frameLayout, gameFragment)
                .commit()
        }

        bottomNV.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.game -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.frameLayout, gameFragment).commit()
                    true
                }
                R.id.hello -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.frameLayout, helloFragment).commit()
                    true
                }
                R.id.logout -> {
                    showLogoutDialog()
                    false
                }
                else -> false
            }
        }
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("退出登录")
            .setMessage("确定要退出当前账号吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("退出") { _, _ -> AuthManager.logout() }
            .show()
    }
}
