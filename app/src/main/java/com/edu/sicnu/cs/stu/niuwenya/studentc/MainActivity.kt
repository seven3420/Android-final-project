package com.edu.sicnu.cs.stu.niuwenya.studentc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.OrderConfirmActivity
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.OrderListActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 登录页与首页由成员 B 负责，接入前先用这两个临时入口进交易链路联调
        findViewById<Button>(R.id.btnConfirmOrder).setOnClickListener {
            startActivity(Intent(this, OrderConfirmActivity::class.java))
        }
        findViewById<Button>(R.id.btnMyOrders).setOnClickListener {
            startActivity(Intent(this, OrderListActivity::class.java))
        }
    }
}
