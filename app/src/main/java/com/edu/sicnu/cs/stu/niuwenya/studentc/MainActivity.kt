package com.edu.sicnu.cs.stu.niuwenya.studentc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.OrderConfirmActivity
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.OrderListActivity
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.StatisticsActivity

class MainActivity : AppCompatActivity() {

    /**
     * Android 17（API 37）起访问 10.0.2.2 / 局域网 IP 需要该运行时权限，
     * 不申请会让所有接口连接超时并提示"网络异常"。
     */
    private val localNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "未授权本地网络访问，交易与统计接口将无法连接",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        ensureLocalNetworkPermission()

        // 登录页与首页由成员 B 负责，接入前先用这两个临时入口进交易链路联调
        findViewById<Button>(R.id.btnConfirmOrder).setOnClickListener {
            startActivity(Intent(this, OrderConfirmActivity::class.java))
        }
        findViewById<Button>(R.id.btnMyOrders).setOnClickListener {
            startActivity(Intent(this, OrderListActivity::class.java))
        }
        findViewById<Button>(R.id.btnStatistics).setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }
    }

    private fun ensureLocalNetworkPermission() {
        if (Build.VERSION.SDK_INT < 37) return // 低版本靠 INTERNET 隐式放行，不能声明式申请
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }
}
